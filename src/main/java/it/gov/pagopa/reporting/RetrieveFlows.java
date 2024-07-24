package it.gov.pagopa.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.sun.xml.ws.client.ClientTransportException;
import it.gov.pagopa.reporting.client.ApiConfigClient;
import it.gov.pagopa.reporting.exception.Cache4XXException;
import it.gov.pagopa.reporting.exception.Cache5XXException;
import it.gov.pagopa.reporting.models.OrganizationsMessage;
import it.gov.pagopa.reporting.models.cache.CacheResponse;
import it.gov.pagopa.reporting.models.cache.CreditorInstitutionStation;
import it.gov.pagopa.reporting.models.cache.Station;
import it.gov.pagopa.reporting.service.FlowsService;
import it.gov.pagopa.reporting.service.NodoChiediElencoFlussi;
import it.gov.pagopa.reporting.service.OrganizationsService;
import it.gov.pagopa.reporting.servicewsdl.FaultBean;
import it.gov.pagopa.reporting.servicewsdl.TipoElencoFlussiRendicontazione;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class RetrieveFlows {

    private final String storageConnectionString = System.getenv("FLOW_SA_CONNECTION_STRING");
    private final String flowsTable = System.getenv("FLOWS_TABLE");
    private final String flowsQueue = System.getenv("FLOWS_QUEUE");
    private final String organizationsTable = System.getenv("ORGANIZATIONS_TABLE");
    private final String organizationsQueue = System.getenv("ORGANIZATIONS_QUEUE");
    private final String timeToLiveInSeconds = System.getenv("QUEUE_RETENTION_SEC");
    private final String initialVisibilityDelayInSeconds = System.getenv("QUEUE_DELAY_SEC");
    private final String maxRetryQueuing = System.getenv("MAX_RETRY_QUEUING");

    private static CacheResponse cacheContent;

    /**
     * This function will be invoked when a new message is detected in the queue
     */
    @FunctionName("RetrieveFlows")
    public void run(
            @QueueTrigger(name = "RetrieveOrganizationsTrigger", queueName = "%ORGANIZATIONS_QUEUE%", connection = "FLOW_SA_CONNECTION_STRING") String message,
            final ExecutionContext context) {

        Logger logger = context.getLogger();
        logger.log(Level.INFO, () -> String.format("[RetrieveOrganizationsTrigger START] processed the message: %s at %s", message, LocalDate.now()));

        NodoChiediElencoFlussi nodeClient = this.getNodeClientInstance(logger);
        FlowsService flowsService = this.getFlowsServiceInstance(logger);
        ApiConfigClient cacheClient = this.getCacheClientInstance();
        if(cacheContent == null || (cacheContent.getRetrieveDate() != null && cacheContent.getRetrieveDate().isBefore(LocalDate.now()))) {
            synchronized (RetrieveFlows.class) {
                setCache(cacheClient, logger);
            }
        }

        try {
            OrganizationsMessage organizationsMessage = new ObjectMapper().readValue(message, OrganizationsMessage.class);

            Arrays.stream(organizationsMessage.getIdPA())
                    .forEach((organization -> {
                        try {
                            Station stationBroker = getPAStationIntermediario(organization)
                                    .orElseThrow(() -> new RuntimeException(String.format("No data present in api config database for PA %s", organization)));
                            String idStation = stationBroker.getStationCode();
                            String idBroker = stationBroker.getBrokerCode();
                            logger.log(Level.INFO, () -> "[RetrieveFlows][NodoChiediElencoFlussiRendicontazione] idPa: " + organization + ", idIntermediario: " + idBroker + ", idStazione: " + idStation );
                            // call NODO dei pagamenti
                            nodeClient.nodoChiediElencoFlussiRendicontazione(organization, idBroker, idStation);

                            // retrieve result
                            FaultBean faultBean = nodeClient.getNodoChiediElencoFlussiRendicontazioneFault();

                            TipoElencoFlussiRendicontazione elencoFlussi = nodeClient.getNodoChiediElencoFlussiRendicontazione();

                            if (faultBean != null) {
                                logger.log(Level.WARNING, () -> "[RetrieveFlows] faultBean DESC " + faultBean.getDescription());
                            } else if (elencoFlussi != null) {
                                logger.log(Level.INFO, () -> "[RetrieveFlows] elencoFlussi PA " + organization + " TotRestituiti " + elencoFlussi.getTotRestituiti());
                                flowsService.flowsProcessing(elencoFlussi.getIdRendicontazione(), organization);
                            }
                        } catch (ClientTransportException e) {
                            logger.log(Level.SEVERE, () -> "[NODO Connection down] Organization: [" + organization +"] Caused by: " + e.getCause() + " Message: " + e.getMessage() + " Stack trace: " + Arrays.toString(e.getStackTrace()));
                            int retry = organizationsMessage.getRetry();
                            if (retry < Integer.parseInt(maxRetryQueuing)) {
                                OrganizationsService organizationsService = getOrganizationsServiceInstance(logger);
                                organizationsService.retryToOrganizationsQueue(organization, retry + 1);
                            } else {
                                logger.log(Level.SEVERE, () -> "[NODO Connection down]  Max retry exceeded.");
                            }
                        }

                    }));
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, () -> "[RetrieveOrganizationsTrigger]  Error " + e.getLocalizedMessage());
        }
    }

    public ApiConfigClient getCacheClientInstance() {
        return ApiConfigClient.getInstance();
    }

    public NodoChiediElencoFlussi getNodeClientInstance(Logger logger) {
        return new NodoChiediElencoFlussi(logger);
    }

    public FlowsService getFlowsServiceInstance(Logger logger) {
        return new FlowsService(this.storageConnectionString, this.flowsTable, this.flowsQueue, logger);
    }

    public OrganizationsService getOrganizationsServiceInstance(Logger logger) {
        return new OrganizationsService(this.storageConnectionString, this.organizationsTable, this.organizationsQueue, Integer.parseInt(timeToLiveInSeconds), Integer.parseInt(initialVisibilityDelayInSeconds), logger);
    }

    public Optional<Station> getPAStationIntermediario(String idPa) {
        List<String> stationPa = getStations(idPa);
        return cacheContent.getStations().stream()
                .filter(station -> stationPa.contains(station.getStationCode()))
                .filter(Station::getEnabled)
                .findFirst();
    }

    public List<String> getStations(String idPa) {
        return cacheContent.getCreditorInstitutionStations().stream()
                .filter(creditorInstitutionStation -> creditorInstitutionStation.getCreditorInstitutionCode().equals(idPa))
                .map(CreditorInstitutionStation::getStationCode).collect(Collectors.toList());
    }

    public synchronized void setCache(ApiConfigClient cacheClient, Logger logger) {
        try {
            if(cacheContent == null) {
                cacheContent = cacheClient.getCache();
                cacheContent.setRetrieveDate(LocalDate.now());
            }
        } catch (Cache4XXException | Cache5XXException e) {
            cacheContent = null;
            logger.log(Level.SEVERE, e.getMessage());
        } catch (IOException e) {
            cacheContent = null;
            logger.log(Level.SEVERE, e.getMessage());
            throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            cacheContent = null;
            logger.log(Level.SEVERE, e.getMessage());
        }
    }
}

package it.gov.pagopa.reporting.service;

import com.sun.xml.ws.client.ClientTransportException;
import it.gov.pagopa.reporting.servicewsdl.FaultBean;
import it.gov.pagopa.reporting.servicewsdl.PagamentiTelematiciRPT;
import it.gov.pagopa.reporting.servicewsdl.PagamentiTelematiciRPTservice;
import it.gov.pagopa.reporting.servicewsdl.TipoElencoFlussiRendicontazione;

import javax.xml.ws.Holder;
import java.net.URL;
import java.util.logging.Logger;

public class NodoChiediElencoFlussi {

    private static final URL WSD_URL = PagamentiTelematiciRPTservice.WSDL_LOCATION;
    private PagamentiTelematiciRPTservice ss;
    private PagamentiTelematiciRPT port;
    private Holder<FaultBean> nodoChiediElencoFlussiRendicontazioneFault;
    private Holder<TipoElencoFlussiRendicontazione> nodoChiediElencoFlussiRendicontazione;

    public NodoChiediElencoFlussi(Logger logger) {

        ss = new PagamentiTelematiciRPTservice(WSD_URL);
        port = ss.getPagamentiTelematiciRPTPort();
    }

    public void setNodoChiediElencoFlussiRendicontazioneFault(
            Holder<FaultBean> nodoChiediElencoFlussiRendicontazioneFault) {
        this.nodoChiediElencoFlussiRendicontazioneFault = nodoChiediElencoFlussiRendicontazioneFault;
    }

    public void setNodoChiediElencoFlussiRendicontazione(
            Holder<TipoElencoFlussiRendicontazione> nodoChiediElencoFlussiRendicontazione) {
        this.nodoChiediElencoFlussiRendicontazione = nodoChiediElencoFlussiRendicontazione;
    }

    public FaultBean getNodoChiediElencoFlussiRendicontazioneFault() {
        return nodoChiediElencoFlussiRendicontazioneFault != null ? nodoChiediElencoFlussiRendicontazioneFault.value
                : null;
    }

    public TipoElencoFlussiRendicontazione getNodoChiediElencoFlussiRendicontazione() {
        return nodoChiediElencoFlussiRendicontazione != null
                ? nodoChiediElencoFlussiRendicontazione.value
                : null;
    }

    public void nodoChiediElencoFlussiRendicontazione(String idPa,
                                                      String idIntermediarioPA,
                                                      String idStazioneIntermediarioPA,
                                                      String passwordStazione) throws ClientTransportException {

        var nodoChiediElencoFlussiRendicontazioneFaultLocal = new Holder<FaultBean>();
        var nodoChiediElencoFlussiRendicontazioneElencoFlussiRendicontazioneLocal = new Holder<TipoElencoFlussiRendicontazione>();

        port.nodoChiediElencoFlussiRendicontazione(
                idIntermediarioPA,
                idStazioneIntermediarioPA,
                passwordStazione, idPa, null,
                nodoChiediElencoFlussiRendicontazioneFaultLocal,
                nodoChiediElencoFlussiRendicontazioneElencoFlussiRendicontazioneLocal);

        setNodoChiediElencoFlussiRendicontazioneFault(nodoChiediElencoFlussiRendicontazioneFaultLocal);
        setNodoChiediElencoFlussiRendicontazione(nodoChiediElencoFlussiRendicontazioneElencoFlussiRendicontazioneLocal);
    }

}

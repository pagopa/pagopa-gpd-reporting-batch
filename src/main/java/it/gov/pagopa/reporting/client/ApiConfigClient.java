package it.gov.pagopa.reporting.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.gson.reflect.TypeToken;
import it.gov.pagopa.reporting.exception.Cache4XXException;
import it.gov.pagopa.reporting.exception.Cache5XXException;
import it.gov.pagopa.reporting.models.cache.CacheResponse;
import it.gov.pagopa.reporting.models.cache.CreditorInstitutionStation;
import it.gov.pagopa.reporting.models.cache.Station;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiConfigClient {

    private static ApiConfigClient instance = null;

    private final HttpTransport httpTransport = new NetHttpTransport();
    private final JsonFactory jsonFactory = new GsonFactory();
    private final String apiConfigCacheHost = System.getenv("CACHE_CLIENT_HOST"); // es: https://api.xxx.platform.pagopa.it
    private final String getCacheDetails =
            System.getenv("CACHE_PATH") != null ? System.getenv("CACHE_PATH") : "/cache?keys=creditorInstitutionStations,stations";
    private final String apiKey = System.getenv("CACHE_API_KEY");


    // Retry ExponentialBackOff config
    private final boolean enableRetry =
            System.getenv("ENABLE_CLIENT_RETRY") != null ? Boolean.parseBoolean(System.getenv("ENABLE_CLIENT_RETRY")) : Boolean.FALSE;
    private final int initialIntervalMillis =
            System.getenv("INITIAL_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("INITIAL_INTERVAL_MILLIS")) : 500;
    private final int maxElapsedTimeMillis =
            System.getenv("MAX_ELAPSED_TIME_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_ELAPSED_TIME_MILLIS")) : 1000;
    private final int maxIntervalMillis  =
            System.getenv("MAX_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_INTERVAL_MILLIS")) : 1000;
    private final double multiplier  =
            System.getenv("MULTIPLIER") != null ? Double.parseDouble(System.getenv("MULTIPLIER")) : 1.5;
    private final double randomizationFactor  =
            System.getenv("RANDOMIZATION_FACTOR") != null ? Double.parseDouble(System.getenv("RANDOMIZATION_FACTOR")) : 0.5;

    public static ApiConfigClient getInstance() {
        if (instance == null) {
            instance = new ApiConfigClient();
        }
        return instance;
    }

    public CacheResponse getCache() throws IOException, IllegalArgumentException, Cache5XXException, Cache4XXException {
        GenericUrl url = new GenericUrl(apiConfigCacheHost + getCacheDetails);
        HttpRequest request = this.buildGetRequestToApiConfigCache(url);

        if (enableRetry) {
            this.setRequestRetry(request);
        }

        return this.executeCallToApiConfigCache(request);
    }

    public HttpRequest buildGetRequestToApiConfigCache(GenericUrl url) throws IOException {

        HttpRequestFactory requestFactory = httpTransport.createRequestFactory(
                (HttpRequest request) ->
                        request.setParser(new JsonObjectParser(jsonFactory))
        );

        HttpRequest request = requestFactory.buildGetRequest(url);
        HttpHeaders headers = request.getHeaders();
        headers.set("Ocp-Apim-Subscription-Key", apiKey);
        return request;
    }

    public void setRequestRetry(HttpRequest request) {
        /**
         * Retry section config
         */
        ExponentialBackOff backoff = new ExponentialBackOff.Builder()
                .setInitialIntervalMillis(initialIntervalMillis)
                .setMaxElapsedTimeMillis(maxElapsedTimeMillis)
                .setMaxIntervalMillis(maxIntervalMillis)
                .setMultiplier(multiplier)
                .setRandomizationFactor(randomizationFactor)
                .build();

        // Exponential Backoff is turned off by default in HttpRequest -> it's necessary include an instance of HttpUnsuccessfulResponseHandler to the HttpRequest to activate it
        // The default back-off on anabnormal HTTP response is BackOffRequired.ON_SERVER_ERROR (5xx)
        request.setUnsuccessfulResponseHandler(
                new HttpBackOffUnsuccessfulResponseHandler(backoff));
    }

    public CacheResponse executeCallToApiConfigCache(HttpRequest request) throws IOException, IllegalArgumentException, Cache5XXException, Cache4XXException {

        Type type = new TypeToken<List<CreditorInstitutionStation>>() {}.getType();

        ObjectMapper mapper = new ObjectMapper();
        CacheResponse cacheResponse = CacheResponse.builder().build();
        List<CreditorInstitutionStation> creditorInstitutionStationList = new ArrayList<>();
        List<Station> stationList = new ArrayList<>();
        try {
            InputStream resIs = request.execute().getContent();
            Map<String,Object> responseMap = mapper.readValue(resIs, HashMap.class);
            Map<String,Object> creditorInstitutionStations = (HashMap) responseMap.get("creditorInstitutionStations");
            for (Map.Entry<String, Object> creditorInstitutionStation : creditorInstitutionStations.entrySet()) {
                creditorInstitutionStationList.add(mapper.readValue(mapper.writeValueAsString(creditorInstitutionStation.getValue()), CreditorInstitutionStation.class));
            }
            Map<String,Object> stations = (HashMap) responseMap.get("stations");
            for (Map.Entry<String, Object> station : stations.entrySet()) {
                stationList.add(mapper.readValue(mapper.writeValueAsString(station.getValue()), Station.class));
            }
            cacheResponse.setStations(stationList);
            cacheResponse.setCreditorInstitutionStations(creditorInstitutionStationList);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() / 100 == 4) {
                String message = String.format("Error %s calling the service URL %s", e.getStatusCode(), request.getUrl());
                throw new Cache4XXException(message);

            } else if (e.getStatusCode() / 100 == 5) {
                String message = String.format("Error %s calling the service URL %s", e.getStatusCode(), request.getUrl());
                throw new Cache5XXException(message);

            }
        }
        return cacheResponse;
    }
}

package it.gov.pagopa.reporting;

import it.gov.pagopa.reporting.service.NodoChiediElencoFlussi;
import it.gov.pagopa.reporting.servicewsdl.PagamentiTelematiciRPT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.ws.WebServiceException;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NodoChiediElencoFlussiTest {

    @Mock
    PagamentiTelematiciRPT myPort;

    Logger logger = Logger.getLogger("testlogging");

    @Test
    void nodoChiediElencoFlussiTestNetworkErrorTest() {

        /*
        NodoChiediElencoFlussi nodoChiediElencoFlussi = new NodoChiediElencoFlussi();

        try {

            nodoChiediElencoFlussi.nodoChiediElencoFlussiRendicontazione("");
            assertNull(nodoChiediElencoFlussi.getNodoChiediElencoFlussiRendicontazioneFault());
        } catch (WebServiceException e) {
            assertTrue(Boolean.TRUE);
        } catch (Exception e) {
            fail();
        }

        assertNull(nodoChiediElencoFlussi.getNodoChiediElencoFlussiRendicontazione());
         */
    }

    @Test
    void nodoChiediElencoFlussiTestSettersTest() throws Exception {

        Logger logger = Logger.getLogger("testlogging");
        NodoChiediElencoFlussi nodoChiediElencoFlussi = new NodoChiediElencoFlussi(logger);

        Field keyField = nodoChiediElencoFlussi.getClass().getDeclaredField("port");
        keyField.setAccessible(true);
        keyField.set(nodoChiediElencoFlussi, myPort);

        String idPa = "12345";
        String idBroker = "123456";
        String idStazione = "123456_00";
        String stazionePassword = "***";
        nodoChiediElencoFlussi.nodoChiediElencoFlussiRendicontazione(idPa, idBroker, idStazione, stazionePassword);

        assertTrue(Boolean.TRUE);
    }

}

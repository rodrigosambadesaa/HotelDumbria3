package org.hoteldumbria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApiContractTest {
    @Test
    public void debugEndpointIsOnlyCleartextForTheAndroidEmulatorHost() {
        String endpoint = WebService.getBaseUrl();
        if (endpoint.startsWith("http://")) {
            assertTrue(endpoint.startsWith("http://10.0.2.2:"));
        }
        assertFalse(endpoint.endsWith("/"));
    }

    @Test
    public void nativeAuthenticationUsesOnlyMobileRoutes() {
        assertTrue(WebService.getLoginUrl().endsWith("/auth/mobile/login"));
        assertTrue(WebService.getRefreshUrl().endsWith("/auth/mobile/refresh"));
        assertTrue(WebService.getLogoutUrl().endsWith("/auth/mobile/logout"));
        assertFalse(WebService.getLoginUrl().contains("/auth/web/"));
    }

    @Test
    public void multipleRoomSelectionUsesAtomicBulkReservation() {
        assertTrue(WebService.getBulkReservationsUrl().endsWith("/reservations/bulk"));
    }
}

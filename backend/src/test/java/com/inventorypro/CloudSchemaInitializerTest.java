package com.inventorypro;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class CloudSchemaInitializerTest {
    private static final String REMOTE = "jdbc:oracle:thin:@(description=(address=(protocol=tcps)(host=example.oraclecloud.com)(port=1522))(connect_data=(service_name=inventory_low.oraclecloud.com)))";
    @Test void requiresRemoteTlsAndDedicatedUser() {
        assertDoesNotThrow(() -> CloudSchemaInitializer.validateTarget(REMOTE, "INVENTORY_APP"));
        assertThrows(IllegalArgumentException.class, () -> CloudSchemaInitializer.validateTarget("jdbc:oracle:thin:@//localhost:1521/XEPDB1", "INVENTORY_APP"));
        assertThrows(IllegalArgumentException.class, () -> CloudSchemaInitializer.validateTarget("jdbc:oracle:thin:@//example.com:1521/XEPDB1", "INVENTORY_APP"));
        assertThrows(IllegalArgumentException.class, () -> CloudSchemaInitializer.validateTarget(REMOTE, "ADMIN"));
    }
    @Test void validatesPrivateAdminCredentials() {
        assertDoesNotThrow(() -> CloudSchemaInitializer.validateAdmin("LabAdmin_1", "A-strong-test-password"));
        assertThrows(IllegalArgumentException.class, () -> CloudSchemaInitializer.validateAdmin("admin user", "A-strong-test-password"));
        assertThrows(IllegalArgumentException.class, () -> CloudSchemaInitializer.validateAdmin("LabAdmin_1", "short"));
    }
}

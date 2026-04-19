import org.junit.jupiter.api.Test;
import se.deversity.common.license.LicenseConfig;
import se.deversity.common.license.LicenseGate;
import se.deversity.common.license.LicenseResult;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MinimalGateTest {

    @Test
    void freeProviderEmailIsAllowed() {
        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId("acct_placeholder")
            .keygenApiKey("key_placeholder")
            .build());
        assertInstanceOf(LicenseResult.Allowed.class, gate.check("demo@gmail.com", null));
    }
}

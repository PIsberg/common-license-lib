import se.deversity.common.license.LicenseConfig;
import se.deversity.common.license.LicenseGate;
import se.deversity.common.license.LicenseResult;

/**
 * The shortest possible end-to-end demo: gate a hypothetical "start-app" call
 * behind an email classification. Doesn't hit Keygen — a free-provider email
 * is let through with no key.
 */
public final class MinimalGate {

    public static void main(String[] args) {
        String email = args.length > 0 ? args[0] : "demo-user@gmail.com";

        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId(System.getenv().getOrDefault("KEYGEN_ACCOUNT_ID", "acct_placeholder"))
            .keygenApiKey(System.getenv().getOrDefault("KEYGEN_API_KEY", "key_placeholder"))
            .build());

        LicenseResult result = gate.check(email, null);

        switch (result) {
            case LicenseResult.Allowed a -> System.out.println("Allowed: " + a.reason());
            case LicenseResult.Denied  d -> System.out.println("Denied: "  + d.reason()
                + (d.message() == null ? "" : " — " + d.message()));
        }
    }
}

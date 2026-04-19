package se.deversity.common.license.email;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Access to the library's bundled set of common private / consumer email providers
 * (gmail, outlook, yahoo, icloud, protonmail, …).
 *
 * <p>The list is loaded once from the classpath resource
 * {@code /se/deversity/common/license/free-providers.txt} (one lowercase domain per line,
 * {@code #} comments and blank lines ignored) and cached immutably.
 */
public final class FreeProviders {

    private static final String RESOURCE = "/se/deversity/common/license/free-providers.txt";
    private static final Set<String> BUNDLED = loadBundled();

    private FreeProviders() {
    }

    /** An unmodifiable snapshot of the bundled free-provider domains, lowercased. */
    public static Set<String> bundled() {
        return BUNDLED;
    }

    private static Set<String> loadBundled() {
        Set<String> out = new HashSet<>();
        try (InputStream in = FreeProviders.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + RESOURCE);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    out.add(s.toLowerCase());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
        return Collections.unmodifiableSet(out);
    }
}

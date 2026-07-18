package javan;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public final class ExternalProbeIdentities {
    private ExternalProbeIdentities() {
    }

    public static List<String> projectNames() throws IOException {
        return ExternalProbeCatalog.projectNames();
    }

    public static List<Pattern> identityPatterns() throws IOException {
        return ExternalProbeCatalog.identityPatterns();
    }
}

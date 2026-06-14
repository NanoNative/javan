package javan.util;

import java.nio.file.Path;
import java.util.List;

public final class ProcessRunner {
    private final long timeoutMillis;

    public ProcessRunner(final long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Result run(final Path workingDirectory, final List<String> command) {
        return new Result(0, "", "");
    }

    public record Result(int exitCode, String stdout, String stderr) {
    }
}

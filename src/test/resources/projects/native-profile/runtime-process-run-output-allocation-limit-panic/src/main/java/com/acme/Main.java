package com.acme;

import java.nio.file.Path;
import java.util.List;

import javan.util.ProcessRunner;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final ProcessRunner.Result result = new ProcessRunner(300_000L).run(
            Path.of("."),
            List.of("sh", "-c", "printf '%1024s' x")
        );
        System.out.println(result.stdout().length());
    }
}

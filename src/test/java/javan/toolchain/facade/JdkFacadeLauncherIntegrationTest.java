package javan.toolchain.facade;

import javan.toolchain.JdkInventory;
import javan.toolchain.JdkResolver;
import javan.toolchain.ToolchainManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
final class JdkFacadeLauncherIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void facadeJavaDelegatesNormalJavaAndOwnsJdkManagementCommands() throws Exception {
        final JdkResolver.Resolution resolution = new ToolchainManager().resolveLocalJdk(java.util.Optional.empty());
        final var backend = resolution.selected().orElseThrow();
        final JdkInventory.Entry entry = new JdkInventory().inspect(resolution).stream()
            .filter(value -> value.candidate().home().equals(backend.home()))
            .findFirst()
            .orElseThrow();
        final JdkFacadeStore.Activation activation = new JdkFacadeStore(tempDir.resolve("facades")).activate(entry);
        final Path javan = javanLauncher(backend.javaExecutable());

        final Path java = activation.current().resolve("bin/java");
        final Result version = run(java, List.of("--version"), javan);
        final Result help = run(java, List.of("--help"), javan);
        final Result use = run(java, List.of("jdk", "use", Integer.toString(Runtime.version().feature())), javan);
        final Result list = run(java, List.of("jdk", "list"), javan);

        assertThat(version.exitCode()).isZero();
        assertThat(version.stdout() + version.stderr()).contains("25", "Javan facade", "Management: java jdk list");
        assertThat(help.exitCode()).isZero();
        assertThat(help.stdout() + help.stderr()).contains("Javan:", "java jdk list", "java jdk use <25|vendor@25>");
        assertThat(use.exitCode()).isZero();
        assertThat(use.stdout()).contains("JDK selected", "facade:");
        assertThat(list.exitCode()).isZero();
        assertThat(list.stdout()).contains("JDKs", "active");
        assertThat(list.stderr()).isEmpty();
    }

    private Path javanLauncher(final Path backendJava) throws IOException {
        final Path launcher = tempDir.resolve("javan");
        final Path classes = Path.of("target/classes").toAbsolutePath().normalize();
        Files.writeString(
            launcher,
            "#!/bin/sh\nexec '" + backendJava + "' -Djavan.home='" + tempDir.resolve("javan-home")
                + "' -cp '" + classes + "' javan.Main \"$@\"\n",
            StandardCharsets.UTF_8
        );
        assertThat(launcher.toFile().setExecutable(true)).isTrue();
        return launcher;
    }

    private Result run(final Path executable, final List<String> args, final Path javan) throws Exception {
        final java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(executable.toString());
        command.addAll(args);
        final ProcessBuilder builder = new ProcessBuilder(command).directory(tempDir.toFile());
        builder.environment().put("JAVAN_BIN", javan.toString());
        final Process process = builder.start();
        final String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), stdout, stderr);
    }

    private record Result(int exitCode, String stdout, String stderr) {
    }
}

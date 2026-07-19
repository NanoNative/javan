package javan;

import javan.cli.Cli;
import javan.cli.Version;
import javan.reporting.RuntimeFootprintReports;
import javan.util.Files2;
import javan.util.Json;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliNetworkIntegrationTest extends CliIntegrationSupport {
    @Test
    void socketExplicitConnectLifecycleBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-explicit-connect-lifecycle");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.InetSocketAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket();
                        System.out.println(socket.isConnected());
                        System.out.println(socket.isBound());
                        System.out.println(socket.getInetAddress() == null);
                        System.out.println(socket.getRemoteSocketAddress() == null);
                        socket.connect(new InetSocketAddress("127.0.0.1", %d));
                        System.out.println(socket.isConnected());
                        System.out.println(socket.isBound());
                        System.out.println(socket.getInetAddress().getHostAddress());
                        System.out.println(socket.getPort());
                        System.out.println(socket.getLocalPort() > 0);
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-explicit-connect-lifecycle").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketConnectStateBuildsAndTalksToLoopbackServer() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-connect-state");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.isConnected());
                        System.out.println(socket.getPort());
                        System.out.println(socket.getInetAddress().getHostAddress());
                        System.out.println(socket.isClosed());
                        socket.close();
                        System.out.println(socket.isClosed());
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-connect-state").toString())).stdout())
                .isEqualTo("true\n" + port + "\n127.0.0.1\nfalse\ntrue\n");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketConnectStateIpv6BuildsAndTalksToLoopbackServer() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("::1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-connect-state-ipv6");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("::1", %d);
                        System.out.println(socket.isConnected());
                        System.out.println(socket.getPort());
                        System.out.println(socket.getInetAddress().getHostAddress());
                        System.out.println(socket.isClosed());
                        socket.close();
                        System.out.println(socket.isClosed());
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-connect-state-ipv6").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketHostConstructorWithLocalBindBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().write(65);
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-host-constructor-local-bind");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.InetAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d, InetAddress.getByName("127.0.0.1"), 0);
                        System.out.println(socket.getLocalAddress().getHostAddress());
                        System.out.println(socket.getLocalPort() > 0);
                        System.out.println(socket.getInetAddress().getHostAddress());
                        System.out.println(socket.getPort());
                        System.out.println(socket.getInputStream().read());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-host-constructor-local-bind").toString())).stdout())
                .isEqualTo("127.0.0.1\ntrue\n127.0.0.1\n" + port + "\n65\n");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketInetAddressConstructorWithLocalBindBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().write(66);
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-inet-address-constructor-local-bind");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.InetAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket(
                            InetAddress.getByName("127.0.0.1"),
                            %d,
                            InetAddress.getByName("127.0.0.1"),
                            0
                        );
                        System.out.println(socket.getLocalAddress().getHostAddress());
                        System.out.println(socket.getLocalPort() > 0);
                        System.out.println(socket.getInetAddress().getHostAddress());
                        System.out.println(socket.getPort());
                        System.out.println(socket.getInputStream().read());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-inet-address-constructor-local-bind").toString())).stdout())
                .isEqualTo("127.0.0.1\ntrue\n127.0.0.1\n" + port + "\n66\n");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketHostConstructorWithNullLocalAddressBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().write(67);
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-host-constructor-null-local-address");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.InetAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d, (InetAddress) null, 0);
                        System.out.println(socket.getLocalAddress().getHostAddress());
                        System.out.println(socket.getLocalPort() > 0);
                        System.out.println(socket.getInetAddress().getHostAddress());
                        System.out.println(socket.getPort());
                        System.out.println(socket.getInputStream().read());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-host-constructor-null-local-address").toString())).stdout())
                .isEqualTo("127.0.0.1\ntrue\n127.0.0.1\n" + port + "\n67\n");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketLocalAddressBuildsAndReportsIpv4Loopback() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-local-address-ipv4");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getLocalAddress().getHostAddress());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-local-address-ipv4").toString())).stdout())
                .isEqualTo("127.0.0.1\n");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketSocketAddressObjectsBuildAndExposePortsAndHosts() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-socket-address-objects");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.InetSocketAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        final InetSocketAddress local = (InetSocketAddress) socket.getLocalSocketAddress();
                        final InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
                        System.out.println(local.getAddress().getHostAddress());
                        System.out.println(local.getPort() > 0);
                        System.out.println(remote.getAddress().getHostAddress());
                        System.out.println(remote.getPort());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-socket-address-objects").toString())).stdout())
                .isEqualTo("127.0.0.1\ntrue\n127.0.0.1\n" + port + "\n");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketTcpNoDelayDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-tcp-no-delay-default");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getTcpNoDelay());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-tcp-no-delay-default").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketTcpNoDelayEnableBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-tcp-no-delay-enable");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        socket.setTcpNoDelay(true);
                        System.out.println(socket.getTcpNoDelay());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-tcp-no-delay-enable").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketKeepAliveDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-keep-alive-default");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getKeepAlive());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-keep-alive-default").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketKeepAliveEnableBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-keep-alive-enable");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        socket.setKeepAlive(true);
                        System.out.println(socket.getKeepAlive());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-keep-alive-enable").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketReuseAddressRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-reuse-address-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getReuseAddress());
                        socket.setReuseAddress(false);
                        System.out.println(socket.getReuseAddress());
                        socket.setReuseAddress(true);
                        System.out.println(socket.getReuseAddress());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-reuse-address-round-trip").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketSoTimeoutRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-so-timeout-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getSoTimeout());
                        socket.setSoTimeout(250);
                        System.out.println(socket.getSoTimeout());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-so-timeout-round-trip").toString())).stdout()).isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketSoLingerRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-so-linger-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getSoLinger());
                        socket.setSoLinger(true, 7);
                        System.out.println(socket.getSoLinger());
                        socket.setSoLinger(false, 99);
                        System.out.println(socket.getSoLinger());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-so-linger-round-trip").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketSoLingerClampBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-so-linger-clamp");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        socket.setSoLinger(true, 65_536);
                        System.out.println(socket.getSoLinger());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-so-linger-clamp").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketOobInlineRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().read();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-oob-inline-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getOOBInline());
                        socket.setOOBInline(true);
                        System.out.println(socket.getOOBInline());
                        socket.setOOBInline(false);
                        System.out.println(socket.getOOBInline());
                        socket.getOutputStream().write(1);
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-oob-inline-round-trip").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketTrafficClassRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().read();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-traffic-class-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getTrafficClass());
                        socket.setTrafficClass(16);
                        System.out.println(socket.getTrafficClass());
                        socket.setTrafficClass(255);
                        System.out.println(socket.getTrafficClass());
                        socket.getOutputStream().write(1);
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-traffic-class-round-trip").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketTrafficClassNegativeBuildsAndFailsClearly() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().read();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-traffic-class-negative");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        socket.setTrafficClass(-1);
                        socket.getOutputStream().write(1);
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/socket-traffic-class-negative").toString()));
            assertThat(nativeRun.exitCode()).isEqualTo(1);
            assertThat(nativeRun.stderr()).contains("socket traffic class out of range");
            accepted.cancel(true);
        }
    }

    @Test
    void socketTrafficClassHighBuildsAndFailsClearly() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().read();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-traffic-class-high");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        socket.setTrafficClass(256);
                        socket.getOutputStream().write(1);
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/socket-traffic-class-high").toString()));
            assertThat(nativeRun.exitCode()).isEqualTo(1);
            assertThat(nativeRun.stderr()).contains("socket traffic class out of range");
            accepted.cancel(true);
        }
    }

    @Test
    void socketReceiveBufferSizeRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-receive-buffer-size-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getReceiveBufferSize());
                        socket.setReceiveBufferSize(8192);
                        System.out.println(socket.getReceiveBufferSize());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-receive-buffer-size-round-trip").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketSendBufferSizeRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-send-buffer-size-round-trip");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.getSendBufferSize());
                        socket.setSendBufferSize(8192);
                        System.out.println(socket.getSendBufferSize());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-send-buffer-size-round-trip").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketBoundAndClosedStateBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().available();
                    Thread.sleep(250L);
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-bound-and-closed-state");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.isBound());
                        System.out.println(socket.isClosed());
                        socket.close();
                        System.out.println(socket.isBound());
                        System.out.println(socket.isClosed());
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-bound-and-closed-state").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketShutdownInputStateBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().available();
                    Thread.sleep(250L);
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-shutdown-input-state");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.isInputShutdown());
                        System.out.println(socket.isOutputShutdown());
                        socket.shutdownInput();
                        System.out.println(socket.isInputShutdown());
                        System.out.println(socket.isOutputShutdown());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-shutdown-input-state").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketShutdownOutputStateBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().available();
                    Thread.sleep(250L);
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-shutdown-output-state");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        System.out.println(socket.isInputShutdown());
                        System.out.println(socket.isOutputShutdown());
                        socket.shutdownOutput();
                        System.out.println(socket.isInputShutdown());
                        System.out.println(socket.isOutputShutdown());
                        socket.close();
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-shutdown-output-state").toString())).stdout())
                .isEqualTo(jvmOutput);
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void serverSocketAcceptBuildsAndAcceptsLoopbackClient() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-accept");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    System.out.println(server.getLocalPort());
                    final Socket accepted = server.accept();
                    System.out.println(accepted.isConnected());
                    System.out.println(accepted.getInetAddress().getHostAddress());
                    accepted.close();
                    System.out.println(accepted.isClosed());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/server-socket-accept").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        connectLoopback(port);
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(stdout.join()).isEqualTo(port + "\ntrue\n127.0.0.1\ntrue\n");
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void serverSocketBacklogConstructorBuildsAndAcceptsLoopbackClient() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-backlog");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    System.out.println(server.getLocalPort());
                    final Socket accepted = server.accept();
                    System.out.println(accepted.isConnected());
                    System.out.println(accepted.getInetAddress().getHostAddress());
                    accepted.close();
                    System.out.println(accepted.isClosed());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/server-socket-backlog").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        connectLoopback(port);
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(stdout.join()).isEqualTo(port + "\ntrue\n127.0.0.1\ntrue\n");
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void serverSocketBindAddressConstructorBuildsAndAcceptsIpv4LoopbackClient() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-bind-address-ipv4");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2, InetAddress.getByName("127.0.0.1"));
                    System.out.println(server.getLocalPort());
                    final Socket accepted = server.accept();
                    System.out.println(accepted.isConnected());
                    System.out.println(accepted.getInetAddress().getHostAddress());
                    accepted.close();
                    System.out.println(accepted.isClosed());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/server-socket-bind-address-ipv4").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        connectLoopback(port);
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(stdout.join()).isEqualTo(port + "\ntrue\n127.0.0.1\ntrue\n");
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void serverSocketBindAddressConstructorBuildsAndAcceptsIpv6LoopbackClient() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final int port = freeTcpPort();
        final Path project = project("server-socket-bind-address-ipv6");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2, InetAddress.getByName("::1"));
                    System.out.println(server.getLocalPort());
                    final Socket accepted = server.accept();
                    System.out.println(accepted.isConnected());
                    System.out.println(accepted.getInetAddress().getHostAddress());
                    accepted.close();
                    System.out.println(accepted.isClosed());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/server-socket-bind-address-ipv6").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        connectLoopbackIpv6(port);
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(stdout.join()).isEqualTo(port + "\ntrue\n0:0:0:0:0:0:0:1\ntrue\n");
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void serverSocketGetInetAddressBuildsForDefaultLoopbackBind() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-get-inet-address-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    System.out.println(server.getInetAddress().getHostAddress());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-get-inet-address-default").toString())).stdout())
            .isEqualTo("127.0.0.1\n");
    }

    @Test
    void serverSocketGetInetAddressBuildsForIpv6BindAddress() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final int port = freeTcpPort();
        final Path project = project("server-socket-get-inet-address-ipv6");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;
            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2, InetAddress.getByName("::1"));
                    System.out.println(server.getInetAddress().getHostAddress());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-get-inet-address-ipv6").toString())).stdout())
            .isEqualTo("0:0:0:0:0:0:0:1\n");
    }

    @Test
    void serverSocketLocalSocketAddressBuildsAndReportsBoundEndpoint() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-local-socket-address");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetSocketAddress;
            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    final InetSocketAddress address = (InetSocketAddress) server.getLocalSocketAddress();
                    System.out.println(address.getAddress().getHostAddress());
                    System.out.println(address.getPort());
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-local-socket-address").toString())).stdout())
            .isEqualTo("127.0.0.1\n" + port + "\n");
    }

    @Test
    void serverSocketSoTimeoutRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-so-timeout-round-trip");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    System.out.println(server.getSoTimeout());
                    server.setSoTimeout(250);
                    System.out.println(server.getSoTimeout());
                    server.close();
                }
            }
            """.formatted(port));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-so-timeout-round-trip").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketReceiveBufferSizeRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-receive-buffer-size-round-trip");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    System.out.println(server.getReceiveBufferSize());
                    server.setReceiveBufferSize(8192);
                    System.out.println(server.getReceiveBufferSize());
                    server.close();
                }
            }
            """.formatted(port));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-receive-buffer-size-round-trip").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketReuseAddressDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-reuse-address-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    System.out.println(server.getReuseAddress());
                    server.close();
                }
            }
            """.formatted(port));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-reuse-address-default").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketReuseAddressRoundTripBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-reuse-address-round-trip");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    server.setReuseAddress(false);
                    System.out.println(server.getReuseAddress());
                    server.setReuseAddress(true);
                    System.out.println(server.getReuseAddress());
                    server.close();
                }
            }
            """.formatted(port));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-reuse-address-round-trip").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketBoundAndClosedStateBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-bound-and-closed-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    System.out.println(server.isBound());
                    System.out.println(server.isClosed());
                    server.close();
                    System.out.println(server.isBound());
                    System.out.println(server.isClosed());
                }
            }
            """.formatted(port));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-bound-and-closed-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketAcceptTimeoutFailsClearlyAtRuntime() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("server-socket-accept-timeout-runtime");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d, 2);
                    server.setSoTimeout(50);
                    server.accept();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/server-socket-accept-timeout-runtime").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("server socket accept timed out");
    }

    @Test
    void socketInputStreamReadByteBuildsAndReadsFromLoopbackServer() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().write(65);
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-input-stream-read-byte");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.io.InputStream;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        final InputStream in = socket.getInputStream();
                        System.out.println(in.read());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-input-stream-read-byte").toString())).stdout())
                .isEqualTo("65\n");
            served.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketReadTimeoutFailsClearlyAtRuntime() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.isConnected();
                    Thread.sleep(250L);
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-read-timeout-runtime");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        socket.setSoTimeout(50);
                        System.out.println(socket.getInputStream().read());
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/socket-read-timeout-runtime").toString()));
            assertThat(nativeRun.exitCode()).isNotZero();
            assertThat(nativeRun.stderr()).contains("socket read timed out");
            accepted.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketOutputStreamWriteBytesBuildsAndWritesToLoopbackServer() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<String> served = CompletableFuture.supplyAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    return new String(socket.getInputStream().readNBytes(3), StandardCharsets.UTF_8);
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-output-stream-write-bytes");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.io.OutputStream;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket("127.0.0.1", %d);
                        final OutputStream out = socket.getOutputStream();
                        out.write(new byte[] {97, 98, 99});
                        out.flush();
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/socket-output-stream-write-bytes").toString()));
            assertThat(nativeRun.exitCode()).isZero();
            assertThat(nativeRun.stdout()).isEmpty();
            assertThat(served.get(5, TimeUnit.SECONDS)).isEqualTo("abc");
        }
    }

    @Test
    void acceptedSocketInputStreamReadByteBuildsAndReadsFromLoopbackClient() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("accepted-socket-input-stream-read-byte");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    final Socket accepted = server.accept();
                    final InputStream in = accepted.getInputStream();
                    System.out.println(in.read());
                    accepted.close();
                    server.close();
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/accepted-socket-input-stream-read-byte").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        writeLoopbackBytes(port, new byte[] {90});
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(stdout.join()).isEqualTo("90\n");
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpHelloRouteBuildsAndServesDeterministicResponse() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-hello-route");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] REQUEST_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'l', 'l', 'o', ' '
                };
                private static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    final Socket accepted = server.accept();
                    final InputStream in = accepted.getInputStream();
                    final OutputStream out = accepted.getOutputStream();
                    final byte[] request = new byte[1024];
                    int length = 0;
                    while (!headerComplete(request, length)) {
                        final int read = in.read(request, length, request.length - length);
                        if (read < 0) {
                            break;
                        }
                        length += read;
                    }
                    if (!startsWith(request, length, REQUEST_PREFIX)) {
                        throw new IllegalStateException("unexpected request");
                    }
                    out.write(RESPONSE_200);
                    out.flush();
                    accepted.close();
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    if (length < 4) {
                        return false;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return true;
                        }
                    }
                    return false;
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-hello-route").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hello"))
            .GET()
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> response = null;
        while (System.nanoTime() < deadline) {
            try {
                response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("pong");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpUnknownRouteBuildsAndServes404Response() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-unknown-route");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] REQUEST_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'l', 'l', 'o', ' '
                };
                private static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };
                private static final byte[] RESPONSE_404 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','4','0','4',' ','N','o','t',' ','F','o','u','n','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'm','i','s','s'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    final Socket accepted = server.accept();
                    final InputStream in = accepted.getInputStream();
                    final OutputStream out = accepted.getOutputStream();
                    final byte[] request = new byte[1024];
                    int length = 0;
                    while (!headerComplete(request, length)) {
                        final int read = in.read(request, length, request.length - length);
                        if (read < 0) {
                            break;
                        }
                        length += read;
                    }
                    if (startsWith(request, length, REQUEST_PREFIX)) {
                        out.write(RESPONSE_200);
                    } else {
                        out.write(RESPONSE_404);
                    }
                    out.flush();
                    accepted.close();
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    if (length < 4) {
                        return false;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return true;
                        }
                    }
                    return false;
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-unknown-route").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/missing"))
            .GET()
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> response = null;
        while (System.nanoTime() < deadline) {
            try {
                response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("miss");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpPostBodyBuildsAndServesCreatedResponse() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-post-body");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] REQUEST_PREFIX = new byte[] {
                    'P', 'O', 'S', 'T', ' ', '/', 'm', 'e', 't', 'r', 'i', 'c', ' '
                };
                private static final byte[] CONTENT_LENGTH_HEADER = new byte[] {
                    'C', 'o', 'n', 't', 'e', 'n', 't', '-', 'L', 'e', 'n', 'g', 't', 'h', ':', ' '
                };
                private static final byte[] EXPECTED_BODY = new byte[] {
                    'h', 'e', 'l', 'l', 'o'
                };
                private static final byte[] RESPONSE_201 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','1',' ','C','r','e','a','t','e','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','5','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    's','a','v','e','d'
                };
                private static final byte[] RESPONSE_400 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','4','0','0',' ','B','a','d',' ','R','e','q','u','e','s','t','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','3','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'b','a','d'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    final Socket accepted = server.accept();
                    final InputStream in = accepted.getInputStream();
                    final OutputStream out = accepted.getOutputStream();
                    final byte[] request = new byte[1024];
                    int length = 0;
                    while (!headerComplete(request, length)) {
                        final int read = in.read(request, length, request.length - length);
                        if (read < 0) {
                            break;
                        }
                        length += read;
                    }
                    final int headerEnd = headerEndIndex(request, length);
                    final int bodyLength = contentLength(request, headerEnd);
                    while (headerEnd >= 0 && bodyLength >= 0 && length < headerEnd + bodyLength) {
                        final int read = in.read(request, length, request.length - length);
                        if (read < 0) {
                            break;
                        }
                        length += read;
                    }
                    if (startsWith(request, length, REQUEST_PREFIX)
                        && bodyLength == EXPECTED_BODY.length
                        && bodyEquals(request, headerEnd, EXPECTED_BODY)) {
                        out.write(RESPONSE_201);
                    } else {
                        out.write(RESPONSE_400);
                    }
                    out.flush();
                    accepted.close();
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    return headerEndIndex(value, length) >= 0;
                }

                private static int headerEndIndex(final byte[] value, final int length) {
                    if (length < 4) {
                        return -1;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return index + 1;
                        }
                    }
                    return -1;
                }

                private static int contentLength(final byte[] value, final int headerEnd) {
                    if (headerEnd < 0) {
                        return -1;
                    }
                    for (int index = 0; index + CONTENT_LENGTH_HEADER.length <= headerEnd; index++) {
                        if (matchesAt(value, index, CONTENT_LENGTH_HEADER)) {
                            int result = 0;
                            int cursor = index + CONTENT_LENGTH_HEADER.length;
                            while (cursor < headerEnd && value[cursor] >= '0' && value[cursor] <= '9') {
                                result = result * 10 + (value[cursor] - '0');
                                cursor++;
                            }
                            return result;
                        }
                    }
                    return -1;
                }

                private static boolean matchesAt(final byte[] value, final int offset, final byte[] expected) {
                    for (int index = 0; index < expected.length; index++) {
                        if (value[offset + index] != expected[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean bodyEquals(final byte[] value, final int offset, final byte[] expected) {
                    if (offset < 0) {
                        return false;
                    }
                    return matchesAt(value, offset, expected);
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-post-body").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/metric"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("hello"))
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> response = null;
        while (System.nanoTime() < deadline) {
            try {
                response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("saved");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpSequentialRequestsBuildsAndServesTwoConnections() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-sequential-requests");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] REQUEST_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'l', 'l', 'o', ' '
                };
                private static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    for (int handled = 0; handled < 2; handled++) {
                        final Socket accepted = server.accept();
                        final InputStream in = accepted.getInputStream();
                        final OutputStream out = accepted.getOutputStream();
                        final byte[] request = new byte[1024];
                        int length = 0;
                        while (!headerComplete(request, length)) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        if (!startsWith(request, length, REQUEST_PREFIX)) {
                            throw new IllegalStateException("unexpected request");
                        }
                        out.write(RESPONSE_200);
                        out.flush();
                        accepted.close();
                    }
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    if (length < 4) {
                        return false;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return true;
                        }
                    }
                    return false;
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-sequential-requests").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hello"))
            .GET()
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> first = null;
        while (System.nanoTime() < deadline) {
            try {
                first = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }
        assertThat(first).isNotNull();
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).isEqualTo("pong");

        final java.net.http.HttpResponse<String> second = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(second.body()).isEqualTo("pong");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpMethodAndPathDispatchBuildsAndServesDifferentRoutes() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-method-and-path-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] GET_HELLO_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'l', 'l', 'o', ' '
                };
                private static final byte[] POST_METRIC_PREFIX = new byte[] {
                    'P', 'O', 'S', 'T', ' ', '/', 'm', 'e', 't', 'r', 'i', 'c', ' '
                };
                private static final byte[] CONTENT_LENGTH_HEADER = new byte[] {
                    'C', 'o', 'n', 't', 'e', 'n', 't', '-', 'L', 'e', 'n', 'g', 't', 'h', ':', ' '
                };
                private static final byte[] EXPECTED_BODY = new byte[] {
                    'h', 'e', 'l', 'l', 'o'
                };
                private static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };
                private static final byte[] RESPONSE_201 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','1',' ','C','r','e','a','t','e','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','5','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    's','a','v','e','d'
                };
                private static final byte[] RESPONSE_404 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','4','0','4',' ','N','o','t',' ','F','o','u','n','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'm','i','s','s'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    for (int handled = 0; handled < 2; handled++) {
                        final Socket accepted = server.accept();
                        final InputStream in = accepted.getInputStream();
                        final OutputStream out = accepted.getOutputStream();
                        final byte[] request = new byte[1024];
                        int length = 0;
                        while (!headerComplete(request, length)) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        final int headerEnd = headerEndIndex(request, length);
                        final int bodyLength = contentLength(request, headerEnd);
                        while (headerEnd >= 0 && bodyLength >= 0 && length < headerEnd + bodyLength) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        if (startsWith(request, length, GET_HELLO_PREFIX)) {
                            out.write(RESPONSE_200);
                        } else if (startsWith(request, length, POST_METRIC_PREFIX)
                            && bodyLength == EXPECTED_BODY.length
                            && bodyEquals(request, headerEnd, EXPECTED_BODY)) {
                            out.write(RESPONSE_201);
                        } else {
                            out.write(RESPONSE_404);
                        }
                        out.flush();
                        accepted.close();
                    }
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    return headerEndIndex(value, length) >= 0;
                }

                private static int headerEndIndex(final byte[] value, final int length) {
                    if (length < 4) {
                        return -1;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return index + 1;
                        }
                    }
                    return -1;
                }

                private static int contentLength(final byte[] value, final int headerEnd) {
                    if (headerEnd < 0) {
                        return -1;
                    }
                    for (int index = 0; index + CONTENT_LENGTH_HEADER.length <= headerEnd; index++) {
                        if (matchesAt(value, index, CONTENT_LENGTH_HEADER)) {
                            int result = 0;
                            int cursor = index + CONTENT_LENGTH_HEADER.length;
                            while (cursor < headerEnd && value[cursor] >= '0' && value[cursor] <= '9') {
                                result = result * 10 + (value[cursor] - '0');
                                cursor++;
                            }
                            return result;
                        }
                    }
                    return -1;
                }

                private static boolean matchesAt(final byte[] value, final int offset, final byte[] expected) {
                    for (int index = 0; index < expected.length; index++) {
                        if (value[offset + index] != expected[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean bodyEquals(final byte[] value, final int offset, final byte[] expected) {
                    if (offset < 0) {
                        return false;
                    }
                    return matchesAt(value, offset, expected);
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-method-and-path-dispatch").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest getRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hello"))
            .GET()
            .build();
        final java.net.http.HttpRequest postRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/metric"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("hello"))
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> first = null;
        while (System.nanoTime() < deadline) {
            try {
                first = client.send(getRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }
        assertThat(first).isNotNull();
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).isEqualTo("pong");

        final java.net.http.HttpResponse<String> second = client.send(postRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.body()).isEqualTo("saved");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpRouteHandlersBuildAndServeAcrossMultipleClasses() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-route-handlers");
        writeJava(project, "com.acme.RouteHandler", """
            package com.acme;

            public interface RouteHandler {
                boolean matches(byte[] request, int length, int headerEnd, int bodyLength);
                byte[] response();
            }
            """);
        writeJava(project, "com.acme.HttpSupport", """
            package com.acme;

            public final class HttpSupport {
                static final byte[] GET_HELLO_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'l', 'l', 'o', ' '
                };
                static final byte[] POST_METRIC_PREFIX = new byte[] {
                    'P', 'O', 'S', 'T', ' ', '/', 'm', 'e', 't', 'r', 'i', 'c', ' '
                };
                static final byte[] CONTENT_LENGTH_HEADER = new byte[] {
                    'C', 'o', 'n', 't', 'e', 'n', 't', '-', 'L', 'e', 'n', 'g', 't', 'h', ':', ' '
                };
                static final byte[] EXPECTED_BODY = new byte[] {
                    'h', 'e', 'l', 'l', 'o'
                };
                static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };
                static final byte[] RESPONSE_201 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','1',' ','C','r','e','a','t','e','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','5','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    's','a','v','e','d'
                };
                static final byte[] RESPONSE_404 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','4','0','4',' ','N','o','t',' ','F','o','u','n','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'm','i','s','s'
                };

                private HttpSupport() {
                }

                public static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                public static boolean headerComplete(final byte[] value, final int length) {
                    return headerEndIndex(value, length) >= 0;
                }

                public static int headerEndIndex(final byte[] value, final int length) {
                    if (length < 4) {
                        return -1;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return index + 1;
                        }
                    }
                    return -1;
                }

                public static int contentLength(final byte[] value, final int headerEnd) {
                    if (headerEnd < 0) {
                        return -1;
                    }
                    for (int index = 0; index + CONTENT_LENGTH_HEADER.length <= headerEnd; index++) {
                        if (matchesAt(value, index, CONTENT_LENGTH_HEADER)) {
                            int result = 0;
                            int cursor = index + CONTENT_LENGTH_HEADER.length;
                            while (cursor < headerEnd && value[cursor] >= '0' && value[cursor] <= '9') {
                                result = result * 10 + (value[cursor] - '0');
                                cursor++;
                            }
                            return result;
                        }
                    }
                    return -1;
                }

                public static boolean bodyEquals(final byte[] value, final int offset, final byte[] expected) {
                    if (offset < 0) {
                        return false;
                    }
                    return matchesAt(value, offset, expected);
                }

                private static boolean matchesAt(final byte[] value, final int offset, final byte[] expected) {
                    for (int index = 0; index < expected.length; index++) {
                        if (value[offset + index] != expected[index]) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            """);
        writeJava(project, "com.acme.HelloHandler", """
            package com.acme;

            public final class HelloHandler implements RouteHandler {
                @Override
                public boolean matches(final byte[] request, final int length, final int headerEnd, final int bodyLength) {
                    return HttpSupport.startsWith(request, length, HttpSupport.GET_HELLO_PREFIX);
                }

                @Override
                public byte[] response() {
                    return HttpSupport.RESPONSE_200;
                }
            }
            """);
        writeJava(project, "com.acme.MetricHandler", """
            package com.acme;

            public final class MetricHandler implements RouteHandler {
                @Override
                public boolean matches(final byte[] request, final int length, final int headerEnd, final int bodyLength) {
                    return HttpSupport.startsWith(request, length, HttpSupport.POST_METRIC_PREFIX)
                        && bodyLength == HttpSupport.EXPECTED_BODY.length
                        && HttpSupport.bodyEquals(request, headerEnd, HttpSupport.EXPECTED_BODY);
                }

                @Override
                public byte[] response() {
                    return HttpSupport.RESPONSE_201;
                }
            }
            """);
        writeJava(project, "com.acme.NotFoundHandler", """
            package com.acme;

            public final class NotFoundHandler implements RouteHandler {
                @Override
                public boolean matches(final byte[] request, final int length, final int headerEnd, final int bodyLength) {
                    return true;
                }

                @Override
                public byte[] response() {
                    return HttpSupport.RESPONSE_404;
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final RouteHandler[] handlers = new RouteHandler[] {
                        new HelloHandler(),
                        new MetricHandler(),
                        new NotFoundHandler()
                    };
                    final ServerSocket server = new ServerSocket(%d);
                    for (int handled = 0; handled < 2; handled++) {
                        final Socket accepted = server.accept();
                        final InputStream in = accepted.getInputStream();
                        final OutputStream out = accepted.getOutputStream();
                        final byte[] request = new byte[1024];
                        int length = 0;
                        while (!HttpSupport.headerComplete(request, length)) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        final int headerEnd = HttpSupport.headerEndIndex(request, length);
                        final int bodyLength = HttpSupport.contentLength(request, headerEnd);
                        while (headerEnd >= 0 && bodyLength >= 0 && length < headerEnd + bodyLength) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        final byte[] response = route(handlers, request, length, headerEnd, bodyLength);
                        out.write(response);
                        out.flush();
                        accepted.close();
                    }
                    server.close();
                }

                private static byte[] route(
                    final RouteHandler[] handlers,
                    final byte[] request,
                    final int length,
                    final int headerEnd,
                    final int bodyLength
                ) {
                    for (final RouteHandler handler : handlers) {
                        if (handler.matches(request, length, headerEnd, bodyLength)) {
                            return handler.response();
                        }
                    }
                    throw new IllegalStateException("no handler");
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-route-handlers").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest getRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hello"))
            .GET()
            .build();
        final java.net.http.HttpRequest postRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/metric"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("hello"))
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> first = null;
        while (System.nanoTime() < deadline) {
            try {
                first = client.send(getRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }
        assertThat(first).isNotNull();
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).isEqualTo("pong");

        final java.net.http.HttpResponse<String> second = client.send(postRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.body()).isEqualTo("saved");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpRequestHeaderDispatchBuildsAndMatchesHeaderValue() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-request-header-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] REQUEST_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'a', 'd', 'e', 'r', ' '
                };
                private static final byte[] MODE_HEADER = new byte[] {
                    'X', '-', 'M', 'o', 'd', 'e', ':', ' '
                };
                private static final byte[] STRICT_VALUE = new byte[] {
                    's', 't', 'r', 'i', 'c', 't'
                };
                private static final byte[] RESPONSE_202 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','2',' ','A','c','c','e','p','t','e','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','6','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    's','t','r','i','c','t'
                };
                private static final byte[] RESPONSE_400 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','4','0','0',' ','B','a','d',' ','R','e','q','u','e','s','t','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','3','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'b','a','d'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    final Socket accepted = server.accept();
                    final InputStream in = accepted.getInputStream();
                    final OutputStream out = accepted.getOutputStream();
                    final byte[] request = new byte[1024];
                    int length = 0;
                    while (!headerComplete(request, length)) {
                        final int read = in.read(request, length, request.length - length);
                        if (read < 0) {
                            break;
                        }
                        length += read;
                    }
                    if (startsWith(request, length, REQUEST_PREFIX) && hasHeaderValue(request, length, MODE_HEADER, STRICT_VALUE)) {
                        out.write(RESPONSE_202);
                    } else {
                        out.write(RESPONSE_400);
                    }
                    out.flush();
                    accepted.close();
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    if (length < 4) {
                        return false;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return true;
                        }
                    }
                    return false;
                }

                private static boolean hasHeaderValue(
                    final byte[] value,
                    final int length,
                    final byte[] header,
                    final byte[] expected
                ) {
                    for (int index = 0; index + header.length + expected.length <= length; index++) {
                        if (matchesAt(value, index, header) && matchesAt(value, index + header.length, expected)) {
                            return true;
                        }
                    }
                    return false;
                }

                private static boolean matchesAt(final byte[] value, final int offset, final byte[] expected) {
                    for (int index = 0; index < expected.length; index++) {
                        if (value[offset + index] != expected[index]) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-request-header-dispatch").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/header"))
            .header("X-Mode", "strict")
            .GET()
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> response = null;
        while (System.nanoTime() < deadline) {
            try {
                response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }
        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("strict");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpResponseHeaderBuildsAndClientObservesHeaderValue() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-response-header");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private static final byte[] REQUEST_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'r', 'e', 's', 'p', 'o', 'n', 's', 'e', '-', 'h', 'e', 'a', 'd', 'e', 'r', ' '
                };
                private static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'X','-','M','o','d','e',':',' ','s','t','r','i','c','t','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(%d);
                    final Socket accepted = server.accept();
                    final InputStream in = accepted.getInputStream();
                    final OutputStream out = accepted.getOutputStream();
                    final byte[] request = new byte[1024];
                    int length = 0;
                    while (!headerComplete(request, length)) {
                        final int read = in.read(request, length, request.length - length);
                        if (read < 0) {
                            break;
                        }
                        length += read;
                    }
                    if (!startsWith(request, length, REQUEST_PREFIX)) {
                        throw new IllegalStateException("unexpected request");
                    }
                    out.write(RESPONSE_200);
                    out.flush();
                    accepted.close();
                    server.close();
                }

                private static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                private static boolean headerComplete(final byte[] value, final int length) {
                    if (length < 4) {
                        return false;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return true;
                        }
                    }
                    return false;
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-response-header").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/response-header"))
            .GET()
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> response = null;
        while (System.nanoTime() < deadline) {
            try {
                response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }
        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("pong");
        assertThat(response.headers().firstValue("X-Mode")).contains("strict");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void loopbackHttpRequestResponseObjectsBuildAndServeAcrossServiceClasses() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("loopback-http-request-response-objects");
        writeJava(project, "com.acme.RequestData", """
            package com.acme;

            public record RequestData(byte[] raw, int length, int headerEnd, int bodyLength) {
            }
            """);
        writeJava(project, "com.acme.ResponseData", """
            package com.acme;

            public record ResponseData(byte[] bytes) {
            }
            """);
        writeJava(project, "com.acme.Route", """
            package com.acme;

            public interface Route {
                boolean matches(RequestData request);
                ResponseData handle(RequestData request);
            }
            """);
        writeJava(project, "com.acme.HttpSupport", """
            package com.acme;

            public final class HttpSupport {
                static final byte[] GET_HELLO_PREFIX = new byte[] {
                    'G', 'E', 'T', ' ', '/', 'h', 'e', 'l', 'l', 'o', ' '
                };
                static final byte[] POST_METRIC_PREFIX = new byte[] {
                    'P', 'O', 'S', 'T', ' ', '/', 'm', 'e', 't', 'r', 'i', 'c', ' '
                };
                static final byte[] CONTENT_LENGTH_HEADER = new byte[] {
                    'C', 'o', 'n', 't', 'e', 'n', 't', '-', 'L', 'e', 'n', 'g', 't', 'h', ':', ' '
                };
                static final byte[] EXPECTED_BODY = new byte[] {
                    'h', 'e', 'l', 'l', 'o'
                };
                static final byte[] RESPONSE_200 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','0',' ','O','K','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'p','o','n','g'
                };
                static final byte[] RESPONSE_201 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','2','0','1',' ','C','r','e','a','t','e','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','5','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    's','a','v','e','d'
                };
                static final byte[] RESPONSE_404 = new byte[] {
                    'H','T','T','P','/','1','.','1',' ','4','0','4',' ','N','o','t',' ','F','o','u','n','d','\\r','\\n',
                    'C','o','n','t','e','n','t','-','L','e','n','g','t','h',':',' ','4','\\r','\\n',
                    'C','o','n','n','e','c','t','i','o','n',':',' ','c','l','o','s','e','\\r','\\n',
                    '\\r','\\n',
                    'm','i','s','s'
                };

                private HttpSupport() {
                }

                public static boolean headerComplete(final byte[] value, final int length) {
                    return headerEndIndex(value, length) >= 0;
                }

                public static int headerEndIndex(final byte[] value, final int length) {
                    if (length < 4) {
                        return -1;
                    }
                    for (int index = 3; index < length; index++) {
                        if (value[index - 3] == '\\r'
                            && value[index - 2] == '\\n'
                            && value[index - 1] == '\\r'
                            && value[index] == '\\n') {
                            return index + 1;
                        }
                    }
                    return -1;
                }

                public static int contentLength(final byte[] value, final int headerEnd) {
                    if (headerEnd < 0) {
                        return -1;
                    }
                    for (int index = 0; index + CONTENT_LENGTH_HEADER.length <= headerEnd; index++) {
                        if (matchesAt(value, index, CONTENT_LENGTH_HEADER)) {
                            int result = 0;
                            int cursor = index + CONTENT_LENGTH_HEADER.length;
                            while (cursor < headerEnd && value[cursor] >= '0' && value[cursor] <= '9') {
                                result = result * 10 + (value[cursor] - '0');
                                cursor++;
                            }
                            return result;
                        }
                    }
                    return -1;
                }

                public static boolean startsWith(final byte[] value, final int length, final byte[] prefix) {
                    if (length < prefix.length) {
                        return false;
                    }
                    for (int index = 0; index < prefix.length; index++) {
                        if (value[index] != prefix[index]) {
                            return false;
                        }
                    }
                    return true;
                }

                public static boolean bodyEquals(final byte[] value, final int offset, final byte[] expected) {
                    if (offset < 0) {
                        return false;
                    }
                    return matchesAt(value, offset, expected);
                }

                private static boolean matchesAt(final byte[] value, final int offset, final byte[] expected) {
                    for (int index = 0; index < expected.length; index++) {
                        if (value[offset + index] != expected[index]) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            """);
        writeJava(project, "com.acme.HelloRoute", """
            package com.acme;

            public final class HelloRoute implements Route {
                @Override
                public boolean matches(final RequestData request) {
                    return HttpSupport.startsWith(request.raw(), request.length(), HttpSupport.GET_HELLO_PREFIX);
                }

                @Override
                public ResponseData handle(final RequestData request) {
                    return new ResponseData(HttpSupport.RESPONSE_200);
                }
            }
            """);
        writeJava(project, "com.acme.MetricRoute", """
            package com.acme;

            public final class MetricRoute implements Route {
                @Override
                public boolean matches(final RequestData request) {
                    return HttpSupport.startsWith(request.raw(), request.length(), HttpSupport.POST_METRIC_PREFIX)
                        && request.bodyLength() == HttpSupport.EXPECTED_BODY.length
                        && HttpSupport.bodyEquals(request.raw(), request.headerEnd(), HttpSupport.EXPECTED_BODY);
                }

                @Override
                public ResponseData handle(final RequestData request) {
                    return new ResponseData(HttpSupport.RESPONSE_201);
                }
            }
            """);
        writeJava(project, "com.acme.NotFoundRoute", """
            package com.acme;

            public final class NotFoundRoute implements Route {
                @Override
                public boolean matches(final RequestData request) {
                    return true;
                }

                @Override
                public ResponseData handle(final RequestData request) {
                    return new ResponseData(HttpSupport.RESPONSE_404);
                }
            }
            """);
        writeJava(project, "com.acme.Router", """
            package com.acme;

            public final class Router {
                private final Route[] routes;

                public Router(final Route[] routes) {
                    this.routes = routes;
                }

                public ResponseData route(final RequestData request) {
                    for (final Route route : routes) {
                        if (route.matches(request)) {
                            return route.handle(request);
                        }
                    }
                    throw new IllegalStateException("no route");
                }
            }
            """);
        writeJava(project, "com.acme.HttpService", """
            package com.acme;

            import java.io.InputStream;
            import java.io.OutputStream;
            import java.net.ServerSocket;
            import java.net.Socket;

            public final class HttpService {
                private final Router router;

                public HttpService(final Router router) {
                    this.router = router;
                }

                public void serve(final int port, final int connections) throws Exception {
                    final ServerSocket server = new ServerSocket(port);
                    for (int handled = 0; handled < connections; handled++) {
                        final Socket accepted = server.accept();
                        final InputStream in = accepted.getInputStream();
                        final OutputStream out = accepted.getOutputStream();
                        final byte[] request = new byte[1024];
                        int length = 0;
                        while (!HttpSupport.headerComplete(request, length)) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        final int headerEnd = HttpSupport.headerEndIndex(request, length);
                        final int bodyLength = HttpSupport.contentLength(request, headerEnd);
                        while (headerEnd >= 0 && bodyLength >= 0 && length < headerEnd + bodyLength) {
                            final int read = in.read(request, length, request.length - length);
                            if (read < 0) {
                                break;
                            }
                            length += read;
                        }
                        final RequestData requestData = new RequestData(request, length, headerEnd, bodyLength);
                        final ResponseData response = router.route(requestData);
                        out.write(response.bytes());
                        out.flush();
                        accepted.close();
                    }
                    server.close();
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Router router = new Router(new Route[] {
                        new HelloRoute(),
                        new MetricRoute(),
                        new NotFoundRoute()
                    });
                    new HttpService(router).serve(%d, 3);
                }
            }
            """.formatted(port));

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Process process = new ProcessBuilder(project.resolve(".javan/bin/loopback-http-request-response-objects").toString())
            .directory(project.toFile())
            .start();
        final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        final java.net.http.HttpRequest getRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hello"))
            .GET()
            .build();
        final java.net.http.HttpRequest postRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/metric"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("hello"))
            .build();
        final java.net.http.HttpRequest missingRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/missing"))
            .GET()
            .build();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        java.net.http.HttpResponse<String> first = null;
        while (System.nanoTime() < deadline) {
            try {
                first = client.send(getRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                break;
            } catch (final java.net.ConnectException exception) {
                Thread.sleep(25L);
            }
        }
        assertThat(first).isNotNull();
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).isEqualTo("pong");

        final java.net.http.HttpResponse<String> second = client.send(postRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.body()).isEqualTo("saved");

        final java.net.http.HttpResponse<String> third = client.send(missingRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(third.statusCode()).isEqualTo(404);
        assertThat(third.body()).isEqualTo("miss");
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(readStream(process.getInputStream())).isEmpty();
        assertThat(stderr.join()).isEmpty();
    }

    @Test
    void buildRejectsNonSocketInputStreamRead() throws Exception {
        final Path project = project("non-socket-input-stream-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InputStream in = null;
                    System.out.println(in.read());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("error[JAVAN062]", "supported stream call requires a specialized native stream receiver");
    }

    @Test
    void httpClientGetStringBuildsAndMatchesJvmOutput() throws Exception {
        final com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0),
            0
        );
        server.createContext("/hello", exchange -> {
            final byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            final int port = server.getAddress().getPort();
            final Path project = project("http-client-get-string");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final HttpClient client = HttpClient.newHttpClient();
                        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/hello")).GET().build();
                        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        System.out.println(response.statusCode());
                        System.out.println(response.body());
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/http-client-get-string").toString())).stdout()).isEqualTo(jvmOutput);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpClientPostStringAndReadByteArrayBuildsAndMatchesJvmOutput() throws Exception {
        final com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0),
            0
        );
        server.createContext("/upload", exchange -> {
            try {
                final byte[] requestBody = exchange.getRequestBody().readAllBytes();
                final String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                final String requestText = new String(requestBody, StandardCharsets.UTF_8);
                final byte[] body;
                final int status;
                if ("text/plain".equals(contentType) && "hello".equals(requestText)) {
                    body = new byte[] {1, 0, 2, 3};
                    status = 201;
                } else {
                    body = new byte[] {9};
                    status = 400;
                }
                exchange.sendResponseHeaders(status, body.length);
                try (java.io.OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            final int port = server.getAddress().getPort();
            final Path project = project("http-client-post-string-byte-array");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final HttpClient client = HttpClient.newHttpClient();
                        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/upload"))
                            .header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString("hello"))
                            .build();
                        final HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        System.out.println(response.statusCode());
                        System.out.println(response.body().length);
                        System.out.println(response.body()[0]);
                        System.out.println(response.body()[1]);
                        System.out.println(response.body()[2]);
                        System.out.println(response.body()[3]);
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/http-client-post-string-byte-array").toString())).stdout()).isEqualTo(jvmOutput);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpClientPutByteArrayAndReadByteArrayBuildsAndMatchesJvmOutput() throws Exception {
        final com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0),
            0
        );
        server.createContext("/blob", exchange -> {
            try {
                final byte[] requestBody = exchange.getRequestBody().readAllBytes();
                final String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                final byte[] body;
                final int status;
                if ("PUT".equals(exchange.getRequestMethod())
                    && "application/octet-stream".equals(contentType)
                    && java.util.Arrays.equals(requestBody, new byte[] {4, 5, 6})) {
                    body = new byte[] {6, 5, 4};
                    status = 202;
                } else {
                    body = new byte[] {0};
                    status = 400;
                }
                exchange.sendResponseHeaders(status, body.length);
                try (java.io.OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            final int port = server.getAddress().getPort();
            final Path project = project("http-client-put-byte-array");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final HttpClient client = HttpClient.newHttpClient();
                        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/blob"))
                            .header("Content-Type", "application/octet-stream")
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[] {4, 5, 6}))
                            .build();
                        final HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        System.out.println(response.statusCode());
                        System.out.println(response.body().length);
                        System.out.println(response.body()[0]);
                        System.out.println(response.body()[1]);
                        System.out.println(response.body()[2]);
                    }
                }
                """.formatted(port));

            final String jvmOutput = runJvm(project, "com.acme.Main");
            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/http-client-put-byte-array").toString())).stdout()).isEqualTo(jvmOutput);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpServerSingleContextBuildsAndMatchesJvmOutput() throws Exception {
        final int port = freeTcpPort();
        final Path project = project("http-server-single-context");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import com.sun.net.httpserver.HttpExchange;
            import com.sun.net.httpserver.HttpServer;
            import java.net.InetSocketAddress;
            import java.net.URI;
            import java.net.http.HttpClient;
            import java.net.http.HttpRequest;
            import java.net.http.HttpResponse;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", %d), 0);
                    server.createContext("/hello", exchange -> {
                        final byte[] body = new byte[] {'p', 'o', 'n', 'g'};
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
                    server.start();
                    final HttpResponse<String> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/hello")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                    );
                    System.out.println(response.statusCode());
                    System.out.println(response.body());
                    server.stop(0);
                }
            }
            """.formatted(port, port));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeProcess = process(project, List.of(project.resolve(".javan/bin/http-server-single-context").toString()));
        assertThat(nativeProcess.stderr()).isEmpty();
        assertThat(nativeProcess.stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressLoopbackHostAddressBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-loopback-host-address");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(InetAddress.getLoopbackAddress().getHostAddress());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-loopback-host-address").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void socketInetAddressConstructorBuildsAndReadsFromLoopbackServer() throws Exception {
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            final CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().write(65);
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-inet-address-constructor-read-byte");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.io.InputStream;
                import java.net.InetAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket(InetAddress.getLoopbackAddress(), %d);
                        final InputStream in = socket.getInputStream();
                        System.out.println(in.read());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-inet-address-constructor-read-byte").toString())).stdout())
                .isEqualTo("65\n");
            served.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void socketInetAddressIpv6ConstructorBuildsAndReadsFromLoopbackServer() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final int port = freeTcpPort();
        try (java.net.ServerSocket server = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("::1"))) {
            final CompletableFuture<Void> served = CompletableFuture.runAsync(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getOutputStream().write(65);
                    socket.getOutputStream().flush();
                } catch (final Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            final Path project = project("socket-inet-address-ipv6-constructor-read-byte");
            writeJava(project, "com.acme.Main", """
                package com.acme;

                import java.io.InputStream;
                import java.net.InetAddress;
                import java.net.Socket;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) throws Exception {
                        final Socket socket = new Socket(InetAddress.getByName("::1"), %d);
                        final InputStream in = socket.getInputStream();
                        System.out.println(in.read());
                        socket.close();
                    }
                }
                """.formatted(port));

            final CliRun run = run(tempDir, "build", project.toString());

            assertThat(run.exitCode()).as(run.stderr()).isZero();
            assertThat(process(project, List.of(project.resolve(".javan/bin/socket-inet-address-ipv6-constructor-read-byte").toString())).stdout())
                .isEqualTo("65\n");
            served.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void inetAddressGetByNameIpv4LiteralBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-name-ipv4");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(InetAddress.getByName("127.0.0.1").getHostAddress());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-name-ipv4").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByNameLocalhostBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-name-localhost");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByName("localhost");
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-name-localhost").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByNameIpv6LoopbackBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-name-ipv6-loopback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByName("::1");
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                    System.out.println(address.getCanonicalHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-name-ipv6-loopback").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByNameIpv6LiteralBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-name-ipv6-literal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByName("2001:db8::1");
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-name-ipv6-literal").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetAllByNameIpv4LiteralBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-all-by-name-ipv4");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress[] addresses = InetAddress.getAllByName("127.0.0.1");
                    System.out.println(addresses.length);
                    System.out.println(addresses[0].getHostAddress());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-all-by-name-ipv4").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetAllByNameLocalhostBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-all-by-name-localhost");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress[] addresses = InetAddress.getAllByName("localhost");
                    System.out.println(addresses.length);
                    System.out.println(addresses[0].getHostAddress());
                    System.out.println(addresses[0].getHostName());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-all-by-name-localhost").toString())).stdout())
            .isEqualTo("1\n127.0.0.1\nlocalhost\n");
    }

    @Test
    void inetAddressGetAllByNameIpv6LiteralBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-all-by-name-ipv6-literal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress[] addresses = InetAddress.getAllByName("2001:db8::1");
                    System.out.println(addresses.length);
                    System.out.println(addresses[0].getHostAddress());
                    System.out.println(addresses[0].getHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-all-by-name-ipv6-literal").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetAddressIpv4BuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-address-ipv4");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final byte[] address = InetAddress.getByName("127.0.0.1").getAddress();
                    System.out.println(address.length);
                    System.out.println(address[0]);
                    System.out.println(address[1]);
                    System.out.println(address[2]);
                    System.out.println(address[3]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-address-ipv4").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetAddressIpv6BuildsAndMatchesJvmOutput() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final Path project = project("inet-address-get-address-ipv6");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final byte[] address = InetAddress.getByName("::1").getAddress();
                    System.out.println(address.length);
                    System.out.println(address[0]);
                    System.out.println(address[1]);
                    System.out.println(address[14]);
                    System.out.println(address[15]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-address-ipv6").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressIpv4BuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-address-ipv4");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-ipv4").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressIpv6BuildsAndMatchesJvmOutput() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final Path project = project("inet-address-get-by-address-ipv6");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByAddress(new byte[] {
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
                    });
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-ipv6").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressNamedLoopbackBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-address-named-loopback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByAddress("loop", new byte[] {127, 0, 0, 1});
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                    System.out.println(address.getCanonicalHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-named-loopback").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressNamedNonLoopbackBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-address-named-non-loopback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByAddress("named", new byte[] {1, 2, 3, 4});
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                    System.out.println(address.getCanonicalHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-named-non-loopback").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressNamedNullNameFallsBackAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-address-get-by-address-named-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByAddress(null, new byte[] {127, 0, 0, 1});
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                    System.out.println(address.getCanonicalHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-named-null").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressNamedIpv6LoopbackBuildsAndMatchesJvmOutput() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available on this host");
        final Path project = project("inet-address-get-by-address-named-ipv6-loopback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InetAddress address = InetAddress.getByAddress("loop6", new byte[] {
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
                    });
                    System.out.println(address.getHostAddress());
                    System.out.println(address.getHostName());
                    System.out.println(address.getCanonicalHostName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-named-ipv6-loopback").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetAddressGetByAddressNamedInvalidLengthFailsClearlyAtRuntime() throws Exception {
        final Path project = project("inet-address-get-by-address-named-invalid-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(InetAddress.getByAddress("named", new byte[] {1, 2, 3}).getHostAddress());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-named-invalid-length").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("addr is of illegal length");
    }

    @Test
    void inetAddressGetByAddressInvalidLengthFailsClearlyAtRuntime() throws Exception {
        final Path project = project("inet-address-get-by-address-invalid-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(InetAddress.getByAddress(new byte[] {1, 2, 3}).getHostAddress());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-address-invalid-length").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("addr is of illegal length");
    }

    @Test
    void inetAddressGetAllByNameDnsHostFailsClearlyAtRuntime() throws Exception {
        final Path project = project("inet-address-get-all-by-name-dns-runtime-fail");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(InetAddress.getAllByName("example.com")[0].getHostAddress());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/inet-address-get-all-by-name-dns-runtime-fail").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("unsupported inet address host");
    }

    @Test
    void inetAddressGetByNameDnsHostFailsClearlyAtRuntime() throws Exception {
        final Path project = project("inet-address-get-by-name-dns-runtime-fail");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(InetAddress.getByName("example.com").getHostAddress());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/inet-address-get-by-name-dns-runtime-fail").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("unsupported inet address host");
    }

    @Test
    void inetSocketAddressGetPortBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-socket-address-get-port");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetSocketAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new InetSocketAddress("127.0.0.1", 8080).getPort());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-socket-address-get-port").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetSocketAddressGetHostStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-socket-address-get-host-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetSocketAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new InetSocketAddress("127.0.0.1", 8080).getHostString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-socket-address-get-host-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetSocketAddressGetAddressHostAddressBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-socket-address-get-address-host-address");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetSocketAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new InetSocketAddress("127.0.0.1", 8080).getAddress().getHostAddress());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-socket-address-get-address-host-address").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetSocketAddressToStringFromHostBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-socket-address-to-string-from-host");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetSocketAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new InetSocketAddress("127.0.0.1", 8080).toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-socket-address-to-string-from-host").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inetSocketAddressToStringFromAddressBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("inet-socket-address-to-string-from-address");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetAddress;
            import java.net.InetSocketAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new InetSocketAddress(InetAddress.getLoopbackAddress(), 8080).toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inet-socket-address-to-string-from-address").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void socketGetChannelBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("socket-get-channel");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;
            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(0);
                    final Socket client = new Socket("127.0.0.1", server.getLocalPort());
                    final Socket accepted = server.accept();
                    System.out.println(client.getChannel() == null);
                    accepted.close();
                    client.close();
                    server.close();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/socket-get-channel").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketGetChannelBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("server-socket-get-channel");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket(0);
                    System.out.println(server.getChannel() == null);
                    server.close();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-get-channel").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void serverSocketExplicitBindLifecycleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("server-socket-explicit-bind-lifecycle");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.InetSocketAddress;
            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket();
                    System.out.println(server.isBound());
                    System.out.println(server.getLocalSocketAddress() == null);
                    server.bind(new InetSocketAddress("127.0.0.1", 0));
                    System.out.println(server.isBound());
                    System.out.println(server.getLocalPort() > 0);
                    System.out.println(server.getInetAddress().getHostAddress());
                    System.out.println(server.getLocalSocketAddress() != null);
                    server.close();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/server-socket-explicit-bind-lifecycle").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void buildRejectsReachableDisabledSocketRuntimeModuleForInetAddressLoopback() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-socket-build", "socket", """
            package com.acme;

            import java.net.InetAddress;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(InetAddress.getLoopbackAddress().getHostAddress());
                }
            }
            """);
    }

    @Test
    void checkAcceptsReachableSocketLifecycleAndReportsNetworkRuntimeModules() throws Exception {
        final Path project = project("socket-lifecycle-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.Socket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Socket socket = new Socket();
                    System.out.println(socket.isConnected());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"network\"",
            "\"socket\"",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAcceptsReachableServerSocketLifecycleAndReportsNetworkRuntimeModules() throws Exception {
        final Path project = project("server-socket-lifecycle-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.ServerSocket;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ServerSocket server = new ServerSocket();
                    System.out.println(server.isBound());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"network\"",
            "\"socket\"",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAcceptsReachableHttpClientAndReportsHttpRuntimeModules() throws Exception {
        final Path project = project("http-client-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.net.http.HttpClient;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    HttpClient.newHttpClient();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"http\", \"network\"]",
            "\"status\": \"pass\""
        );
    }

    private static boolean ipv6LoopbackAvailable() {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("::1"))) {
            return server.getInetAddress() != null;
        } catch (final Exception exception) {
            return false;
        }
    }

}

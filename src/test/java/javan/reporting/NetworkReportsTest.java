package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class NetworkReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void reportsReachableExternalHostsWithoutRecordingUrlsOrLoopbackEndpoints() throws Exception {
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                literal("HTTPS://user:private-password@API.EXAMPLE.COM:8443/v1/items?token=private-token"),
                invocation("java/net/URL", "<init>", "(Ljava/lang/String;)V"),
                literal("https://private-token"),
                invocation("java/net/URL", "<init>", "(Ljava/lang/String;)V"),
                literal("https://api.example.com/health"),
                invocation("java/net/URI", "create", "(Ljava/lang/String;)Ljava/net/URI;"),
                literal("cache.example.test"),
                invocation("java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;"),
                literal("localhost"),
                invocation("java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;"),
                literal("private-token"),
                invocation("java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;"),
                literal("remote-host"),
                intLiteral(443),
                invocation("java/net/Socket", "<init>", "(Ljava/lang/String;I)V"),
                literal("socket.example.test"),
                intLiteral(8443),
                invocation("java/net/Socket", "<init>", "(Ljava/lang/String;I)V")
            )),
            "com/acme/Hidden",
            classFile("com/acme/Hidden", method(
                "hidden",
                "()V",
                literal("https://hidden.example.test/never"),
                invocation("java/net/URI", "create", "(Ljava/lang/String;)Ljava/net/URI;")
            ))
        );
        final NetworkReports reports = new NetworkReports();
        final NetworkReports.Report report = reports.analyze(classes, List.of(entry));

        final List<Path> written = reports.write(tempDir, classes, new CallGraph(entry, List.of(entry), List.of()));

        assertThat(report.reachableNetworkCallSiteCount()).isEqualTo(8);
        assertThat(report.endpointCallSiteCount()).isEqualTo(8);
        assertThat(report.knownExternalEndpointCallSiteCount()).isEqualTo(4);
        assertThat(report.excludedInternalEndpointCallSiteCount()).isEqualTo(1);
        assertThat(report.unknownEndpointCallSiteCount()).isEqualTo(3);
        assertThat(report.hosts()).containsExactly(
            new NetworkReports.HostCount("api.example.com", 2),
            new NetworkReports.HostCount("cache.example.test", 1),
            new NetworkReports.HostCount("socket.example.test", 1)
        );
        assertThat(report.unknownEndpointCalls()).containsExactly(
            new NetworkReports.TargetCount("java/net/InetAddress.getByName(Ljava/lang/String;)Ljava/net/InetAddress;", 1),
            new NetworkReports.TargetCount("java/net/Socket.<init>(Ljava/lang/String;I)V", 1),
            new NetworkReports.TargetCount("java/net/URL.<init>(Ljava/lang/String;)V", 1)
        );
        assertThat(written).containsExactly(
            tempDir.resolve("reports/network.json"),
            tempDir.resolve("reports/network.md")
        );
        assertThat(Files.readString(tempDir.resolve("reports/network.json"))).contains(
            "\"reachableNetworkCallSiteCount\": 8",
            "\"knownExternalEndpointCallSiteCount\": 4",
            "{\"host\": \"api.example.com\", \"count\": 2}",
            "{\"host\": \"cache.example.test\", \"count\": 1}",
            "{\"host\": \"socket.example.test\", \"count\": 1}"
        ).doesNotContain("private-password", "private-token", "hidden.example.test", "localhost", "remote-host");
        assertThat(Files.readString(tempDir.resolve("reports/network.md"))).contains(
            "# Reachable Network",
            "| `api.example.com` | 2 |",
            "| `cache.example.test` | 1 |"
        ).doesNotContain("private-password", "private-token", "hidden.example.test", "localhost", "remote-host");
    }

    @Test
    void excludesPrivateAndLocalNetworkHosts() {
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                literal("10.4.5.6"), inetByName(),
                literal("172.16.0.1"), inetByName(),
                literal("172.31.255.255"), inetByName(),
                literal("192.168.1.1"), inetByName(),
                literal("169.254.3.2"), inetByName(),
                literal("fd00::1"), inetByName(),
                literal("fe80::1"), inetByName(),
                literal("service.local"), inetByName(),
                literal("api.example.test"), inetByName()
            ))
        );

        final NetworkReports.Report report = new NetworkReports().analyze(classes, List.of(entry));

        assertThat(report.knownExternalEndpointCallSiteCount()).isOne();
        assertThat(report.excludedInternalEndpointCallSiteCount()).isEqualTo(8);
        assertThat(report.unknownEndpointCallSiteCount()).isZero();
        assertThat(report.hosts()).containsExactly(new NetworkReports.HostCount("api.example.test", 1));
    }

    private static ClassFile classFile(final String name, final MethodInfo method) {
        return new ClassFile(65, name, "java/lang/Object", 0, List.of(), List.of(), List.of(method), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final String name, final String descriptor, final Instruction... instructions) {
        return new MethodInfo(0, name, descriptor, Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(instructions))));
    }

    private static Instruction invocation(final String owner, final String name, final String descriptor) {
        return new Instruction(
            0, 182, "invokevirtual", new byte[0], Optional.of(new MethodRef(owner, name, descriptor)), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction inetByName() {
        return invocation("java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;");
    }

    private static Instruction literal(final String value) {
        return new Instruction(
            0, 18, "ldc", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value),
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction intLiteral(final int value) {
        return new Instruction(
            0, 16, "bipush", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(value), Optional.empty(), Optional.empty()
        );
    }
}

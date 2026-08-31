package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes deterministic reachable network API evidence without making network connections.
 */
public final class NetworkReports {
    private static final String NET = "java/net/";
    private static final String HTTP_SERVER = "com/sun/net/httpserver/";
    private static final MethodRef URL_STRING = new MethodRef("java/net/URL", "<init>", "(Ljava/lang/String;)V");
    private static final MethodRef URI_CREATE = new MethodRef("java/net/URI", "create", "(Ljava/lang/String;)Ljava/net/URI;");
    private static final MethodRef INET_BY_NAME = new MethodRef(
        "java/net/InetAddress", "getByName", "(Ljava/lang/String;)Ljava/net/InetAddress;"
    );

    /**
     * Analyzes reachable bytecode and writes JSON and Markdown network reports.
     *
     * @param outputDirectory Javan output directory
     * @param classes parsed application and dependency classes
     * @param callGraph closed-world reachability result
     * @return written report paths
     * @throws IOException when report files cannot be written
     */
    public List<Path> write(
        final Path outputDirectory,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        final Report report = analyze(classes, callGraph.reachableMethods());
        final Path json = outputDirectory.resolve("reports/network.json");
        final Path markdown = outputDirectory.resolve("reports/network.md");
        Files2.writeString(json, json(report));
        Files2.writeString(markdown, markdown(report));
        return List.of(json, markdown);
    }

    /**
     * Counts reachable network APIs and strictly provable external host literals.
     *
     * @param classes parsed application and dependency classes
     * @param reachable reachable application methods
     * @return immutable network evidence
     */
    Report analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<TargetCount> networkCalls = new ArrayList<>();
        final List<HostCount> hosts = new ArrayList<>();
        final List<TargetCount> unknownEndpointCalls = new ArrayList<>();
        int reachableNetworkCallSites = 0;
        int endpointCallSites = 0;
        int knownExternalEndpointCallSites = 0;
        int excludedInternalEndpointCallSites = 0;

        for (final EntryPoint entry : reachable) {
            final Optional<MethodInfo> method = method(classes, entry);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Optional<MethodRef> reference = instructions.get(index).methodRef();
                if (reference.isEmpty() || !networkApi(reference.orElseThrow())) {
                    continue;
                }
                final MethodRef target = reference.orElseThrow();
                reachableNetworkCallSites++;
                increment(networkCalls, target.display());
                if (!endpointApi(target)) {
                    continue;
                }
                endpointCallSites++;
                final Optional<String> host = literalHost(target, instructions, index);
                if (host.isEmpty()) {
                    increment(unknownEndpointCalls, target.display());
                } else if (internal(host.orElseThrow())) {
                    excludedInternalEndpointCallSites++;
                } else {
                    knownExternalEndpointCallSites++;
                    incrementHost(hosts, host.orElseThrow());
                }
            }
        }

        return new Report(
            reachableNetworkCallSites,
            endpointCallSites,
            knownExternalEndpointCallSites,
            excludedInternalEndpointCallSites,
            endpointCallSites - knownExternalEndpointCallSites - excludedInternalEndpointCallSites,
            List.copyOf(hosts),
            List.copyOf(unknownEndpointCalls),
            List.copyOf(networkCalls)
        );
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile classFile = classes.get(entry.className());
        return classFile == null ? Optional.empty() : classFile.method(entry.methodName(), entry.descriptor());
    }

    private static boolean networkApi(final MethodRef reference) {
        return reference.owner().startsWith(NET) || reference.owner().startsWith(HTTP_SERVER);
    }

    private static boolean endpointApi(final MethodRef reference) {
        return URL_STRING.equals(reference)
            || URI_CREATE.equals(reference)
            || INET_BY_NAME.equals(reference)
            || ("java/net/Socket".equals(reference.owner())
                && "<init>".equals(reference.name())
                && reference.descriptor().startsWith("(Ljava/lang/String;"));
    }

    private static Optional<String> literalHost(
        final MethodRef reference,
        final List<Instruction> instructions,
        final int invocationIndex
    ) {
        if (invocationIndex == 0
            || (!reference.equals(URL_STRING) && !reference.equals(URI_CREATE) && !reference.equals(INET_BY_NAME))) {
            return Optional.empty();
        }
        final Optional<String> literal = instructions.get(invocationIndex - 1).stringValue();
        if (literal.isEmpty()) {
            return Optional.empty();
        }
        if (reference.equals(INET_BY_NAME)) {
            return literal.filter(value -> internal(value) || externalHostLiteral(value));
        }
        return httpHost(literal.orElseThrow());
    }

    private static boolean externalHostLiteral(final String value) {
        if (value.isEmpty() || (value.indexOf('.') < 0 && value.indexOf(':') < 0)) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if ((current >= 'a' && current <= 'z')
                || (current >= 'A' && current <= 'Z')
                || (current >= '0' && current <= '9')
                || current == '.'
                || current == '-'
                || current == ':') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static Optional<String> httpHost(final String value) {
        final int schemeEnd = value.indexOf("://");
        if (schemeEnd <= 0) {
            return Optional.empty();
        }
        final String scheme = Strings2.toAsciiLowerCase(Strings2.slice(value, 0, schemeEnd));
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Optional.empty();
        }
        final int authorityStart = schemeEnd + 3;
        final int authorityEnd = authorityEnd(value, authorityStart);
        if (authorityStart == authorityEnd) {
            return Optional.empty();
        }
        final String authority = Strings2.slice(value, authorityStart, authorityEnd);
        final int userInfo = authority.lastIndexOf('@');
        final String hostPort = userInfo < 0 ? authority : Strings2.slice(authority, userInfo + 1, authority.length());
        return host(hostPort).filter(host -> internal(host) || externalHostLiteral(host));
    }

    private static int authorityEnd(final String value, final int start) {
        int end = value.length();
        for (int index = start; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '/' || current == '?' || current == '#') {
                end = index;
                break;
            }
        }
        return end;
    }

    private static Optional<String> host(final String hostPort) {
        if (hostPort.isEmpty()) {
            return Optional.empty();
        }
        if (hostPort.charAt(0) == '[') {
            final int closing = hostPort.indexOf(']');
            return closing < 2
                ? Optional.empty()
                : Optional.of(Strings2.toAsciiLowerCase(Strings2.slice(hostPort, 1, closing)));
        }
        final int port = hostPort.indexOf(':');
        final String host = port < 0 ? hostPort : Strings2.slice(hostPort, 0, port);
        return host.isEmpty() ? Optional.empty() : Optional.of(Strings2.toAsciiLowerCase(host));
    }

    private static boolean internal(final String host) {
        final String normalized = Strings2.toAsciiLowerCase(host);
        return "localhost".equals(normalized)
            || normalized.endsWith(".localhost")
            || normalized.startsWith("127.")
            || "::1".equals(normalized)
            || "0.0.0.0".equals(normalized);
    }

    private static void incrementHost(final List<HostCount> hosts, final String host) {
        for (int index = 0; index < hosts.size(); index++) {
            final HostCount existing = hosts.get(index);
            final int comparison = Strings2.compareAscii(host, existing.host());
            if (comparison == 0) {
                hosts.set(index, new HostCount(host, existing.count() + 1));
                return;
            }
            if (comparison < 0) {
                hosts.add(index, new HostCount(host, 1));
                return;
            }
        }
        hosts.add(new HostCount(host, 1));
    }

    private static void increment(final List<TargetCount> targets, final String target) {
        for (int index = 0; index < targets.size(); index++) {
            final TargetCount existing = targets.get(index);
            final int comparison = Strings2.compareAscii(target, existing.target());
            if (comparison == 0) {
                targets.set(index, new TargetCount(target, existing.count() + 1));
                return;
            }
            if (comparison < 0) {
                targets.add(index, new TargetCount(target, 1));
                return;
            }
        }
        targets.add(new TargetCount(target, 1));
    }

    private static String json(final Report report) {
        return new StringBuilder()
            .append("{\n")
            .append("  \"schemaVersion\": \"1\",\n")
            .append("  \"reachableNetworkCallSiteCount\": ").append(report.reachableNetworkCallSiteCount()).append(",\n")
            .append("  \"endpointCallSiteCount\": ").append(report.endpointCallSiteCount()).append(",\n")
            .append("  \"knownExternalEndpointCallSiteCount\": ").append(report.knownExternalEndpointCallSiteCount()).append(",\n")
            .append("  \"excludedInternalEndpointCallSiteCount\": ").append(report.excludedInternalEndpointCallSiteCount()).append(",\n")
            .append("  \"unknownEndpointCallSiteCount\": ").append(report.unknownEndpointCallSiteCount()).append(",\n")
            .append("  \"knownExternalHosts\": [\n").append(hostsJson(report.hosts())).append("  ],\n")
            .append("  \"unknownEndpointCalls\": [\n").append(targetsJson(report.unknownEndpointCalls())).append("  ],\n")
            .append("  \"networkCalls\": [\n").append(targetsJson(report.networkCalls())).append("  ]\n")
            .append("}\n")
            .toString();
    }

    private static String hostsJson(final List<HostCount> hosts) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < hosts.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final HostCount host = hosts.get(index);
            result.append("    {\"host\": ").append(Json.string(host.host())).append(", \"count\": ")
                .append(host.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String targetsJson(final List<TargetCount> targets) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < targets.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final TargetCount target = targets.get(index);
            result.append("    {\"target\": ").append(Json.string(target.target())).append(", \"count\": ")
                .append(target.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String markdown(final Report report) {
        final StringBuilder result = new StringBuilder();
        result.append("# Reachable Network\n\n");
        result.append("The compiler scans reachable network APIs without making connections. It records only host names proven ")
            .append("from direct literals, omits complete URLs, and excludes loopback hosts.\n\n");
        result.append("- reachable network API call sites: `").append(report.reachableNetworkCallSiteCount()).append("`\n");
        result.append("- potential endpoint call sites: `").append(report.endpointCallSiteCount()).append("`\n");
        result.append("- known external endpoint call sites: `").append(report.knownExternalEndpointCallSiteCount()).append("`\n");
        result.append("- excluded internal endpoint call sites: `").append(report.excludedInternalEndpointCallSiteCount()).append("`\n");
        result.append("- unknown endpoint call sites: `").append(report.unknownEndpointCallSiteCount()).append("`\n\n");
        result.append("## Known External Hosts\n\n");
        result.append("| Host | Reachable call sites |\n");
        result.append("| --- | ---: |\n");
        appendHosts(result, report.hosts());
        result.append("\n## Unknown Endpoint Calls\n\n");
        result.append("| Network target | Reachable call sites |\n");
        result.append("| --- | ---: |\n");
        appendTargets(result, report.unknownEndpointCalls());
        return result.toString();
    }

    private static void appendHosts(final StringBuilder result, final List<HostCount> hosts) {
        if (hosts.isEmpty()) {
            result.append("| none | 0 |\n");
            return;
        }
        for (final HostCount host : hosts) {
            result.append("| `").append(host.host()).append("` | ").append(host.count()).append(" |\n");
        }
    }

    private static void appendTargets(final StringBuilder result, final List<TargetCount> targets) {
        if (targets.isEmpty()) {
            result.append("| none | 0 |\n");
            return;
        }
        for (final TargetCount target : targets) {
            result.append("| `").append(target.target()).append("` | ").append(target.count()).append(" |\n");
        }
    }

    record Report(
        int reachableNetworkCallSiteCount,
        int endpointCallSiteCount,
        int knownExternalEndpointCallSiteCount,
        int excludedInternalEndpointCallSiteCount,
        int unknownEndpointCallSiteCount,
        List<HostCount> hosts,
        List<TargetCount> unknownEndpointCalls,
        List<TargetCount> networkCalls
    ) {
    }

    record HostCount(String host, int count) {
    }

    record TargetCount(String target, int count) {
    }
}

package javan.testing;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.platform.launcher.TagFilter.includeTags;

/** Discovers one execution suite and deterministically divides it between CI workers. */
public final class CiTestWorkerPlanner {
    private static final int LARGE_CLASS_METHODS = 100;
    private static final Map<String, Double> NATIVE_CLASS_SECONDS = durations();

    private CiTestWorkerPlanner() {
    }

    public static void main(final String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: <suite> <worker-index> <worker-count>");
        }
        System.out.println(selector(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2])));
    }

    static String selector(final String suite, final int workerIndex, final int workerCount) {
        if (workerCount < 1 || workerIndex < 0 || workerIndex >= workerCount) {
            throw new IllegalArgumentException("worker index must be within worker count");
        }

        final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(Path.of("target/test-classes"))))
            .filters(includeTags(suite))
            .build();
        final var plan = LauncherFactory.create().discover(request);
        final List<String> tests = plan.getRoots().stream()
            .flatMap(root -> plan.getDescendants(root).stream())
            .map(TestIdentifier::getSource)
            .flatMap(java.util.Optional::stream)
            .filter(MethodSource.class::isInstance)
            .map(MethodSource.class::cast)
            .map(source -> source.getClassName() + "#" + source.getMethodName())
            .distinct()
            .sorted()
            .toList();

        final List<String> selected = distribute(suite, tests, workerCount).get(workerIndex);
        if (selected.isEmpty()) {
            throw new IllegalStateException("suite " + suite + " produced an empty worker " + workerIndex);
        }
        return toSurefireSelector(selected);
    }

    private static List<List<String>> distribute(final String suite, final List<String> tests, final int workerCount) {
        final Map<String, List<String>> methodsByClass = new LinkedHashMap<>();
        for (final String test : tests) {
            final int separator = test.indexOf('#');
            methodsByClass.computeIfAbsent(test.substring(0, separator), ignored -> new ArrayList<>()).add(test);
        }
        final List<Worker> workers = new ArrayList<>();
        for (int index = 0; index < workerCount; index++) {
            workers.add(new Worker());
        }
        final boolean splitClasses = methodsByClass.size() < workerCount;
        methodsByClass.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, List<String>>>comparingDouble(entry -> weight(suite, entry.getKey(), entry.getValue().size()))
                .reversed()
                .thenComparing(Map.Entry::getKey))
            .forEach(entry -> {
                if (splitClasses || entry.getValue().size() > LARGE_CLASS_METHODS) {
                    final double perMethod = weight(suite, entry.getKey(), entry.getValue().size()) / entry.getValue().size();
                    entry.getValue().forEach(test -> leastLoaded(workers).add(test, perMethod));
                } else {
                    leastLoaded(workers).addAll(entry.getValue(), weight(suite, entry.getKey(), entry.getValue().size()));
                }
            });
        return workers.stream().map(worker -> List.copyOf(worker.tests)).toList();
    }

    static double estimatedSeconds(final String suite, final String test) {
        final int separator = test.indexOf('#');
        return weight(suite, test.substring(0, separator), 1);
    }

    private static Worker leastLoaded(final List<Worker> workers) {
        return workers.stream().min(Comparator.comparingDouble(worker -> worker.seconds)).orElseThrow();
    }

    private static double weight(final String suite, final String className, final int methods) {
        return "native".equals(suite) ? NATIVE_CLASS_SECONDS.getOrDefault(className, (double) methods) : methods;
    }

    private static Map<String, Double> durations() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Objects.requireNonNull(CiTestWorkerPlanner.class.getResourceAsStream("/javan/testing/native-class-durations.tsv"),
                "native test duration profile"), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.startsWith("#")).map(line -> line.split("\\t"))
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0], parts -> Double.parseDouble(parts[1])));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load native test durations", exception);
        }
    }

    private static final class Worker {
        private final List<String> tests = new ArrayList<>();
        private double seconds;
        private void add(final String test, final double estimatedSeconds) { tests.add(test); seconds += estimatedSeconds; }
        private void addAll(final List<String> tests, final double estimatedSeconds) { this.tests.addAll(tests); seconds += estimatedSeconds; }
    }

    private static String toSurefireSelector(final List<String> tests) {
        final Map<String, List<String>> methodsByClass = new LinkedHashMap<>();
        for (final String test : tests) {
            final int separator = test.indexOf('#');
            methodsByClass.computeIfAbsent(test.substring(0, separator), ignored -> new ArrayList<>())
                .add(test.substring(separator + 1));
        }
        return methodsByClass.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "#" + entry.getValue().stream().sorted().reduce((a, b) -> a + "+" + b).orElseThrow())
            .reduce((a, b) -> a + "," + b)
            .orElseThrow();
    }
}

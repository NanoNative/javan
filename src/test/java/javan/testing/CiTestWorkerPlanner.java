package javan.testing;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.platform.launcher.TagFilter.includeTags;

/** Discovers one execution suite and deterministically divides it between CI workers. */
public final class CiTestWorkerPlanner {
    private static final int LARGE_CLASS_METHODS = 100;

    private CiTestWorkerPlanner() {
    }

    public static void main(final String[] args) {
        final String[] plannerArgs = args.length == 1 ? args[0].split(":", -1) : args;
        if (plannerArgs.length != 3) {
            throw new IllegalArgumentException("usage: <suite> <worker-index> <worker-count> or <suite>:<worker-index>:<worker-count>");
        }
        System.out.println(selector(
            plannerArgs[0],
            Integer.parseInt(plannerArgs[1]),
            Integer.parseInt(plannerArgs[2])
        ));
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

        final List<String> selected = distribute(tests, workerCount).get(workerIndex);
        if (selected.isEmpty()) {
            throw new IllegalStateException("suite " + suite + " produced an empty worker " + workerIndex);
        }
        return toSurefireSelector(selected);
    }

    private static List<List<String>> distribute(final List<String> tests, final int workerCount) {
        final Map<String, List<String>> methodsByClass = new LinkedHashMap<>();
        for (final String test : tests) {
            final int separator = test.indexOf('#');
            methodsByClass.computeIfAbsent(test.substring(0, separator), ignored -> new ArrayList<>()).add(test);
        }
        final List<List<String>> workers = new ArrayList<>();
        for (int index = 0; index < workerCount; index++) {
            workers.add(new ArrayList<>());
        }
        final boolean splitClasses = methodsByClass.size() < workerCount;
        methodsByClass.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, List<String>>>comparingInt(entry -> entry.getValue().size())
                .reversed()
                .thenComparing(Map.Entry::getKey))
            .forEach(entry -> {
                if (splitClasses || entry.getValue().size() > LARGE_CLASS_METHODS) {
                    entry.getValue().forEach(test -> leastLoaded(workers).add(test));
                } else {
                    leastLoaded(workers).addAll(entry.getValue());
                }
            });
        return workers;
    }

    private static List<String> leastLoaded(final List<List<String>> workers) {
        return workers.stream().min(Comparator.comparingInt(List::size)).orElseThrow();
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

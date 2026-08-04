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

/** Discovers one execution suite and deterministically divides it between CI runners. */
public final class CiTestShardPlanner {
    private static final int LARGE_CLASS_METHODS = 100;

    private CiTestShardPlanner() {
    }

    public static void main(final String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: <suite> <shard-index> <shard-count>");
        }
        System.out.println(selector(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2])));
    }

    static String selector(final String suite, final int shardIndex, final int shardCount) {
        if (shardCount < 1 || shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("shard index must be within shard count");
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

        final List<String> selected = distribute(tests, shardCount).get(shardIndex);
        if (selected.isEmpty()) {
            throw new IllegalStateException("suite " + suite + " produced an empty shard " + shardIndex);
        }
        return toSurefireSelector(selected);
    }

    private static List<List<String>> distribute(final List<String> tests, final int shardCount) {
        final Map<String, List<String>> methodsByClass = new LinkedHashMap<>();
        for (final String test : tests) {
            final int separator = test.indexOf('#');
            methodsByClass.computeIfAbsent(test.substring(0, separator), ignored -> new ArrayList<>()).add(test);
        }
        final List<List<String>> shards = new ArrayList<>();
        for (int index = 0; index < shardCount; index++) {
            shards.add(new ArrayList<>());
        }
        methodsByClass.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, List<String>>>comparingInt(entry -> entry.getValue().size())
                .reversed()
                .thenComparing(Map.Entry::getKey))
            .forEach(entry -> {
                if (entry.getValue().size() > LARGE_CLASS_METHODS) {
                    entry.getValue().forEach(test -> leastLoaded(shards).add(test));
                } else {
                    leastLoaded(shards).addAll(entry.getValue());
                }
            });
        return shards;
    }

    private static List<String> leastLoaded(final List<List<String>> shards) {
        return shards.stream().min(Comparator.comparingInt(List::size)).orElseThrow();
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

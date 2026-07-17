package javan.compat;

import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
final class CompatibilityReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeOrdersJdkInventoryClassesByName() throws Exception {
        final List<ClassMetadata> jdkClasses = new ArrayList<>();
        for (int index = 25; index >= 0; index--) {
            jdkClasses.add(metadata("java.base", "java/lang/C" + twoDigits(index)));
        }

        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            jdkClasses,
            List.of()
        );

        final int feature = Runtime.version().feature();
        final String inventory = Files.readString(tempDir.resolve(".javan/reports/jdk-" + feature + "-inventory.json"));
        assertThat(inventory.indexOf("\"name\": \"java/lang/C00\""))
            .isLessThan(inventory.indexOf("\"name\": \"java/lang/C25\""));
    }

    @Test
    void writeSummaryCountsSupportRowsByStatus() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(
                metadata(
                    "java.base",
                    "java/lang/Object",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of(
                        member(0, "getClass", "()Ljava/lang/Class;", List.of(), List.of()),
                        member(0, "wait", "()V", List.of(), List.of())
                    )
                ),
                metadata(
                    "java.base",
                    "java/lang/Class",
                    0,
                    List.of(),
                    List.of(member(0, "forName", "(Ljava/lang/String;)Ljava/lang/Class;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/util/concurrent/Executors",
                    0,
                    List.of(),
                    List.of(member(0, "newSingleThreadExecutor", "()Ljava/util/concurrent/ExecutorService;", List.of(), List.of()))
                )
            ),
            List.of()
        );

        final String summary = Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"));

        assertThat(summary).contains(
            "\"exactSupportedJdkCallables\": {\"classes\": 1, \"constructors\": 1, \"methods\": 1, \"callables\": 2, \"totalCallables\": 5, \"leftCallables\": 3, \"coveragePercent\": \"40.0\"}",
            "\"exactJdkCallableAccounting\": {\"supportedCallables\": 2, \"explicitRejectedCallables\": 3, \"doneCallables\": 5, \"unknownCallables\": 0, \"totalCallables\": 5, \"donePercent\": \"100.0\"}",
            "\"supportRows\": 298",
            "\"passRows\": 298",
            "\"scopedRows\": 0",
            "\"targetRows\": 0",
            "\"rejectedRows\": 0",
            "\"accountedRows\": 298",
            "\"unaccountedRows\": 0"
        );
    }

    @Test
    void writeSupportMatrixIncludesEvidenceBackedMemoryAndLibraryRows() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(metadata("java.base", "java/lang/Object")),
            List.of()
        );

        final int feature = Runtime.version().feature();
        final String matrix = Files.readString(tempDir.resolve("doc/status/support-matrix.md"));
        final String json = Files.readString(tempDir.resolve("doc/status/support-matrix.json"));

        assertThat(matrix).contains(
            "Real external project probes are tracked separately in `doc/status/real-project-readiness.md`.",
            "External project names do not belong in this matrix; this ledger stays compiler-owned and deterministic.",
            "| `try-catch` | pass |",
            "| `try-finally` | pass |",
            "| `boxed-primitive-gc` | pass |",
            "| `enum-basic` | pass |",
            "| `enum-ordinal` | pass |",
            "| `enum-values` | pass |",
            "| `enum-switch` | pass |",
            "| `enum-value-of` | pass |",
            "| `interface-dispatch` | pass |",
            "| `polymorphic-virtual` | pass |",
            "| `interface-polymorphic` | pass |",
            "| `string-intrinsics` | pass |",
            "| `resource-system-loader-stream` | pass |",
            "| `resource-loader-instance-stream` | pass |",
            "| `library-last-error-abi` | pass |",
            "| `library-c-result-wrapper-success` | pass |",
            "| `library-retained-input-ownership` | pass |",
            "| `library-negative-byte-array-rejection` | pass |",
            "| `hashmap-realloc-gc` | pass |",
            "| `collections-empty-set` | pass |",
            "| `collections-unmodifiable-collection` | pass |",
            "| `collections-unmodifiable-set` | pass |",
            "| `collections-unmodifiable-list` | pass |",
            "| `collection-add` | pass |",
            "| `collection-add-all` | pass |",
            "| `collection-remove-all` | pass |",
            "| `collection-retain-all` | pass |",
            "| `collection-remove-if` | pass |",
            "| `collection-contains-all` | pass |",
            "| `collection-remove` | pass |",
            "| `collection-clear` | pass |",
            "| `abstractlist-direct-owner-add` | pass |",
            "| `abstractlist-direct-owner-clear` | pass |",
            "| `abstractlist-direct-owner-indexed-surface` | pass |",
            "| `abstractlist-direct-owner-index-of` | pass |",
            "| `abstractlist-direct-owner-iterator` | pass |",
            "| `abstractlist-direct-owner-list-iterator` | pass |",
            "| `abstractlist-direct-owner-last-index-of` | pass |",
            "| `list-add-all-at` | pass |",
            "| `list-remove-object` | pass |",
            "| `list-index-of` | pass |",
            "| `list-last-index-of` | pass |",
            "| `list-list-iterator-at` | pass |",
            "| `list-remove-at` | pass |",
            "| `list-remove-all` | pass |",
            "| `list-retain-all` | pass |",
            "| `list-contains-all` | pass |",
            "| `listiterator-previous` | pass |",
            "| `listiterator-indexes` | pass |",
            "| `listiterator-set` | pass |",
            "| `listiterator-add` | pass |",
            "| `listiterator-remove` | pass |",
            "| `iterator-remove` | pass |",
            "| `iterator-foreach-remaining` | pass |",
            "| `iterable-foreach` | pass |",
            "| `bifunction-apply` | pass |",
            "| `map-compute` | pass |",
            "| `map-compute-if-present` | pass |",
            "| `map-merge` | pass |",
            "| `predicate-test` | pass |",
            "| `set-add-all` | pass |",
            "| `set-remove-all` | pass |",
            "| `set-retain-all` | pass |",
            "| `set-contains-all` | pass |",
            "| `set-remove` | pass |",
            "| `set-clear` | pass |",
            "| `hashset-capacity-constructor` | pass |",
            "| `linkedhashset-capacity-constructor` | pass |",
            "| `hashset-load-factor-constructor` | pass |",
            "| `linkedhashset-load-factor-constructor` | pass |",
            "| `hashset-collection-constructor` | pass |",
            "| `linkedhashset-collection-constructor` | pass |",
            "| `hashset-static-factory` | pass |",
            "| `linkedhashset-static-factory` | pass |",
            "| `arraylist-direct-owner-read-surface` | pass |",
            "| `arraylist-direct-owner-get-first` | pass |",
            "| `arraylist-direct-owner-get-last` | pass |",
            "| `arraylist-direct-owner-add-all-at` | pass |",
            "| `arraylist-direct-owner-remove-object` | pass |",
            "| `arraylist-direct-owner-index-of` | pass |",
            "| `arraylist-direct-owner-last-index-of` | pass |",
            "| `arraylist-direct-owner-set` | pass |",
            "| `arraylist-direct-owner-remove-at` | pass |",
            "| `arraylist-direct-owner-remove-last` | pass |",
            "| `arraylist-direct-owner-add-first` | pass |",
            "| `arraylist-direct-owner-add-last` | pass |",
            "| `arraylist-direct-owner-remove-first` | pass |",
            "| `arraylist-direct-owner-remove-all` | pass |",
            "| `arraylist-direct-owner-retain-all` | pass |",
            "| `hashset-direct-owner-read-surface` | pass |",
            "| `hashset-direct-owner-add-all` | pass |",
            "| `hashset-direct-owner-remove-all` | pass |",
            "| `hashset-direct-owner-retain-all` | pass |",
            "| `hashset-direct-owner-remove` | pass |",
            "| `hashset-direct-owner-clear` | pass |",
            "| `linkedhashset-direct-owner-read-surface` | pass |",
            "| `linkedhashset-direct-owner-add-all` | pass |",
            "| `linkedhashset-direct-owner-remove-all` | pass |",
            "| `linkedhashset-direct-owner-retain-all` | pass |",
            "| `linkedhashset-direct-owner-remove` | pass |",
            "| `linkedhashset-direct-owner-clear` | pass |",
            "| `hashmap-capacity-constructor` | pass |",
            "| `linkedhashmap-capacity-constructor` | pass |",
            "| `hashmap-load-factor-constructor` | pass |",
            "| `linkedhashmap-load-factor-constructor` | pass |",
            "| `hashmap-map-constructor` | pass |",
            "| `linkedhashmap-map-constructor` | pass |",
            "| `hashmap-static-factory` | pass |",
            "| `linkedhashmap-static-factory` | pass |",
            "| `concurrenthashmap-capacity-constructor` | pass |",
            "| `concurrenthashmap-map-constructor` | pass |",
            "| `concurrenthashmap-load-factor-constructor` | pass |",
            "| `concurrenthashmap-concurrency-level-constructor` | pass |",
            "| `collection-to-array` | pass |",
            "| `list-to-array` | pass |",
            "| `set-to-array` | pass |",
            "| `collections-singleton-set` | pass |",
            "| `collections-singleton-list` | pass |",
            "| `collections-empty-list` | pass |",
            "| `collections-singleton-map` | pass |",
            "| `collections-unmodifiable-map` | pass |",
            "| `map-entry` | pass |",
            "| `map-foreach` | pass |",
            "| `map-clear` | pass |",
            "| `map-put-all` | pass |",
            "| `map-replace` | pass |",
            "| `map-replace-key-value` | pass |",
            "| `map-is-empty` | pass |",
            "| `map-size` | pass |",
            "| `map-values` | pass |",
            "| `map-remove` | pass |",
            "| `map-remove-key-value` | pass |",
            "| `map-contains-value` | pass |",
            "| `map-concrete-key-set` | pass |",
            "| `map-concrete-entry-set` | pass |",
            "| `map-of-singleton` | pass |",
            "| `map-of-pair` | pass |",
            "| `map-of-triple` | pass |",
            "| `map-of-quadruple` | pass |",
            "| `map-of-quintuple` | pass |",
            "| `map-of-sextuple` | pass |",
            "| `map-of-septuple` | pass |",
            "| `map-of-octuple` | pass |",
            "| `map-of-nonuple` | pass |",
            "| `map-of-decuple` | pass |",
            "| `map-of-entries` | pass |",
            "| `set-copy-of` | pass |",
            "| `set-of-empty` | pass |",
            "| `set-of-singleton` | pass |",
            "| `set-of-pair` | pass |",
            "| `set-of-triple` | pass |",
            "| `set-of-quadruple` | pass |",
            "| `set-of-quintuple` | pass |",
            "| `set-of-sextuple` | pass |",
            "| `set-of-septuple` | pass |",
            "| `set-of-octuple` | pass |",
            "| `set-of-nonuple` | pass |",
            "| `set-of-decuple` | pass |",
            "| `set-of-varargs-array` | pass |",
            "| `list-of-varargs-gc` | pass |",
            "| `owned-buffer-realloc-validation` | pass |",
            "| `network-address-runtime` | pass |",
            "| `network-inetaddress-get-by-name-literal-host` | pass |",
            "| `network-inetaddress-get-all-by-name-literal-host` | pass |",
            "| `network-inetaddress-byte-address` | pass |",
            "| `network-inetaddress-named-byte-address` | pass |",
            "| `network-tcp-client-socket` | pass |",
            "| `network-tcp-client-socket-ipv6-loopback` | pass |",
            "| `network-tcp-client-socket-address` | pass |",
            "| `network-tcp-client-socket-local-bind` | pass |",
            "| `network-tcp-server-socket` | pass |",
            "| `network-tcp-server-socket-backlog` | pass |",
            "| `network-tcp-server-socket-bind-address` | pass |",
            "| `network-tcp-server-socket-local-address` | pass |",
            "| `network-tcp-server-socket-reuse-address` | pass |",
            "| `network-tcp-socket-local-address` | pass |",
            "| `network-tcp-socket-socket-address` | pass |",
            "| `network-tcp-socket-tcp-nodelay` | pass |",
            "| `network-tcp-socket-keepalive` | pass |",
            "| `network-tcp-socket-reuse-address` | pass |",
            "| `network-tcp-socket-receive-buffer` | pass |",
            "| `network-tcp-socket-send-buffer` | pass |",
            "| `network-tcp-socket-so-linger` | pass |",
            "| `network-tcp-socket-oob-inline` | pass |",
            "| `network-tcp-socket-traffic-class` | pass |",
            "| `network-tcp-socket-bound-state` | pass |",
            "| `network-tcp-socket-input-shutdown-state` | pass |",
            "| `network-tcp-socket-output-shutdown-state` | pass |",
            "| `network-tcp-socket-timeout-round-trip` | pass |",
            "| `network-tcp-socket-read-timeout-boundary` | pass |",
            "| `network-tcp-server-socket-bound-state` | pass |",
            "| `network-tcp-server-socket-local-socket-address` | pass |",
            "| `network-tcp-server-socket-receive-buffer` | pass |",
            "| `network-tcp-server-socket-timeout-round-trip` | pass |",
            "| `network-tcp-server-socket-accept-timeout-boundary` | pass |",
            "| `network-tcp-socket-stream-io` | pass |",
            "| `network-tcp-socket-channel-null` | pass |",
            "| `network-tcp-server-socket-channel-null` | pass |",
            "| `network-tcp-socket-explicit-connect-lifecycle` | pass |",
            "| `network-tcp-server-socket-explicit-bind-lifecycle` | pass |",
            "| `network-http-client-get-string` | pass |",
            "| `network-http-client-post-string-byte-array` | pass |",
            "| `network-http-client-put-byte-array` | pass |",
            "| `platform-thread-construction` | pass |",
            "| `platform-thread-empty-start-join` | pass |",
            "| `platform-thread-runnable-start-join-single-threaded` | pass |",
            "| `platform-thread-current-interrupt-state` | pass |",
            "| `platform-thread-current-thread-root-gc-pressure` | pass |",
            "| `platform-thread-runnable-target-root-gc-pressure` | pass |",
            "| `platform-thread-current-thread-inventory` | pass |",
            "| `platform-thread-live-root-registry` | pass |",
            "| `platform-thread-finished-thread-reclaim` | pass |",
            "| `platform-thread-yield` | pass |",
            "| `platform-thread-on-spin-wait` | pass |",
            "| `platform-thread-priority-default` | pass |",
            "| `platform-thread-priority-set-get` | pass |",
            "| `platform-thread-priority-inherited-construction` | pass |",
            "| `platform-thread-sleep-uninterrupted` | pass |",
            "| `platform-thread-sleep-millis-nanos-uninterrupted` | pass |",
            "| `platform-thread-sleep-entry-interrupted-same-method-catch` | pass |",
            "| `platform-thread-sleep-millis-nanos-entry-interrupted-same-method-catch` | pass |",
            "| `platform-thread-inheritable-threadlocal` | pass |",
            "| `virtual-thread-start-inheritable-threadlocal` | pass |",
            "| `virtual-thread-builder-start-inheritable-threadlocal` | pass |",
            "| `virtual-thread-factory-new-thread-inheritable-threadlocal` | pass |",
            "| `virtual-thread-executor-submit-inheritable-threadlocal` | pass |",
            "| `platform-thread-join-entry-interrupted-same-method-catch` | pass |",
            "| `platform-thread-join-timeout` | pass |",
            "| `platform-thread-join-millis-nanos-timeout` | pass |",
            "| `platform-thread-join-duration-timeout` | pass |",
            "| `scheduled-executor-fixed-delay` | pass |",
            "| `platform-thread-current-thread-start-build-reject` | pass |",
            "| `platform-thread-current-thread-join-build-reject` | pass |",
            "| `platform-thread-duplicate-start-build-reject` | pass |",
            "| `network-socket-rejection` | pass |",
            "| `network-http-rejection` | pass |",
            "| `network-runtime-feature-reporting` | pass |"
        );
        assertThat(json).contains(
            "\"generatedForJdk\": " + feature,
            "\"feature\": \"try-catch\"",
            "\"feature\": \"try-finally\"",
            "\"feature\": \"boxed-primitive-gc\"",
            "\"feature\": \"enum-value-of\"",
            "\"feature\": \"interface-dispatch\"",
            "\"feature\": \"polymorphic-virtual\"",
            "\"feature\": \"interface-polymorphic\"",
            "\"feature\": \"resource-system-loader-stream\"",
            "\"feature\": \"resource-loader-instance-stream\"",
            "\"feature\": \"string-intrinsics\"",
            "\"feature\": \"library-c-result-wrapper-success\"",
            "\"feature\": \"collections-empty-set\"",
            "\"feature\": \"collections-unmodifiable-collection\"",
            "\"feature\": \"collections-unmodifiable-set\"",
            "\"feature\": \"collections-unmodifiable-list\"",
            "\"feature\": \"collection-remove-all\"",
            "\"feature\": \"collection-retain-all\"",
            "\"feature\": \"collection-contains-all\"",
            "\"feature\": \"collection-remove\"",
            "\"feature\": \"collection-clear\"",
            "\"feature\": \"abstractlist-direct-owner-indexed-surface\"",
            "\"feature\": \"list-add-all-at\"",
            "\"feature\": \"list-remove-object\"",
            "\"feature\": \"list-index-of\"",
            "\"feature\": \"list-last-index-of\"",
            "\"feature\": \"list-remove-at\"",
            "\"feature\": \"list-remove-all\"",
            "\"feature\": \"list-retain-all\"",
            "\"feature\": \"list-contains-all\"",
            "\"feature\": \"set-remove-all\"",
            "\"feature\": \"set-retain-all\"",
            "\"feature\": \"set-contains-all\"",
            "\"feature\": \"set-remove\"",
            "\"feature\": \"set-clear\"",
            "\"feature\": \"hashset-capacity-constructor\"",
            "\"feature\": \"linkedhashset-capacity-constructor\"",
            "\"feature\": \"hashset-load-factor-constructor\"",
            "\"feature\": \"linkedhashset-load-factor-constructor\"",
            "\"feature\": \"hashset-collection-constructor\"",
            "\"feature\": \"linkedhashset-collection-constructor\"",
            "\"feature\": \"hashset-static-factory\"",
            "\"feature\": \"linkedhashset-static-factory\"",
            "\"feature\": \"arraylist-direct-owner-read-surface\"",
            "\"feature\": \"arraylist-direct-owner-get-first\"",
            "\"feature\": \"arraylist-direct-owner-get-last\"",
            "\"feature\": \"arraylist-direct-owner-add-all-at\"",
            "\"feature\": \"arraylist-direct-owner-remove-object\"",
            "\"feature\": \"arraylist-direct-owner-index-of\"",
            "\"feature\": \"arraylist-direct-owner-last-index-of\"",
            "\"feature\": \"arraylist-direct-owner-set\"",
            "\"feature\": \"arraylist-direct-owner-remove-at\"",
            "\"feature\": \"arraylist-direct-owner-remove-last\"",
            "\"feature\": \"arraylist-direct-owner-add-first\"",
            "\"feature\": \"arraylist-direct-owner-add-last\"",
            "\"feature\": \"arraylist-direct-owner-remove-first\"",
            "\"feature\": \"arraylist-direct-owner-remove-all\"",
            "\"feature\": \"arraylist-direct-owner-retain-all\"",
            "\"feature\": \"hashset-direct-owner-read-surface\"",
            "\"feature\": \"hashset-direct-owner-remove-all\"",
            "\"feature\": \"hashset-direct-owner-retain-all\"",
            "\"feature\": \"hashset-direct-owner-remove\"",
            "\"feature\": \"hashset-direct-owner-clear\"",
            "\"feature\": \"linkedhashset-direct-owner-read-surface\"",
            "\"feature\": \"linkedhashset-direct-owner-remove-all\"",
            "\"feature\": \"linkedhashset-direct-owner-retain-all\"",
            "\"feature\": \"linkedhashset-direct-owner-remove\"",
            "\"feature\": \"linkedhashset-direct-owner-clear\"",
            "\"feature\": \"hashmap-map-constructor\"",
            "\"feature\": \"linkedhashmap-map-constructor\"",
            "\"feature\": \"hashmap-static-factory\"",
            "\"feature\": \"linkedhashmap-static-factory\"",
            "\"feature\": \"concurrenthashmap-map-constructor\"",
            "\"feature\": \"collection-to-array\"",
            "\"feature\": \"collection-remove-if\"",
            "\"feature\": \"list-to-array\"",
            "\"feature\": \"set-to-array\"",
            "\"feature\": \"collections-singleton-set\"",
            "\"feature\": \"collections-singleton-list\"",
            "\"feature\": \"collections-empty-list\"",
            "\"feature\": \"collections-singleton-map\"",
            "\"feature\": \"collections-unmodifiable-map\"",
            "\"feature\": \"map-entry\"",
            "\"feature\": \"bifunction-apply\"",
            "\"feature\": \"map-compute\"",
            "\"feature\": \"map-compute-if-present\"",
            "\"feature\": \"map-merge\"",
            "\"feature\": \"predicate-test\"",
            "\"feature\": \"map-replace\"",
            "\"feature\": \"map-replace-key-value\"",
            "\"feature\": \"map-remove\"",
            "\"feature\": \"map-remove-key-value\"",
            "\"feature\": \"map-concrete-key-set\"",
            "\"feature\": \"map-concrete-entry-set\"",
            "\"feature\": \"map-of-singleton\"",
            "\"feature\": \"map-of-pair\"",
            "\"feature\": \"map-of-triple\"",
            "\"feature\": \"map-of-quadruple\"",
            "\"feature\": \"map-of-quintuple\"",
            "\"feature\": \"map-of-sextuple\"",
            "\"feature\": \"map-of-septuple\"",
            "\"feature\": \"map-of-octuple\"",
            "\"feature\": \"map-of-nonuple\"",
            "\"feature\": \"map-of-decuple\"",
            "\"feature\": \"set-copy-of\"",
            "\"feature\": \"set-of-empty\"",
            "\"feature\": \"set-of-singleton\"",
            "\"feature\": \"set-of-pair\"",
            "\"feature\": \"set-of-triple\"",
            "\"feature\": \"set-of-quadruple\"",
            "\"feature\": \"set-of-quintuple\"",
            "\"feature\": \"set-of-sextuple\"",
            "\"feature\": \"set-of-septuple\"",
            "\"feature\": \"set-of-octuple\"",
            "\"feature\": \"set-of-nonuple\"",
            "\"feature\": \"set-of-decuple\"",
            "\"feature\": \"set-of-varargs-array\"",
            "\"feature\": \"network-address-runtime\"",
            "\"feature\": \"network-inetaddress-get-by-name-literal-host\"",
            "\"feature\": \"network-inetaddress-get-all-by-name-literal-host\"",
            "\"feature\": \"network-inetaddress-byte-address\"",
            "\"feature\": \"network-inetaddress-named-byte-address\"",
            "\"feature\": \"network-tcp-client-socket\"",
            "\"feature\": \"network-tcp-client-socket-ipv6-loopback\"",
            "\"feature\": \"network-tcp-client-socket-address\"",
            "\"feature\": \"network-tcp-client-socket-local-bind\"",
            "\"feature\": \"network-tcp-server-socket\"",
            "\"feature\": \"network-tcp-server-socket-backlog\"",
            "\"feature\": \"network-tcp-server-socket-bind-address\"",
            "\"feature\": \"network-tcp-server-socket-local-address\"",
            "\"feature\": \"network-tcp-server-socket-reuse-address\"",
            "\"feature\": \"network-tcp-socket-local-address\"",
            "\"feature\": \"network-tcp-socket-socket-address\"",
            "\"feature\": \"network-tcp-socket-tcp-nodelay\"",
            "\"feature\": \"network-tcp-socket-keepalive\"",
            "\"feature\": \"network-tcp-socket-reuse-address\"",
            "\"feature\": \"network-tcp-socket-receive-buffer\"",
            "\"feature\": \"network-tcp-socket-send-buffer\"",
            "\"feature\": \"network-tcp-socket-so-linger\"",
            "\"feature\": \"network-tcp-socket-oob-inline\"",
            "\"feature\": \"network-tcp-socket-traffic-class\"",
            "\"feature\": \"network-tcp-socket-timeout-round-trip\"",
            "\"feature\": \"network-tcp-socket-read-timeout-boundary\"",
            "\"feature\": \"network-tcp-server-socket-local-socket-address\"",
            "\"feature\": \"network-tcp-server-socket-receive-buffer\"",
            "\"feature\": \"network-tcp-server-socket-timeout-round-trip\"",
            "\"feature\": \"network-tcp-server-socket-accept-timeout-boundary\"",
            "\"feature\": \"network-tcp-socket-stream-io\"",
            "\"feature\": \"network-tcp-socket-channel-null\"",
            "\"feature\": \"network-tcp-server-socket-channel-null\"",
            "\"feature\": \"network-tcp-socket-explicit-connect-lifecycle\"",
            "\"feature\": \"network-tcp-server-socket-explicit-bind-lifecycle\"",
            "\"feature\": \"network-http-client-get-string\"",
            "\"feature\": \"network-http-client-post-string-byte-array\"",
            "\"feature\": \"network-http-client-put-byte-array\"",
            "\"feature\": \"platform-thread-construction\"",
            "\"feature\": \"platform-thread-empty-start-join\"",
            "\"feature\": \"platform-thread-runnable-start-join-single-threaded\"",
            "\"feature\": \"platform-thread-current-interrupt-state\"",
            "\"feature\": \"platform-thread-current-thread-root-gc-pressure\"",
            "\"feature\": \"platform-thread-runnable-target-root-gc-pressure\"",
            "\"feature\": \"platform-thread-current-thread-inventory\"",
            "\"feature\": \"platform-thread-live-root-registry\"",
            "\"feature\": \"platform-thread-finished-thread-reclaim\"",
            "\"feature\": \"platform-thread-yield\"",
            "\"feature\": \"platform-thread-on-spin-wait\"",
            "\"feature\": \"platform-thread-priority-default\"",
            "\"feature\": \"platform-thread-priority-set-get\"",
            "\"feature\": \"platform-thread-priority-inherited-construction\"",
            "\"feature\": \"platform-thread-sleep-uninterrupted\"",
            "\"feature\": \"platform-thread-sleep-millis-nanos-uninterrupted\"",
            "\"feature\": \"platform-thread-sleep-entry-interrupted-same-method-catch\"",
            "\"feature\": \"platform-thread-sleep-millis-nanos-entry-interrupted-same-method-catch\"",
            "\"feature\": \"platform-thread-inheritable-threadlocal\"",
            "\"feature\": \"virtual-thread-start-inheritable-threadlocal\"",
            "\"feature\": \"virtual-thread-builder-start-inheritable-threadlocal\"",
            "\"feature\": \"virtual-thread-factory-new-thread-inheritable-threadlocal\"",
            "\"feature\": \"virtual-thread-executor-submit-inheritable-threadlocal\"",
            "\"feature\": \"platform-thread-join-entry-interrupted-same-method-catch\"",
            "\"feature\": \"platform-thread-join-timeout\"",
            "\"feature\": \"platform-thread-join-millis-nanos-timeout\"",
            "\"feature\": \"platform-thread-join-duration-timeout\"",
            "\"feature\": \"scheduled-executor-fixed-delay\"",
            "\"feature\": \"platform-thread-current-thread-start-build-reject\"",
            "\"feature\": \"platform-thread-current-thread-join-build-reject\"",
            "\"feature\": \"platform-thread-duplicate-start-build-reject\"",
            "\"feature\": \"network-socket-rejection\"",
            "\"feature\": \"network-http-rejection\"",
            "\"feature\": \"network-runtime-feature-reporting\""
        );
    }

    @Test
    void writeJdkCompatibilityLedgerStaysIndependentOfExternalProbeNames() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(metadata("java.base", "java/lang/Object")),
            List.of()
        );

        final String compatibility = Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"));

        assertThat(compatibility).contains(
            "This ledger excludes external example or library probes.",
            "and never define a supported JDK member count."
        );
    }

    @Test
    void writeUsesLegacyJavaVersionPrefixForFeatureDetection() throws Exception {
        withJavaVersion("1.8.0_442", () -> {
            new CompatibilityReports().write(
                tempDir,
                tempDir.resolve(".javan"),
                List.of(metadata("", "com/acme/Main")),
                List.of(metadata("java.base", "java/lang/Object")),
                List.of()
            );

            assertThat(tempDir.resolve(".javan/reports/jdk-8-inventory.json")).isRegularFile();
            assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json")))
                .contains("\"javaFeatureVersion\": 8");
        });
    }

    @Test
    void writeUsesZeroFeatureForBlankJavaVersion() throws Exception {
        withJavaVersion(" ", () -> {
            new CompatibilityReports().write(
                tempDir,
                tempDir.resolve(".javan"),
                List.of(metadata("", "com/acme/Main")),
                List.of(metadata("java.base", "java/lang/Object")),
                List.of()
            );

            assertThat(tempDir.resolve(".javan/reports/jdk-0-inventory.json")).isRegularFile();
            assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json")))
                .contains("\"javaFeatureVersion\": 0");
        });
    }

    @Test
    void writeStopsFeatureParsingAtSuffixAndCountsPreviewSyntheticAndErrors() throws Exception {
        withJavaVersion("25-ea", () -> {
            final List<ClassMetadata> projectClasses = List.of(
                metadata(
                    "",
                    "com/acme/Preview",
                    65_535,
                    List.of(
                        member(
                            0x1000,
                            "<init>",
                            "()V",
                            List.of("Synthetic"),
                            List.of(new InstructionMetadata(0, 197, "multianewarray", 3, BytecodeSupport.Status.RECOGNIZED_REJECTED))
                        )
                    ),
                    List.of(
                        member(
                            0x1000,
                            "bridge",
                            "()V",
                            List.of(),
                            List.of(new InstructionMetadata(1, 255, "opcode_255", 0, BytecodeSupport.Status.UNKNOWN_FATAL))
                        )
                    )
                )
            );
            final List<ClassMetadata> jdkClasses = List.of(
                metadata("java.logging", "java/util/logging/Logger"),
                metadata("java.base", "java/lang/Object"),
                metadata("java.base", "java/lang/String")
            );

            new CompatibilityReports().write(
                tempDir,
                tempDir.resolve(".javan"),
                projectClasses,
                jdkClasses,
                List.of(
                    Diagnostic.error("JAVAN999", "fatal", "com/acme/Preview", "bridge()V", "opcode_255", "reason", "fix"),
                    Diagnostic.warning("JAVAN199", "warning", "com/acme/Preview", "<init>()V", "multianewarray", "reason", "fix")
                )
            );

            assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"))).contains(
                "\"javaFeatureVersion\": 25",
                "\"diagnosticErrors\": 1",
                "\"recognizedRejectedOpcodeUses\": 1",
                "\"unknownFatalOpcodeUses\": 1",
                "\"status\": \"fail\""
            );
            assertThat(Files.readString(tempDir.resolve(".javan/reports/bytecode-patterns-jdk-25.json"))).contains(
                "\"previewClasses\": [\"com/acme/Preview\"]",
                "\"syntheticMethods\": [\"com/acme/Preview.<init>()V\", \"com/acme/Preview.bridge()V\"]"
            );
            assertThat(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"))).contains(
                "- JDK modules: `2`"
            );
        });
    }

    @Test
    void writeJdkCompatibilityMarkdownShowsExplicitRejectedAndUnknownCallableCounts() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(
                metadata(
                    "java.base",
                    "java/lang/Object",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of(
                        member(0, "getClass", "()Ljava/lang/Class;", List.of(), List.of()),
                        member(0, "wait", "(J)V", List.of(), List.of())
                    )
                ),
                metadata(
                    "java.base",
                    "java/lang/Class",
                    0,
                    List.of(),
                    List.of(member(0, "forName", "(Ljava/lang/String;)Ljava/lang/Class;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/util/concurrent/Executors",
                    0,
                    List.of(),
                    List.of(member(0, "newCachedThreadPool", "()Ljava/util/concurrent/ExecutorService;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/lang/InheritableThreadLocal",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of()
                ),
                metadata(
                    "java.base",
                    "java/lang/String",
                    0,
                    List.of(),
                    List.of(member(0, "valueOf", "(I)Ljava/lang/String;", List.of(), List.of()))
                )
            ),
            List.of()
        );

        assertThat(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"))).contains(
            "| exact supported JDK callables | 4 / 7 (57.1%) |",
            "| exact explicit rejected JDK callables | 3 |",
            "| exact done JDK callables | 7 / 7 (100.0%) |",
            "| exact unknown JDK callables | 0 |",
            "| exact supported JDK callables left | 3 |"
        );
    }

    @Test
    void writeSummaryReportsFlowQualifiedRejectedJdkShapesSeparatelyFromExactMemberAccounting() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(metadata("java.base", "java/lang/Object")),
            List.of(
                Diagnostic.error("JAVAN075", "lifecycle", "com/acme/Main", "run()V", "Thread.currentThread().start()", "reason", "fix"),
                Diagnostic.warning("JAVAN175", "lifecycle", "com/acme/Main", "run()V", "duplicate Thread.start() on local 1", "reason", "fix"),
                Diagnostic.error("JAVAN077", "concurrency", "com/acme/Main", "run()V", "Thread.Builder.start(Runnable)", "reason", "fix"),
                Diagnostic.warning("JAVAN177", "concurrency", "com/acme/Main", "run()V", "Thread.Builder.factory()", "reason", "fix"),
                Diagnostic.error("JAVAN077", "concurrency", "com/acme/Main", "run()V", "Thread.ofVirtual()", "reason", "fix"),
                Diagnostic.warning("JAVAN177", "concurrency", "com/acme/Main", "run()V", "Thread.Builder.OfVirtual.factory()", "reason", "fix"),
                Diagnostic.error("JAVAN077", "concurrency", "com/acme/Main", "run()V", "Executor.execute(Runnable)", "reason", "fix"),
                Diagnostic.warning("JAVAN177", "concurrency", "com/acme/Main", "run()V", "ExecutorService.close()", "reason", "fix"),
                Diagnostic.error("JAVAN076", "sync", "com/acme/Main", "run()V", "Object.wait()", "reason", "fix")
            )
        );

        assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"))).contains(
            "\"flowQualifiedRejectedJdkCalls\": {\"reachableCurrentThreadLifecycle\": 1, \"unreachableCurrentThreadLifecycle\": 1, \"reachableThreadBuilderReceiverShape\": 1, \"unreachableThreadBuilderReceiverShape\": 1, \"reachableVirtualThreadFactoryShape\": 1, \"unreachableVirtualThreadFactoryShape\": 1, \"reachableExecutorReceiverShape\": 1, \"unreachableExecutorReceiverShape\": 1, \"total\": 8}",
            "\"jdkCoverageAccounting\": {\"implemented\": true, \"complete\": false, \"scope\": \"exact-member-baseline-plus-flow-qualified-diagnostics\""
        );
        assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.md"))).contains(
            "- flow-qualified reachable current-thread lifecycle rejects: `1`",
            "- flow-qualified unreachable current-thread lifecycle rejects: `1`",
            "- flow-qualified reachable thread-builder receiver-shape rejects: `1`",
            "- flow-qualified unreachable thread-builder receiver-shape rejects: `1`",
            "- flow-qualified reachable virtual-thread factory-shape rejects: `1`",
            "- flow-qualified unreachable virtual-thread factory-shape rejects: `1`",
            "- flow-qualified reachable executor receiver-shape rejects: `1`",
            "- flow-qualified unreachable executor receiver-shape rejects: `1`",
            "- flow-qualified rejected JDK call shapes total: `8`"
        );
        assertThat(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md"))).contains(
            "| flow-qualified reachable current-thread lifecycle rejects | 1 |",
            "| flow-qualified unreachable current-thread lifecycle rejects | 1 |",
            "| flow-qualified reachable thread-builder receiver-shape rejects | 1 |",
            "| flow-qualified unreachable thread-builder receiver-shape rejects | 1 |",
            "| flow-qualified reachable virtual-thread factory-shape rejects | 1 |",
            "| flow-qualified unreachable virtual-thread factory-shape rejects | 1 |",
            "| flow-qualified reachable executor receiver-shape rejects | 1 |",
            "| flow-qualified unreachable executor receiver-shape rejects | 1 |",
            "| flow-qualified rejected JDK call shapes total | 8 |"
        );
    }

    @Test
    void writeSummaryCountsDeliberateOwnerFamilyRejectionsInExactCallableAccounting() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(metadata("", "com/acme/Main")),
            List.of(
                metadata(
                    "java.base",
                    "java/lang/Object",
                    0,
                    List.of(member(0, "<init>", "()V", List.of(), List.of())),
                    List.of()
                ),
                metadata(
                    "jdk.jfr",
                    "jdk/jfr/FlightRecorder",
                    0,
                    List.of(),
                    List.of(member(0, "isAvailable", "()Z", List.of(), List.of()))
                ),
                metadata(
                    "jdk.unsupported",
                    "sun/misc/Unsafe",
                    0,
                    List.of(),
                    List.of(member(0, "getUnsafe", "()Lsun/misc/Unsafe;", List.of(), List.of()))
                ),
                metadata(
                    "java.base",
                    "java/lang/String",
                    0,
                    List.of(),
                    List.of(member(0, "valueOf", "(I)Ljava/lang/String;", List.of(), List.of()))
                )
            ),
            List.of()
        );

        assertThat(Files.readString(tempDir.resolve(".javan/reports/compatibility-summary.json"))).contains(
            "\"exactSupportedJdkCallables\": {\"classes\": 2, \"constructors\": 1, \"methods\": 1, \"callables\": 2, \"totalCallables\": 4, \"leftCallables\": 2, \"coveragePercent\": \"50.0\"}",
            "\"exactJdkCallableAccounting\": {\"supportedCallables\": 2, \"explicitRejectedCallables\": 2, \"doneCallables\": 4, \"unknownCallables\": 0, \"totalCallables\": 4, \"donePercent\": \"100.0\"}"
        );
    }

    private static ClassMetadata metadata(final String moduleName, final String className) {
        return metadata(moduleName, className, 0, List.of(), List.of());
    }

    private static ClassMetadata metadata(
        final String moduleName,
        final String className,
        final int minorVersion,
        final List<MemberMetadata> constructors,
        final List<MemberMetadata> methods
    ) {
        return new ClassMetadata(
            Path.of(className + ".class"),
            true,
            moduleName,
            minorVersion,
            69,
            0,
            className,
            "java/lang/Object",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            constructors,
            methods
        );
    }

    private static String twoDigits(final int value) {
        if (value < 10) {
            return "0" + value;
        }
        return Integer.toString(value);
    }

    private static MemberMetadata member(
        final int accessFlags,
        final String name,
        final String descriptor,
        final List<String> attributes,
        final List<InstructionMetadata> instructions
    ) {
        return new MemberMetadata(accessFlags, name, descriptor, attributes, instructions);
    }

    private static void withJavaVersion(final String value, final ThrowingRunnable runnable) throws Exception {
        final String previous = System.getProperty("java.version");
        try {
            System.setProperty("java.version", value);
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty("java.version");
            } else {
                System.setProperty("java.version", previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

package javan.codegen;

import javan.TestProcesses;
import javan.build.NativeLinkInputs;
import javan.build.ResourceBundler;
import javan.compat.JdkCallSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
@ResourceLock("native-cli-heavy")
final class RuntimeFilesTest {
    @TempDir
    private Path tempDir;

    @Test
    void runtimeSetEqualsDeclarationAndDefinitionAreUnique() throws Exception {
        final String source = Files.readString(new RuntimeFiles().write(tempDir));
        final String header = Files.readString(tempDir.resolve("javan_runtime.h"));

        assertThat(List.of(
            header.split("int32_t javan_set_equals\\(void\\* receiver, void\\* other\\);", -1).length - 1,
            source.split("int32_t javan_set_equals\\(void\\* receiver, void\\* other\\) \\{", -1).length - 1
        )).containsExactly(1, 1);
    }

    @Test
    void runtimeHeaderDeclaresDoubleToFloatHelper() throws Exception {
        new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(tempDir.resolve("javan_runtime.h"))).contains("float javan_d2f(double value);");
    }

    @Test
    void runtimeDoubleToFloatConvertsExactFiniteValue() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3ff8000000000000)")).isEqualTo("3fc00000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsOrdinaryFiniteValue() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3ff0000018000000)")).isEqualTo("3f800001\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsPositiveTieToEven() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3ff0000010000000)")).isEqualTo("3f800000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsNegativeTieToEven() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xbff0000010000000)")).isEqualTo("bf800000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsOddLowerTieToEven() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3ff0000030000000)")).isEqualTo("3f800002\n");
    }

    @Test
    void runtimeDoubleToFloatPreservesPositiveZero() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x0000000000000000)")).isEqualTo("00000000\n");
    }

    @Test
    void runtimeDoubleToFloatPreservesNegativeZero() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x8000000000000000)")).isEqualTo("80000000\n");
    }

    @Test
    void runtimeDoubleToFloatPreservesPositiveInfinity() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x7ff0000000000000)")).isEqualTo("7f800000\n");
    }

    @Test
    void runtimeDoubleToFloatPreservesNegativeInfinity() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xfff0000000000000)")).isEqualTo("ff800000\n");
    }

    @Test
    void runtimeDoubleToFloatCanonicalizesPositiveQuietNan() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x7ff8000000000001)")).isEqualTo("7fc00000\n");
    }

    @Test
    void runtimeDoubleToFloatCanonicalizesNegativeQuietNan() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xfff8000000000001)")).isEqualTo("7fc00000\n");
    }

    @Test
    void runtimeDoubleToFloatCanonicalizesPositiveSignalingNan() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x7ff0000000000001)")).isEqualTo("7fc00000\n");
    }

    @Test
    void runtimeDoubleToFloatCanonicalizesNegativeSignalingNan() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xfff0000000000001)")).isEqualTo("7fc00000\n");
    }

    @Test
    void runtimeDoubleToFloatGraduallyUnderflows() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3800000000000000)")).isEqualTo("00400000\n");
    }

    @Test
    void runtimeDoubleToFloatPreservesMinimumBinary32Subnormal() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x36a0000000000000)")).isEqualTo("00000001\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsPositiveHalfMinimumBinary32SubnormalToZero() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3690000000000000)")).isEqualTo("00000000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsNegativeHalfMinimumBinary32SubnormalToZero() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xb690000000000000)")).isEqualTo("80000000\n");
    }

    @Test
    void runtimeDoubleToFloatCarriesMinimumNormalTie() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x380fffffe0000000)")).isEqualTo("00800000\n");
    }

    @Test
    void runtimeDoubleToFloatPreservesMaximumFiniteFloat() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x47efffffe0000000)")).isEqualTo("7f7fffff\n");
    }

    @Test
    void runtimeDoubleToFloatKeepsValueBelowOverflowThresholdFinite() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x47efffffefffffff)")).isEqualTo("7f7fffff\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsOverflowTieToInfinity() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x47effffff0000000)")).isEqualTo("7f800000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsPositiveOverflowToInfinity() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x7fefffffffffffff)")).isEqualTo("7f800000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsNegativeOverflowToInfinity() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xffefffffffffffff)")).isEqualTo("ff800000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsMinimumPositiveBinary64SubnormalToZero() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x0000000000000001)")).isEqualTo("00000000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsMinimumNegativeBinary64SubnormalToNegativeZero() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x8000000000000001)")).isEqualTo("80000000\n");
    }

    @Test
    void runtimeDoubleToFloatCarriesFiniteNormalMantissa() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0x3ffffffff0000000)")).isEqualTo("40000000\n");
    }

    @Test
    void runtimeDoubleToFloatRoundsNegativeOverflowMidpointToInfinity() throws Exception {
        assertThat(doubleToFloatBits("UINT64_C(0xc7effffff0000000)")).isEqualTo("ff800000\n");
    }

    @Test
    void recordStringValidationBoundsBorrowedLookupMissesWithManyLiveAllocations() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            enum {
                ALLOCATION_COUNT = 32768,
                LOOKUP_COUNT = 131072
            };

            int main(void) {
                const char borrowed[] = "borrowed record component";
                javan_register_static_roots(0, 0);
                for (int index = 0; index < ALLOCATION_COUNT; index++) {
                    (void) javan_alloc(1);
                }
                for (int index = 0; index < LOOKUP_COUNT; index++) {
                    javan_record_shape_validate((void*) "literal record component", "s");
                    javan_record_shape_validate((void*) borrowed, "s");
                }
                javan_validate_heap_metadata();
                puts("ok");
                return 0;
            }
            """,
            "65536",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(6)
        );

        assertThat(stdout).isEqualTo("ok\n");
    }

    @Test
    void recordStringValidationRejectsTrackedNonString() throws Exception {
        final String panic = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            "javan_record_shape_validate(javan_alloc(1), \"s\");"
        );

        assertThat(panic).isEqualTo("record generic value does not match declared shape\n");
    }

    @Test
    void allocationIndexSurvivesGrowthDeletionReallocationAndCollection() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            enum { VALUE_COUNT = 640 };

            int main(void) {
                void* values[VALUE_COUNT];
                javan_register_static_roots(0, 0);
                javan_pending_throw("java/lang/Throwable", NULL, "", "", "", -1, -1, "");
                javan_pending_clear();
                javan_gc_collect();
                const unsigned long baseline = javan_heap_live_allocations();
                for (int index = 0; index < VALUE_COUNT; index++) {
                    values[index] = javan_alloc(1);
                }
                for (int index = 0; index < VALUE_COUNT; index += 2) {
                    javan_free(values[index]);
                    values[index] = NULL;
                }
                void* list = javan_arraylist_new();
                for (int index = 1; index < VALUE_COUNT; index += 2) {
                    javan_arraylist_add(list, values[index]);
                }
                javan_free(list);
                for (int index = 1; index < VALUE_COUNT; index += 2) {
                    javan_free(values[index]);
                }
                javan_pending_throw("java/lang/Throwable", NULL, "", "", "", -1, -1, "");
                (void) javan_pending_catch();
                javan_pending_clear();
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("%d\\n", javan_heap_live_allocations() == baseline);
                return 0;
            }
            """,
            "131072"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void rootedAllocationValuesTableReservationFailureNeverPublishesFreedPointer() throws Exception {
        assertThat(rootedAllocationRegistryReservationFailure(1)).isEqualTo("0\n1\n");
    }

    @Test
    void rootedAllocationNodesTableReservationFailureNeverPublishesFreedPointer() throws Exception {
        assertThat(rootedAllocationRegistryReservationFailure(2)).isEqualTo("0\n1\n");
    }

    @Test
    void rootedAllocationNodeMetadataFailureNeverPublishesFreedPointer() throws Exception {
        assertThat(rootedAllocationNodeMetadataFailure()).isEqualTo("0\n1\n");
    }

    @Test
    void allocationRegistryCapacityOverflowRejectsWithoutMutation() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_allocation_registry_capacity_overflow(void* tracked) {
                javan_runtime_lock_enter();
                void** values = javan_allocation_index.values;
                javan_allocation_node** nodes = javan_allocation_index.nodes;
                int length = javan_allocation_index.length;
                int capacity = javan_allocation_index.capacity;
                int occupied = javan_registry_slot(values, capacity, tracked);
                void* indexed_value = values[occupied];
                javan_allocation_node* indexed_node = nodes[occupied];
                int accepted = javan_allocation_registry_ensure_capacity(INT_MAX / 2 + 1);
                int unchanged = javan_allocation_index.values == values
                    && javan_allocation_index.nodes == nodes
                    && javan_allocation_index.length == length
                    && javan_allocation_index.capacity == capacity
                    && values[occupied] == indexed_value
                    && nodes[occupied] == indexed_node;
                javan_runtime_lock_leave();
                return accepted == 0 && unchanged != 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            extern int javan_test_allocation_registry_capacity_overflow(void* tracked);

            int main(void) {
                javan_register_static_roots(0, 0);
                void* value = javan_alloc(1);
                int passed = javan_test_allocation_registry_capacity_overflow(value);
                javan_validate_heap_metadata();
                javan_free(value);
                printf("%d\\n", passed);
                return 0;
            }
            """,
            "4096",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void heapMetadataRejectsNonPowerOfTwoAllocationIndexCapacity() throws Exception {
        assertThat(heapMetadataFailure(
            """
            static void* javan_test_non_power_of_two_values[3];
            static javan_allocation_node* javan_test_non_power_of_two_nodes[3];

            void javan_test_corrupt_allocation_index_capacity(void) {
                javan_allocation_index.values = javan_test_non_power_of_two_values;
                javan_allocation_index.nodes = javan_test_non_power_of_two_nodes;
                javan_allocation_index.length = 0;
                javan_allocation_index.capacity = 3;
            }
            """,
            """
            extern void javan_test_corrupt_allocation_index_capacity(void);
            int main(void) {
                javan_register_static_roots(0, 0);
                javan_test_corrupt_allocation_index_capacity();
                return javan_heap_metadata_failure_result();
            }
            """
        )).isEqualTo("0\ninvalid heap allocation index\n");
    }

    @Test
    void heapMetadataRejectsAllocationIndexAtFiftyPercentLoad() throws Exception {
        assertThat(heapMetadataFailure(
            """
            void javan_test_corrupt_allocation_index_load(void* value) {
                (void) value;
                javan_allocation_index.length = javan_allocation_index.capacity / 2;
            }
            """,
            """
            extern void javan_test_corrupt_allocation_index_load(void* value);
            int main(void) {
                javan_register_static_roots(0, 0);
                void* value = javan_alloc(1);
                javan_test_corrupt_allocation_index_load(value);
                return javan_heap_metadata_failure_result();
            }
            """
        )).isEqualTo("0\ninvalid heap allocation index\n");
    }

    @Test
    void forcedMovedReallocationKeepsAllocationMetadataValid() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(
                new RuntimeReplacement(
                    "static void* javan_realloc_tracked(",
                    """
                    static void* javan_test_force_moved_realloc(void* value, unsigned long old_size, unsigned long size);

                    static void* javan_realloc_tracked(
                    """.stripTrailing()
                ),
                new RuntimeReplacement(
                    "realloc(value, actual_size)",
                    "javan_test_force_moved_realloc(value, node->size, actual_size)",
                    2
                )
            ),
            """
            static int javan_test_force_moved_realloc_count = 0;

            static void* javan_test_force_moved_realloc(void* value, unsigned long old_size, unsigned long size) {
                void* next = malloc(size);
                if (next == NULL) {
                    return NULL;
                }
                memcpy(next, value, old_size < size ? old_size : size);
                free(value);
                javan_test_force_moved_realloc_count++;
                return next;
            }

            int javan_test_forced_moved_reallocation(void) {
                void* value = javan_alloc(8);
                uintptr_t original_address = (uintptr_t) value;
                memset(value, 0x5a, 8);
                void* next = javan_realloc(value, 64);
                int moved = (uintptr_t) next != original_address;
                javan_allocation_metadata snapshot;
                int old_removed = javan_find_allocation((void*) original_address, &snapshot) == 0;
                int indexed = javan_find_allocation(next, &snapshot) != 0
                    && snapshot.value == next && snapshot.base == next && snapshot.size == 64;
                javan_validate_heap_metadata();
                javan_free(next);
                javan_validate_heap_metadata();
                return moved != 0
                    && old_removed != 0
                    && indexed != 0
                    && javan_test_force_moved_realloc_count == 1
                    && javan_heap_live_allocations() == 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            extern int javan_test_forced_moved_reallocation(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_forced_moved_reallocation());
                return 0;
            }
            """,
            "4096",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void concurrentExplicitFreeCannotInvalidateBuiltinInstanceOfMetadataSnapshot() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(
                new RuntimeReplacement(
                    "static int javan_find_allocation(void* value, javan_allocation_metadata* snapshot) {",
                    """
                    static void javan_test_after_allocation_snapshot(javan_allocation_metadata* snapshot);
                    static void javan_test_dispose_allocation_node(javan_allocation_node* node);

                    static int javan_find_allocation(void* value, javan_allocation_metadata* snapshot) {
                    """.stripTrailing()
                ),
                new RuntimeReplacement(
                    """
                    javan_runtime_lock_leave();
                    return found;
                    """.stripTrailing().indent(4).stripTrailing(),
                    """
                    javan_runtime_lock_leave();
                    if (found != 0) {
                        javan_test_after_allocation_snapshot(snapshot);
                    }
                    return found;
                    """.stripTrailing().indent(4).stripTrailing()
                ),
                new RuntimeReplacement(
                    """
                    javan_allocation_registry_remove(value);
                    free(node);
                    free(base);
                    javan_heap_maybe_validate();
                    """.stripTrailing().indent(4).stripTrailing(),
                    """
                    javan_allocation_registry_remove(value);
                    javan_test_dispose_allocation_node(node);
                    free(base);
                    javan_heap_maybe_validate();
                    """.stripTrailing().indent(4).stripTrailing()
                )
            ),
            """
            static void javan_test_dispose_allocation_node(javan_allocation_node* node);

            #include <stdatomic.h>

            static void* javan_test_snapshot_target = NULL;
            static javan_allocation_node* javan_test_deferred_node = NULL;
            static atomic_int javan_test_snapshot_ready = ATOMIC_VAR_INIT(0);
            static atomic_int javan_test_node_disposed = ATOMIC_VAR_INIT(0);

            static void javan_test_thread_yield(void) {
                #if defined(_WIN32)
                (void) SwitchToThread();
                #else
                (void) sched_yield();
                #endif
            }

            static void javan_test_after_allocation_snapshot(javan_allocation_metadata* snapshot) {
                if (snapshot->value != javan_test_snapshot_target) {
                    return;
                }
                atomic_store_explicit(&javan_test_snapshot_ready, 1, memory_order_release);
                while (atomic_load_explicit(&javan_test_node_disposed, memory_order_acquire) == 0) {
                    javan_test_thread_yield();
                }
            }

            static void javan_test_dispose_allocation_node(javan_allocation_node* node) {
                memset(node, 0xa5, sizeof(javan_allocation_node));
                javan_test_deferred_node = node;
                atomic_store_explicit(&javan_test_node_disposed, 1, memory_order_release);
            }

            void javan_test_set_snapshot_target(void* value) {
                javan_test_snapshot_target = value;
            }

            int javan_test_snapshot_ready_value(void) {
                return atomic_load_explicit(&javan_test_snapshot_ready, memory_order_acquire);
            }

            int javan_test_node_was_deferred(void) {
                return javan_test_deferred_node != NULL
                    && atomic_load_explicit(&javan_test_node_disposed, memory_order_acquire) != 0;
            }

            void javan_test_release_deferred_node(void) {
                free(javan_test_deferred_node);
                javan_test_deferred_node = NULL;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdatomic.h>
            #include <stdio.h>

            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <sched.h>
            #endif

            static void* value;
            static atomic_int instance_result = ATOMIC_VAR_INIT(0);

            extern void javan_test_set_snapshot_target(void* value);
            extern int javan_test_snapshot_ready_value(void);
            extern int javan_test_node_was_deferred(void);
            extern void javan_test_release_deferred_node(void);

            static void javan_test_yield(void) {
                #if defined(_WIN32)
                (void) SwitchToThread();
                #else
                (void) sched_yield();
                #endif
            }

            static void javan_test_lookup(void) {
                atomic_store_explicit(
                    &instance_result,
                    javan_object_builtin_instance_of(value, 1),
                    memory_order_release
                );
            }

            static void javan_test_free(void) {
                while (javan_test_snapshot_ready_value() == 0) {
                    javan_test_yield();
                }
                javan_free(value);
            }

            #if defined(_WIN32)
            static DWORD WINAPI javan_test_lookup_entry(LPVOID argument) {
                (void) argument;
                javan_test_lookup();
                return 0;
            }

            static DWORD WINAPI javan_test_free_entry(LPVOID argument) {
                (void) argument;
                javan_test_free();
                return 0;
            }
            #else
            static void* javan_test_lookup_entry(void* argument) {
                (void) argument;
                javan_test_lookup();
                return NULL;
            }

            static void* javan_test_free_entry(void* argument) {
                (void) argument;
                javan_test_free();
                return NULL;
            }
            #endif

            int main(void) {
                javan_register_static_roots(0, 0);
                value = javan_arraylist_new();
                javan_test_set_snapshot_target(value);
                #if defined(_WIN32)
                HANDLE lookup = CreateThread(NULL, 0, javan_test_lookup_entry, NULL, 0, NULL);
                HANDLE freer = CreateThread(NULL, 0, javan_test_free_entry, NULL, 0, NULL);
                if (lookup == NULL || freer == NULL) {
                    if (lookup != NULL) CloseHandle(lookup);
                    if (freer != NULL) CloseHandle(freer);
                    return 2;
                }
                DWORD lookup_wait = WaitForSingleObject(lookup, INFINITE);
                DWORD free_wait = WaitForSingleObject(freer, INFINITE);
                CloseHandle(lookup);
                CloseHandle(freer);
                if (lookup_wait != WAIT_OBJECT_0 || free_wait != WAIT_OBJECT_0) {
                    return 3;
                }
                #else
                pthread_t lookup;
                pthread_t freer;
                if (pthread_create(&lookup, NULL, javan_test_lookup_entry, NULL) != 0) {
                    return 2;
                }
                if (pthread_create(&freer, NULL, javan_test_free_entry, NULL) != 0) {
                    (void) pthread_join(lookup, NULL);
                    return 2;
                }
                if (pthread_join(lookup, NULL) != 0 || pthread_join(freer, NULL) != 0) {
                    return 3;
                }
                #endif
                int passed = atomic_load_explicit(&instance_result, memory_order_acquire) == 1
                    && javan_test_node_was_deferred() != 0
                    && javan_heap_live_allocations() == 0;
                javan_test_release_deferred_node();
                printf("%d\\n", passed);
                return 0;
            }
            """,
            "4096",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void concurrentAuthoritativeMissesCompleteWhileAllocationRegistryGrowsAndFrees() throws Exception {
        final InstrumentedRuntimeProbe probe = concurrentAuthoritativeMissesProbe();

        assertThat(runInstrumentedRuntimeProbe(
            probe.replacements(),
            probe.runtimeProbe(),
            probe.main(),
            "131072",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void concurrentAuthoritativeMissesProbeWin32BranchCrossCompilesWhenMinGwIsAvailable() throws Exception {
        final Path compiler = findFirstExecutableOnPath("x86_64-w64-mingw32-gcc");
        assumeTrue(compiler != null, "MinGW cross compiler is not installed");
        final InstrumentedRuntimeProbe probe = concurrentAuthoritativeMissesProbe();
        final Path runtime = writeInstrumentedRuntimeProbe(probe.replacements(), probe.runtimeProbe(), probe.main());
        final Path source = instrumentedRuntimeProbeSource();
        final TestProcesses.Result mainCompile = TestProcesses.run(
            tempDir,
            mingwCompileCommand(compiler, source, tempDir.resolve("instrumented-runtime-probe-main.o")),
            java.time.Duration.ofSeconds(60)
        );
        final TestProcesses.Result runtimeCompile = TestProcesses.run(
            tempDir,
            mingwCompileCommand(compiler, runtime, tempDir.resolve("instrumented-runtime-probe-runtime.o")),
            java.time.Duration.ofSeconds(60)
        );

        assertThat(List.of(mainCompile.exitCode(), runtimeCompile.exitCode()))
            .describedAs(mainCompile.stderr() + runtimeCompile.stderr())
            .containsExactly(0, 0);
    }

    private InstrumentedRuntimeProbe concurrentAuthoritativeMissesProbe() {
        return new InstrumentedRuntimeProbe(
            List.of(new RuntimeReplacement(
                """
                static javan_allocation_node* javan_allocation_registry_lookup(void* value) {
                    if (value == NULL || javan_allocation_index.capacity <= 0) {
                """.stripTrailing(),
                """
                static void javan_test_record_allocation_registry_lookup_lock(void);

                static javan_allocation_node* javan_allocation_registry_lookup(void* value) {
                    javan_test_record_allocation_registry_lookup_lock();
                    if (value == NULL || javan_allocation_index.capacity <= 0) {
                """.stripTrailing()
            )),
            """
            static int javan_test_unlocked_allocation_registry_lookup = 0;

            static void javan_test_record_allocation_registry_lookup_lock(void) {
                if (javan_runtime_lock_depth_value <= 0) {
                    javan_test_unlocked_allocation_registry_lookup = 1;
                }
            }

            int javan_test_authoritative_allocation_miss(void* value) {
                javan_allocation_metadata snapshot;
                return javan_find_allocation(value, &snapshot) == 0;
            }

            int javan_test_allocation_lookup_was_serialized(void) {
                return javan_test_unlocked_allocation_registry_lookup == 0
                    && javan_allocation_index.capacity >= 2048;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>

            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <pthread.h>
            #endif

            enum {
                MUTATION_ROUNDS = 64,
                VALUE_COUNT = 640,
                MISS_COUNT = 131072,
                FINDER_WORKER = 1,
                MUTATOR_WORKER = 2
            };

            typedef struct {
                int kind;
            } javan_test_worker;

            static char borrowed_values[MISS_COUNT];
            static atomic_int miss_failed = ATOMIC_VAR_INIT(0);
            static atomic_int mutation_failed = ATOMIC_VAR_INIT(0);
            static atomic_int workers_ready = ATOMIC_VAR_INIT(0);
            static atomic_int workers_started = ATOMIC_VAR_INIT(0);
            static atomic_int workers_active = ATOMIC_VAR_INIT(0);
            static atomic_int workers_overlapped = ATOMIC_VAR_INIT(0);
            static atomic_int workers_cancelled = ATOMIC_VAR_INIT(0);

            extern int javan_test_authoritative_allocation_miss(void* value);
            extern int javan_test_allocation_lookup_was_serialized(void);

            static int javan_test_wait_for_worker_overlap(void) {
                atomic_fetch_add_explicit(&workers_ready, 1, memory_order_acq_rel);
                while (atomic_load_explicit(&workers_started, memory_order_acquire) == 0
                    && atomic_load_explicit(&workers_cancelled, memory_order_acquire) == 0) {
                }
                if (atomic_load_explicit(&workers_cancelled, memory_order_acquire) != 0) {
                    return 0;
                }
                atomic_fetch_add_explicit(&workers_active, 1, memory_order_acq_rel);
                while (atomic_load_explicit(&workers_active, memory_order_acquire) < 2
                    && atomic_load_explicit(&workers_cancelled, memory_order_acquire) == 0) {
                }
                if (atomic_load_explicit(&workers_cancelled, memory_order_acquire) != 0) {
                    return 0;
                }
                atomic_store_explicit(&workers_overlapped, 1, memory_order_release);
                return 1;
            }

            static void javan_test_find_misses(void) {
                for (int index = 0; index < MISS_COUNT; index++) {
                    if (javan_test_authoritative_allocation_miss(&borrowed_values[index]) == 0) {
                        atomic_store_explicit(&miss_failed, 1, memory_order_release);
                        return;
                    }
                }
            }

            static void javan_test_grow_and_free(void) {
                void* values[VALUE_COUNT];
                for (int round = 0; round < MUTATION_ROUNDS; round++) {
                    for (int index = 0; index < VALUE_COUNT; index++) {
                        values[index] = javan_alloc(1);
                        if (values[index] == NULL) {
                            atomic_store_explicit(&mutation_failed, 1, memory_order_release);
                            return;
                        }
                    }
                    for (int index = VALUE_COUNT - 1; index >= 0; index--) {
                        javan_free(values[index]);
                    }
                }
            }

            static void javan_test_run_worker(void* argument) {
                javan_test_worker* worker = (javan_test_worker*) argument;
                if (javan_test_wait_for_worker_overlap() == 0) {
                    return;
                }
                if (worker->kind == FINDER_WORKER) {
                    javan_test_find_misses();
                    return;
                }
                javan_test_grow_and_free();
            }

            #if defined(_WIN32)
            static DWORD WINAPI javan_test_worker_entry(LPVOID argument) {
                javan_test_run_worker(argument);
                return 0;
            }
            #else
            static void* javan_test_worker_entry(void* argument) {
                javan_test_run_worker(argument);
                return NULL;
            }
            #endif

            static void javan_test_cancel_workers(void) {
                atomic_store_explicit(&workers_cancelled, 1, memory_order_release);
                atomic_store_explicit(&workers_started, 1, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_test_worker finder_worker = { FINDER_WORKER };
                javan_test_worker mutator_worker = { MUTATOR_WORKER };
                #if defined(_WIN32)
                HANDLE finder = CreateThread(NULL, 0, javan_test_worker_entry, &finder_worker, 0, NULL);
                if (finder == NULL) {
                    return 2;
                }
                HANDLE mutator = CreateThread(NULL, 0, javan_test_worker_entry, &mutator_worker, 0, NULL);
                if (mutator == NULL) {
                    javan_test_cancel_workers();
                    (void) WaitForSingleObject(finder, INFINITE);
                    CloseHandle(finder);
                    return 2;
                }
                #else
                pthread_t finder;
                pthread_t mutator;
                if (pthread_create(&finder, NULL, javan_test_worker_entry, &finder_worker) != 0) {
                    return 2;
                }
                if (pthread_create(&mutator, NULL, javan_test_worker_entry, &mutator_worker) != 0) {
                    javan_test_cancel_workers();
                    (void) pthread_join(finder, NULL);
                    return 2;
                }
                #endif
                while (atomic_load_explicit(&workers_ready, memory_order_acquire) < 2) {
                }
                atomic_store_explicit(&workers_started, 1, memory_order_release);
                #if defined(_WIN32)
                DWORD finder_wait = WaitForSingleObject(finder, INFINITE);
                DWORD mutator_wait = WaitForSingleObject(mutator, INFINITE);
                CloseHandle(finder);
                CloseHandle(mutator);
                if (finder_wait != WAIT_OBJECT_0 || mutator_wait != WAIT_OBJECT_0) {
                    return 3;
                }
                #else
                if (pthread_join(finder, NULL) != 0 || pthread_join(mutator, NULL) != 0) {
                    return 3;
                }
                #endif
                javan_validate_heap_metadata();
                printf("%d\\n",
                    atomic_load_explicit(&miss_failed, memory_order_acquire) == 0
                        && atomic_load_explicit(&mutation_failed, memory_order_acquire) == 0
                        && atomic_load_explicit(&workers_overlapped, memory_order_acquire) != 0
                        && javan_test_allocation_lookup_was_serialized() != 0
                        && javan_heap_live_allocations() == 0);
                return 0;
            }
            """
        );
    }

    @Test
    void heapMetadataRejectsAllocationIndexEntryWithoutNode() throws Exception {
        assertThat(heapMetadataFailure(
            """
            void javan_test_corrupt_allocation_index(void* value) {
                int index = javan_registry_slot(javan_allocation_index.values, javan_allocation_index.capacity, value);
                javan_allocation_index.nodes[index] = NULL;
            }
            """,
            """
            extern void javan_test_corrupt_allocation_index(void* value);
            int main(void) {
                javan_register_static_roots(0, 0);
                void* value = javan_alloc(1);
                javan_test_corrupt_allocation_index(value);
                return javan_heap_metadata_failure_result();
            }
            """
        )).isEqualTo("0\ninvalid heap allocation index\n");
    }

    @Test
    void heapMetadataRejectsCacheEntryWithoutNode() throws Exception {
        assertThat(heapMetadataFailure(
            """
            void javan_test_corrupt_allocation_cache(void) {
                javan_allocation_cache_nodes[0] = NULL;
            }
            """,
            """
            extern void javan_test_corrupt_allocation_cache(void);
            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_alloc(1);
                javan_test_corrupt_allocation_cache();
                return javan_heap_metadata_failure_result();
            }
            """
        )).isEqualTo("0\ninvalid heap allocation cache\n");
    }

    private String rootedAllocationRegistryReservationFailure(final int failureStep) throws Exception {
        return runInstrumentedRuntimeProbe(
            List.of(new RuntimeReplacement(
                """
                static void* javan_raw_calloc_retry(unsigned long size) {
                    void* value = calloc(1, size);
                    if (value == NULL) {
                        javan_gc_collect();
                        value = calloc(1, size);
                    }
                    return value;
                }
                """.stripTrailing(),
                """
                int javan_test_allocation_registry_reservation_failure_step = 0;
                int javan_test_allocation_registry_reservation_failure_count = 0;

                static void* javan_raw_calloc_retry(unsigned long size) {
                    if (javan_test_allocation_registry_reservation_failure_step == 1) {
                        javan_test_allocation_registry_reservation_failure_step = 0;
                        javan_test_allocation_registry_reservation_failure_count++;
                        return NULL;
                    }
                    if (javan_test_allocation_registry_reservation_failure_step == 2) {
                        javan_test_allocation_registry_reservation_failure_step = 1;
                    }
                    void* value = calloc(1, size);
                    if (value == NULL) {
                        javan_gc_collect();
                        value = calloc(1, size);
                    }
                    return value;
                }
                """.stripTrailing()
            )),
            """
            void javan_test_alloc_rooted(void** root_slot) {
                (void) javan_alloc_rooted(1, root_slot);
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            extern int javan_test_allocation_registry_reservation_failure_step;
            extern int javan_test_allocation_registry_reservation_failure_count;
            extern void javan_test_alloc_rooted(void** root_slot);

            int main(void) {
                void* values[63];
                static void* rooted = NULL;
                javan_register_static_roots(0, 0);
                for (int index = 0; index < 63; index++) {
                    values[index] = javan_alloc(1);
                }
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    javan_test_allocation_registry_reservation_failure_step = %d;
                    javan_test_alloc_rooted(&rooted);
                    return 2;
                }
                int passed = strcmp(javan_last_error(), "out of memory") == 0
                    && rooted == NULL
                    && javan_test_allocation_registry_reservation_failure_count == 1;
                javan_panic_clear_target(&target);
                javan_clear_error();
                javan_test_allocation_registry_reservation_failure_step = 0;
                javan_validate_heap_metadata();
                void* recovered = javan_alloc(1);
                javan_free(recovered);
                for (int index = 0; index < 63; index++) {
                    javan_free(values[index]);
                }
                javan_validate_heap_metadata();
                printf("%%d\\n", passed != 0
                    && javan_last_error() == NULL
                    && javan_heap_live_allocations() == 0
                    && javan_heap_live_bytes() == 0);
                return 0;
            }
            """.formatted(failureStep),
            "4096",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        );
    }

    private String rootedAllocationNodeMetadataFailure() throws Exception {
        return runInstrumentedRuntimeProbe(
            List.of(
                new RuntimeReplacement(
                    "static void javan_track_allocation(",
                    """
                    static void* javan_test_allocation_node_malloc(void);

                    static void javan_track_allocation(
                    """.stripTrailing()
                ),
                new RuntimeReplacement(
                    "malloc(sizeof(javan_allocation_node))",
                    "javan_test_allocation_node_malloc()",
                    2
                )
            ),
            """
            static int javan_test_allocation_node_malloc_fail = 0;
            static int javan_test_allocation_node_malloc_count = 0;

            static void* javan_test_allocation_node_malloc(void) {
                if (javan_test_allocation_node_malloc_fail != 0) {
                    javan_test_allocation_node_malloc_count++;
                    return NULL;
                }
                return malloc(sizeof(javan_allocation_node));
            }

            void javan_test_alloc_rooted_with_node_failure(void** root_slot) {
                javan_test_allocation_node_malloc_fail = 1;
                (void) javan_alloc_rooted(1, root_slot);
            }

            int javan_test_allocation_node_malloc_attempts(void) {
                return javan_test_allocation_node_malloc_count;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            extern void javan_test_alloc_rooted_with_node_failure(void** root_slot);
            extern int javan_test_allocation_node_malloc_attempts(void);

            int main(void) {
                static void* rooted = NULL;
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    javan_test_alloc_rooted_with_node_failure(&rooted);
                    return 2;
                }
                int passed = strcmp(javan_last_error(), "out of memory") == 0
                    && rooted == NULL
                    && javan_test_allocation_node_malloc_attempts() == 2;
                javan_panic_clear_target(&target);
                javan_clear_error();
                javan_validate_heap_metadata();
                printf("%d\\n", passed != 0
                    && javan_heap_live_allocations() == 0
                    && javan_heap_live_bytes() == 0);
                return 0;
            }
            """,
            "4096",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        );
    }

    private String heapMetadataFailure(final String runtimeProbe, final String main) throws Exception {
        return runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_heap_metadata_failure_result(void) {
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    javan_validate_heap_metadata();
                    return 2;
                }
                printf("%s\\n", javan_last_error());
                return 0;
            }
            """ + runtimeProbe,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            extern int javan_heap_metadata_failure_result(void);
            """ + main,
            "4096",
            Map.of("JAVAN_GC_STRESS", ""),
            java.time.Duration.ofSeconds(30)
        );
    }

    private String runInstrumentedRuntimeProbe(
        final List<RuntimeReplacement> replacements,
        final String runtimeProbe,
        final String main,
        final String heapLimitBytes,
        final Map<String, String> environmentOverrides,
        final java.time.Duration timeout
    ) throws Exception {
        final Path runtime = writeInstrumentedRuntimeProbe(replacements, runtimeProbe, main);
        final Path source = instrumentedRuntimeProbeSource();
        final Path binary = new NativeLinker().link(
            tempDir,
            source,
            runtime,
            tempDir.resolve("instrumented-runtime-probe"),
            NativeLinkInputs.empty(),
            List.of()
        );
        final Map<String, String> environment = new java.util.LinkedHashMap<>();
        environment.put("JAVAN_HEAP_LIMIT_BYTES", heapLimitBytes);
        environment.putAll(environmentOverrides);
        final TestProcesses.Result result = TestProcesses.run(
            tempDir,
            List.of(binary.toString()),
            timeout,
            environment
        );

        return result.exitCode() + "\n" + result.stdout() + result.stderr();
    }

    private Path writeInstrumentedRuntimeProbe(
        final List<RuntimeReplacement> replacements,
        final String runtimeProbe,
        final String main
    ) throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        String instrumented = Files.readString(runtime);
        for (final RuntimeReplacement replacement : replacements) {
            int matches = 0;
            int offset = 0;
            while ((offset = instrumented.indexOf(replacement.target(), offset)) >= 0) {
                matches++;
                offset += replacement.target().length();
            }
            if (matches != replacement.expectedMatches()) {
                throw new IllegalStateException(
                    "runtime instrumentation target count was " + matches
                        + ", expected " + replacement.expectedMatches()
                        + ": " + replacement.target()
                );
            }
            instrumented = instrumented.replace(replacement.target(), replacement.replacement());
        }
        Files.writeString(runtime, instrumented + "\n" + runtimeProbe);
        Files.writeString(instrumentedRuntimeProbeSource(), main);

        return runtime;
    }

    private Path instrumentedRuntimeProbeSource() {
        return tempDir.resolve("instrumented-runtime-probe.c");
    }

    private static List<String> mingwCompileCommand(final Path compiler, final Path source, final Path output) {
        return List.of(
            compiler.toString(),
            "-std=c11",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-Wno-error=unused-function",
            "-Wno-error=unused-variable",
            "-c",
            source.toString(),
            "-o",
            output.toString()
        );
    }

    private record RuntimeReplacement(String target, String replacement, int expectedMatches) {
        private RuntimeReplacement(final String target, final String replacement) {
            this(target, replacement, 1);
        }
    }

    private record InstrumentedRuntimeProbe(
        List<RuntimeReplacement> replacements,
        String runtimeProbe,
        String main
    ) {
    }

    @Test
    void runtimeHeaderDeclaresDoubleToInt() throws Exception {
        new RuntimeFiles().write(tempDir);
        final String header = Files.readString(tempDir.resolve("javan_runtime.h"));

        assertThat(header).contains("int javan_double_to_int(double value);");
    }

    @Test
    void runtimeHeaderAndSourceExposeDoubleToLongHelper() throws Exception {
        final String source = Files.readString(new RuntimeFiles().write(tempDir));
        final String header = Files.readString(tempDir.resolve("javan_runtime.h"));

        assertThat(List.of(
            header.contains("long long javan_double_to_long(double value);"),
            source.contains("long long javan_double_to_long(double value) {")
        )).containsExactly(true, true);
    }

    @Test
    void runtimeHeaderAndSourceExposeLongToDoubleHelper() throws Exception {
        final String source = Files.readString(new RuntimeFiles().write(tempDir));
        final String header = Files.readString(tempDir.resolve("javan_runtime.h"));

        assertThat(List.of(
            header.contains("double javan_l2d(long long value);"),
            source.contains("double javan_l2d(long long value) {")
        )).containsExactly(true, true);
    }

    @Test
    void runtimeHeaderAndSourceExposeFloatToDoubleHelper() throws Exception {
        final String source = Files.readString(new RuntimeFiles().write(tempDir));
        final String header = Files.readString(tempDir.resolve("javan_runtime.h"));

        assertThat(List.of(
            header.contains("double javan_f2d(float value);"),
            source.contains("double javan_f2d(float value) {")
        )).containsExactly(true, true);
    }

    @Test
    void runtimeDoubleToLongGuardsNaNAndRangeBeforeCasting() throws Exception {
        final String source = Files.readString(new RuntimeFiles().write(tempDir));

        assertThat(source).contains("""
            long long javan_double_to_long(double value) {
                if (value != value) {
                    return 0LL;
                }
                if (value >= 0x1p63) {
                    return LLONG_MAX;
                }
                if (value <= -0x1p63) {
                    return LLONG_MIN;
                }
                return (long long) value;
            }
            """.stripTrailing());
    }

    @Test
    void runtimeDoubleToLongBuildsJavaValuesAtBoundaries() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            static double from_bits(uint64_t bits) {
                double value;
                memcpy(&value, &bits, sizeof(value));
                return value;
            }

            int main(void) {
                const uint64_t values[] = {
                    UINT64_C(0x0000000000000000), UINT64_C(0x8000000000000000),
                    UINT64_C(0x401f000000000000), UINT64_C(0xc01f000000000000),
                    UINT64_C(0x0000000000000001), UINT64_C(0x8000000000000001),
                    UINT64_C(0x7ff8000000001234), UINT64_C(0x7ff0000000000000),
                    UINT64_C(0xfff0000000000000), UINT64_C(0x43e0000000000000),
                    UINT64_C(0xc3e0000000000000), UINT64_C(0x43dfffffffffffff),
                    UINT64_C(0xc3dfffffffffffff), UINT64_C(0x43e0000000000001),
                    UINT64_C(0xc3e0000000000001), UINT64_C(0x7fefffffffffffff),
                    UINT64_C(0xffefffffffffffff)
                };
                for (unsigned long index = 0; index < sizeof(values) / sizeof(values[0]); index++) {
                    printf("%lld\\n", javan_double_to_long(from_bits(values[index])));
                }
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("""
            0
            0
            7
            -7
            0
            0
            0
            9223372036854775807
            -9223372036854775808
            9223372036854775807
            -9223372036854775808
            9223372036854774784
            -9223372036854774784
            9223372036854775807
            -9223372036854775808
            9223372036854775807
            -9223372036854775808
            """);
    }

    @Test
    void runtimeDoubleToLongTruncatesTowardZeroUnderUpwardRounding() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <fenv.h>
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            #pragma STDC FENV_ACCESS ON

            static double from_bits(uint64_t bits) {
                double value;
                memcpy(&value, &bits, sizeof(value));
                return value;
            }

            int main(void) {
            #if defined(FE_UPWARD)
                if (fesetround(FE_UPWARD) == 0) {
                    printf("%lld\\n", javan_double_to_long(from_bits(UINT64_C(0x401f000000000000))));
                    printf("%lld\\n", javan_double_to_long(from_bits(UINT64_C(0xc01f000000000000))));
                    return 0;
                }
            #endif
                puts("fenv-unavailable");
                return 0;
            }
            """, "512", fenvLinkInputs());

        assumeTrue(!stdout.equals("fenv-unavailable\\n"), "C fenv cannot set FE_UPWARD");
        assertThat(stdout).isEqualTo("""
            7
            -7
            """);
    }

    @Test
    void runtimeDoubleToIntGuardsNaNAndRangeBeforeCasting() throws Exception {
        final String source = Files.readString(new RuntimeFiles().write(tempDir));

        assertThat(source).contains("""
            int javan_double_to_int(double value) {
                if (value != value) {
                    return 0;
                }
                if (value >= 2147483647.0) {
                    return 2147483647;
                }
                if (value <= -2147483648.0) {
                    return (-2147483647 - 1);
                }
                return (int) value;
            }
            """.stripTrailing());
    }

    @Test
    void runtimeLongToDoubleBuildsJavaRawBitsAtBoundaries() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            static unsigned long long raw_bits(double value) {
                uint64_t bits = UINT64_C(0);
                memcpy(&bits, &value, sizeof(bits));
                return (unsigned long long) bits;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%016llx\\n", raw_bits(javan_l2d(0LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(1LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(-1LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(9007199254740991LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(9007199254740992LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(9007199254740993LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(9007199254740995LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(-9007199254740993LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(-9007199254740995LL)));
                printf("%016llx\\n", raw_bits(javan_l2d(LLONG_MIN)));
                printf("%016llx\\n", raw_bits(javan_l2d(LLONG_MAX)));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("""
            0000000000000000
            3ff0000000000000
            bff0000000000000
            433fffffffffffff
            4340000000000000
            4340000000000000
            4340000000000002
            c340000000000000
            c340000000000002
            c3e0000000000000
            43e0000000000000
            """);
    }

    @Test
    void runtimeLongToDoubleUsesJavaTiesToEvenUnderUpwardRounding() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <fenv.h>
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            #pragma STDC FENV_ACCESS ON

            static unsigned long long raw_bits(double value) {
                uint64_t bits = UINT64_C(0);
                memcpy(&bits, &value, sizeof(bits));
                return (unsigned long long) bits;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
            #if defined(FE_UPWARD)
                if (fesetround(FE_UPWARD) == 0) {
                    printf("%016llx\\n", raw_bits(javan_l2d(9007199254740993LL)));
                    printf("%016llx\\n", raw_bits(javan_l2d(9007199254740995LL)));
                    return 0;
                }
            #endif
                puts("fenv-unavailable");
                return 0;
            }
            """, "512", fenvLinkInputs());

        assumeTrue(!stdout.equals("fenv-unavailable\n"), "C fenv cannot set FE_UPWARD");
        assertThat(stdout).isEqualTo("""
            4340000000000000
            4340000000000002
            """);
    }

    @Test
    void runtimeSetEqualsReturnsTrueForIdentity() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* set = javan_set_empty();
                printf("%d\\n", javan_set_equals(set, set));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeSetEqualsRejectsNullArgument() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_set_equals(javan_set_empty(), NULL));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsRejectsNullReceiver() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_set_equals(NULL, javan_set_empty()));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsRejectsNonSetArgument() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_set_equals(javan_set_empty(), javan_arraylist_new()));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @TestFactory
    List<DynamicTest> sealedRecordShapeRejectsMalformedEncoding() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("record-shape-probe.c");
        Files.writeString(main, """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(int argc, char** argv) {
                if (argc != 2) {
                    return 2;
                }
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    javan_record_shape_validate(NULL, argv[1]);
                    javan_panic_clear_target(&target);
                    puts("no panic");
                    return 0;
                }
                javan_panic_clear_target(&target);
                const char* error = javan_last_error();
                printf("%s\\n", error == NULL ? "missing panic" : error);
                return 0;
            }
            """);
        final Path binary = new NativeLinker().link(
            tempDir,
            main,
            runtime,
            tempDir.resolve("record-shape-probe"),
            NativeLinkInputs.empty(),
            List.of()
        );
        return List.of(
            "p", "p;", "p0;", "p01;", "p-1;", "p1", "p1;;",
            "p2;1;", "p1;1;", "p2147483648;", "p1;2", "p1;tail"
        ).stream().map(shape -> dynamicTest("rejects " + shape, () -> {
            final TestProcesses.Result result = TestProcesses.run(
                tempDir,
                List.of(binary.toString(), shape),
                java.time.Duration.ofSeconds(30),
                Map.of("JAVAN_HEAP_LIMIT_BYTES", "4096", "JAVAN_GC_STRESS", "1")
            );

            assertThat(result.exitCode() + "\n" + result.stdout() + result.stderr())
                .as("shape %s", shape)
                .isEqualTo("0\ninvalid generated record shape\n");
        })).toList();
    }

    @Test
    void sealedRecordShapeRejectsValueOutsideUnion() throws Exception {
        final String panic = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            "javan_record_shape_validate(javan_integer_value_of(7), \"p2147483646;2147483647;\");"
        );

        assertThat(panic).isEqualTo("record generic value does not match declared shape\n");
    }

    @Test
    void sealedRecordShapeParsesTrailingBytesAfterMembershipMatch() throws Exception {
        final String panic = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            struct javan_object_header* value =
                (struct javan_object_header*) javan_alloc(sizeof(struct javan_object_header));
            value->_javan_type_id = 1;
            value->_javan_runtime_state = NULL;
            value->_javan_runtime_kind = 0;
            value->_javan_runtime_reserved = 0;
            javan_register_object((void*) value, 1);
            """,
            "javan_record_shape_validate((void*) value, \"p1;tail\");"
        );

        assertThat(panic).isEqualTo("invalid generated record shape\n");
    }

    @Test
    void sealedRecordShapeAcceptsNullWithZeroHash() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                printf("%d\\n", javan_record_shape_hash_code(NULL, "p1;2147483647;"));
                return 0;
            }
            """, "4096");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsRejectsUntrackedStaticStringArgument() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_set_equals(javan_set_empty(), "static"));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsRejectsUnequalLogicalSizes() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* left = javan_hashset_new();
                void* right = javan_hashset_new();
                javan_set_add(left, "left");
                javan_set_add(right, "left");
                javan_set_add(right, "right");
                printf("%d\\n", javan_set_equals(left, right));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsIgnoresInsertionOrder() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* left = javan_hashset_new();
                void* right = javan_hashset_new();
                javan_set_add(left, "left");
                javan_set_add(left, "right");
                javan_set_add(right, "right");
                javan_set_add(right, "left");
                printf("%d\\n", javan_set_equals(left, right));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeSetEqualsRejectsSameSizeMissingMember() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* left = javan_hashset_new();
                void* right = javan_hashset_new();
                javan_set_add(left, "left");
                javan_set_add(left, "right");
                javan_set_add(right, "left");
                javan_set_add(right, "other");
                printf("%d\\n", javan_set_equals(left, right));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsUsesNullSafeElementEquality() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* left = javan_hashset_new();
                void* right = javan_hashset_new();
                javan_set_add(left, NULL);
                javan_set_add(right, NULL);
                printf("%d\\n", javan_set_equals(left, right));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeSetEqualsRecognizesUnmodifiableSet() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* mutable = javan_hashset_new();
                void* other = javan_hashset_new();
                javan_set_add(mutable, "left");
                javan_set_add(other, "left");
                printf("%d\\n", javan_set_equals(javan_set_unmodifiable(mutable), other));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeSetEqualsRejectsUnmodifiableCollectionOfSet() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* set = javan_hashset_new();
                javan_set_add(set, "left");
                printf("%d\\n", javan_set_equals(set, javan_list_unmodifiable(set)));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeSetEqualsUsesMaterializedMapKeySetSnapshot() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* map = javan_hashmap_new();
                (void) javan_map_put(map, "left", "one");
                void* keys = javan_map_key_set(map);
                (void) javan_map_put(map, "right", "two");
                void* expected = javan_hashset_new();
                javan_set_add(expected, "left");
                printf("%d\\n", javan_set_equals(keys, expected));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeSetEqualsReadsSequentialMutationState() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* left = javan_hashset_new();
                void* right = javan_hashset_new();
                javan_set_add(left, "left");
                javan_set_add(right, "left");
                int before = javan_set_equals(left, right);
                javan_set_add(left, "right");
                printf("%d %d\\n", before, javan_set_equals(left, right));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1 0\n");
    }

    @Test
    void runtimeSetEqualsRepeatsImmutableReads() throws Exception {
        final String stdout = runRuntimeBoundaryProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* left = javan_set_of_pair("left", "right");
                void* right = javan_set_of_pair("right", "left");
                printf("%d %d\\n", javan_set_equals(left, right), javan_set_equals(left, right));
                return 0;
            }
            """, "512");

        assertThat(stdout).isEqualTo("1 1\n");
    }

    @Test
    void writeIncludesNativeOsArchSystemProperty() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "strcmp(key, \"os.arch\") == 0",
            "return \"aarch64\";",
            "return \"x86_64\";"
        );
    }

    @Test
    void writeIncludesRootAndEnglishLocaleCaseRuntime() throws Exception {
        final String runtime = Files.readString(new RuntimeFiles().write(tempDir));

        assertThat(runtime).contains(
            "#define JAVAN_LOCALE_ENGLISH 1",
            "#define JAVAN_LOCALE_ROOT 2",
            "void* javan_locale_root(void)",
            "void* javan_string_to_lower_case_locale(const char* value, void* locale)"
        );
    }

    @Test
    void stringLocaleLowerCaseRejectsDynamicNonAsciiInput() throws Exception {
        final String output = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            void* locale = javan_locale_root();
            """,
            "javan_string_to_lower_case_locale(\"\\xC3\\xA9\", locale);"
        );

        assertThat(output).contains("non-ASCII string case conversion requires the UTF-16 string model");
    }

    @Test
    void pendingThrowableRuntimeAssignabilityMatchesJavaHierarchy() throws Exception {
        final Set<String> throwableTypes = new LinkedHashSet<>();
        throwableTypes.add("java/lang/Throwable");
        for (final JdkCallSupport.PlatformThrowableParent edge : JdkCallSupport.platformThrowableParents()) {
            throwableTypes.add(edge.type());
            throwableTypes.add(edge.parent());
        }
        throwableTypes.add("java/example/GeneratedException");
        throwableTypes.add("javax/example/GeneratedError");
        final StringBuilder cases = new StringBuilder();
        for (final String thrownType : throwableTypes) {
            for (final String catchType : throwableTypes) {
                cases.append("    {\"")
                    .append(thrownType)
                    .append("\", \"")
                    .append(catchType)
                    .append("\", ")
                    .append(JdkCallSupport.isPlatformThrowableAssignable(thrownType, catchType) ? 1 : 0)
                    .append("},\n");
            }
        }
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            typedef struct {
                const char* thrown_type;
                const char* catch_type;
                int expected;
            } throwable_case;

            static const throwable_case cases[] = {
            %s};

            int main(void) {
                int failures = 0;
                javan_register_static_roots(0, 0);
                const size_t count = sizeof(cases) / sizeof(cases[0]);
                for (size_t index = 0; index < count; index++) {
                    javan_pending_throw(cases[index].thrown_type, 0, "", "", "", -1, -1, "");
                    if (javan_pending_type_assignable_to((void*) cases[index].catch_type) != cases[index].expected) {
                        failures++;
                    }
                    javan_pending_clear();
                }
                printf("%%d\\n", failures);
                return 0;
            }
            """.formatted(cases),
            "4096"
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void writeCopiesPendingPanicDetailBeforeClearingManagedThrowable() throws Exception {
        final String runtime = Files.readString(new RuntimeFiles().write(tempDir));

        assertThat(runtime).containsSubsequence(
            "char detail_copy[256];",
            "javan_copy_error_field(detail_copy, sizeof(detail_copy), detail_source);",
            "javan_pending_clear_state(state);",
            "javan_runtime_lock_leave();",
            "javan_panic_at("
        );
    }

    @Test
    void pendingThrowableRuntimeAssignabilityIsFalseWithoutPendingState() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                printf("%d\\n", javan_pending_type_assignable_to((void*) "java/lang/Throwable"));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void pendingThrowableRuntimeAssignabilityRejectsNullCatchType() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_pending_throw("java/lang/NullPointerException", 0, "", "", "", -1, -1, "");
                const int result = javan_pending_type_assignable_to(0);
                javan_pending_clear();
                printf("%d\\n", result);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void caughtThrowableRethrowRestoresExactRuntimeType() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_pending_throw(
                    "java/lang/NullPointerException",
                    javan_string_from("inner"),
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    7,
                    4,
                    "throw value;"
                );
                void* caught = javan_pending_catch();
                javan_pending_rethrow(caught);
                printf("%d\\n", javan_pending_type_is((void*) "java/lang/NullPointerException"));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void caughtThrowableMessageIsCollectibleAfterItsRootFrameEnds() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_pending_throw("java/lang/Throwable", 0, "", "", "", -1, -1, "");
                javan_pending_clear();
                javan_gc_collect();
                const unsigned long baseline = javan_heap_live_allocations();

                void* message = 0;
                void* caught = 0;
                void** roots[] = {
                    (void**) &message,
                    (void**) &caught
                };
                javan_root_frame_push(roots, 2);
                message = javan_string_from("dynamic");
                javan_pending_throw("java/lang/IllegalArgumentException", message, "", "", "", -1, -1, "");
                caught = javan_pending_catch();
                javan_root_frame_pop(roots);
                message = 0;
                caught = 0;
                javan_gc_collect();

                printf("%d\\n", javan_heap_live_allocations() == baseline);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void materializedLambdaKindPredicateDistinguishesRuntimeAndConcreteObjects() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                void* lambda = 0;
                void* concrete = 0;
                void** roots[] = {
                    (void**) &lambda,
                    (void**) &concrete
                };
                javan_register_static_roots(0, 0);
                javan_root_frame_push(roots, 2);
                lambda = javan_materialized_lambda_new(1);
                concrete = javan_integer_value_of(7);
                printf(
                    "%d:%d:%d\\n",
                    javan_materialized_lambda_is_instance(lambda),
                    javan_materialized_lambda_is_instance(concrete),
                    javan_materialized_lambda_is_instance(0)
                );
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:0:0\n");
    }

    @Test
    void materializedLambdaWrapperStateAndCapturesAreCollectible() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                void* capture = 0;
                void* lambda = 0;
                void** roots[] = {
                    (void**) &capture,
                    (void**) &lambda
                };
                javan_register_static_roots(0, 0);
                javan_root_frame_push(roots, 2);
                capture = javan_integer_value_of(7);
                lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void materializedLambdaRootsVarargsCapturesAcrossForcedCollection() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                void* capture = 0;
                void* lambda = 0;
                void** capture_roots[] = {
                    (void**) &capture
                };
                void** lambda_roots[] = {
                    (void**) &lambda
                };
                javan_register_static_roots(0, 0);
                javan_root_frame_push(capture_roots, 1);
                capture = javan_integer_value_of(7);
                for (int index = 0; index < 8; index++) {
                    (void) javan_integer_value_of(index + 100);
                }
                javan_root_frame_pop(capture_roots);
                unsigned long before = javan_heap_gc_collections();
                lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
                javan_root_frame_push(lambda_roots, 1);
                printf(
                    "%d:%d\\n",
                    javan_heap_gc_collections() > before,
                    javan_integer_int_value(javan_materialized_lambda_capture(lambda, 0))
                );
                javan_root_frame_pop(lambda_roots);
                return 0;
            }
            """,
            "64"
        );

        assertThat(stdout).isEqualTo("1:7\n");
    }

    @Test
    void materializedLambdaRejectsNegativeCaptureCountBeforeVarargsRead() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            """,
            """
            (void) javan_materialized_lambda_new_with_captures(1, -1);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture count\n");
    }

    @Test
    void materializedLambdaRejectsCaptureCountAboveRuntimeBoundBeforeVarargsRead() throws Exception {
        final String nullCaptures = ", (void*) 0".repeat(256);
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            """,
            "(void) javan_materialized_lambda_new_with_captures(1, 256" + nullCaptures + ");"
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture count\n");
    }

    @Test
    void materializedLambdaRejectsMaximumIntegerCaptureCountBeforePointerSizeMultiplication() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            """,
            """
            (void) javan_materialized_lambda_new_with_captures(1, INT_MAX);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture count\n");
    }

    @Test
    void materializedLambdaHeapValidationRejectsUndersizedCaptureBufferBeforeIteration() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* state_value = 0;
            static void** roots[] = {
                (void**) &state_value
            };
            javan_register_static_roots(roots, 1);
            void* capture = javan_integer_value_of(7);
            void* lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            state_value = ((struct javan_object_header*) lambda)->_javan_runtime_state;
            javan_gc_collect();
            materialized_lambda_state_probe* state = (materialized_lambda_state_probe*) state_value;
            state->capture_count = 2;
            """,
            """
            javan_validate_heap_metadata();
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda metadata\n");
    }

    @Test
    void materializedLambdaGcRejectsUndersizedCaptureBufferBeforeIteration() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* state_value = 0;
            static void** roots[] = {
                (void**) &state_value
            };
            javan_register_static_roots(roots, 1);
            void* capture = javan_integer_value_of(7);
            void* lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            state_value = ((struct javan_object_header*) lambda)->_javan_runtime_state;
            javan_gc_collect();
            materialized_lambda_state_probe* state = (materialized_lambda_state_probe*) state_value;
            state->capture_count = 2;
            """,
            """
            javan_gc_collect();
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda metadata\n");
    }

    @Test
    void materializedLambdaTargetHelperRejectsShortString() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* value = 0;
            static void** roots[] = {
                (void**) &value
            };
            javan_register_static_roots(roots, 1);
            value = javan_string_from("x");
            """,
            """
            (void) javan_materialized_lambda_target_id(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda target\n");
    }

    @Test
    void materializedLambdaCaptureHelperRejectsShortString() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* value = 0;
            static void** roots[] = {
                (void**) &value
            };
            javan_register_static_roots(roots, 1);
            value = javan_string_from("x");
            """,
            """
            (void) javan_materialized_lambda_capture(value, 0);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture\n");
    }

    @Test
    void materializedLambdaTargetHelperRejectsOwnedCaptureBuffer() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* capture = 0;
            static void* lambda = 0;
            static void** roots[] = {
                (void**) &capture,
                (void**) &lambda
            };
            javan_register_static_roots(roots, 2);
            capture = javan_integer_value_of(7);
            lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            materialized_lambda_state_probe* state =
                (materialized_lambda_state_probe*) ((struct javan_object_header*) lambda)->_javan_runtime_state;
            void* value = (void*) state->captures;
            """,
            """
            (void) javan_materialized_lambda_target_id(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda target\n");
    }

    @Test
    void materializedLambdaCaptureHelperRejectsOwnedCaptureBuffer() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* capture = 0;
            static void* lambda = 0;
            static void** roots[] = {
                (void**) &capture,
                (void**) &lambda
            };
            javan_register_static_roots(roots, 2);
            capture = javan_integer_value_of(7);
            lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            materialized_lambda_state_probe* state =
                (materialized_lambda_state_probe*) ((struct javan_object_header*) lambda)->_javan_runtime_state;
            void* value = (void*) state->captures;
            """,
            """
            (void) javan_materialized_lambda_capture(value, 0);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture\n");
    }

    @Test
    void materializedLambdaTargetHelperRejectsStateNode() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* capture = 0;
            static void* lambda = 0;
            static void** roots[] = {
                (void**) &capture,
                (void**) &lambda
            };
            javan_register_static_roots(roots, 2);
            capture = javan_integer_value_of(7);
            lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            void* value = ((struct javan_object_header*) lambda)->_javan_runtime_state;
            """,
            """
            (void) javan_materialized_lambda_target_id(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda target\n");
    }

    @Test
    void materializedLambdaCaptureHelperRejectsStateNode() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* capture = 0;
            static void* lambda = 0;
            static void** roots[] = {
                (void**) &capture,
                (void**) &lambda
            };
            javan_register_static_roots(roots, 2);
            capture = javan_integer_value_of(7);
            lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            void* value = ((struct javan_object_header*) lambda)->_javan_runtime_state;
            """,
            """
            (void) javan_materialized_lambda_capture(value, 0);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture\n");
    }

    @Test
    void materializedLambdaTargetHelperRejectsStaleManagedValue() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            void* value = javan_string_from("stale");
            javan_gc_collect();
            """,
            """
            (void) javan_materialized_lambda_target_id(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda target\n");
    }

    @Test
    void materializedLambdaCaptureHelperRejectsStaleManagedValue() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            void* value = javan_string_from("stale");
            javan_gc_collect();
            """,
            """
            (void) javan_materialized_lambda_capture(value, 0);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture\n");
    }

    @Test
    void materializedLambdaTargetHelperRejectsUnmanagedValue() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            void* value = (void*) 1;
            """,
            """
            (void) javan_materialized_lambda_target_id(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda target\n");
    }

    @Test
    void materializedLambdaCaptureHelperRejectsUnmanagedValue() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            javan_register_static_roots(0, 0);
            void* value = (void*) 1;
            """,
            """
            (void) javan_materialized_lambda_capture(value, 0);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture\n");
    }

    @Test
    void materializedLambdaTargetHelperRejectsPartiallyBuiltWrapper() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* capture = 0;
            static void* lambda = 0;
            static void* value = 0;
            static void** roots[] = {
                (void**) &capture,
                (void**) &lambda,
                (void**) &value
            };
            javan_register_static_roots(roots, 3);
            capture = javan_integer_value_of(7);
            lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            value = javan_alloc(sizeof(struct javan_object_header));
            ((struct javan_object_header*) value)->_javan_runtime_state =
                ((struct javan_object_header*) lambda)->_javan_runtime_state;
            ((struct javan_object_header*) value)->_javan_runtime_kind =
                JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA;
            """,
            """
            (void) javan_materialized_lambda_target_id(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda target\n");
    }

    @Test
    void materializedLambdaCaptureHelperRejectsPartiallyBuiltWrapper() throws Exception {
        final String stdout = runRuntimePanicProbe(
            """
            static void* capture = 0;
            static void* lambda = 0;
            static void* value = 0;
            static void** roots[] = {
                (void**) &capture,
                (void**) &lambda,
                (void**) &value
            };
            javan_register_static_roots(roots, 3);
            capture = javan_integer_value_of(7);
            lambda = javan_materialized_lambda_new_with_captures(1, 1, capture);
            value = javan_alloc(sizeof(struct javan_object_header));
            ((struct javan_object_header*) value)->_javan_runtime_state =
                ((struct javan_object_header*) lambda)->_javan_runtime_state;
            ((struct javan_object_header*) value)->_javan_runtime_kind =
                JAVAN_RUNTIME_KIND_MATERIALIZED_LAMBDA;
            """,
            """
            (void) javan_materialized_lambda_capture(value, 0);
            """
        );

        assertThat(stdout).isEqualTo("invalid materialized lambda capture\n");
    }

    @Test
    void materializedLambdaHelpersRemainSafeDuringConcurrentCollection() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <pthread.h>
            #include <stdatomic.h>
            #include <stdio.h>

            static void* lambda_value = 0;
            static void* capture_value = 0;
            static _Atomic int failures = 0;
            static void** static_roots[] = {
                (void**) &lambda_value,
                (void**) &capture_value
            };

            static void* read_lambda(void* ignored) {
                (void) ignored;
                for (int index = 0; index < 20000; index++) {
                    if (javan_materialized_lambda_is_instance(lambda_value) == 0
                        || javan_materialized_lambda_target_id(lambda_value) != 1
                        || javan_materialized_lambda_capture(lambda_value, 0) != capture_value) {
                        atomic_store(&failures, 1);
                        return 0;
                    }
                }
                return 0;
            }

            static void* collect_heap(void* ignored) {
                (void) ignored;
                for (int index = 0; index < 20000; index++) {
                    (void) javan_integer_value_of(index);
                    javan_gc_collect();
                }
                return 0;
            }

            int main(void) {
                pthread_t reader;
                pthread_t collector;
                javan_register_static_roots(static_roots, 2);
                capture_value = javan_integer_value_of(7);
                lambda_value = javan_materialized_lambda_new_with_captures(1, 1, capture_value);
                if (pthread_create(&reader, 0, read_lambda, 0) != 0
                    || pthread_create(&collector, 0, collect_heap, 0) != 0) {
                    return 2;
                }
                (void) pthread_join(reader, 0);
                (void) pthread_join(collector, 0);
                printf("%d\\n", atomic_load(&failures));
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(60)
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void resourceSectionEscapesNonPrintableResourcePathsWithoutStringFormat() throws Exception {
        final Path source = Files.write(tempDir.resolve("raw.bin"), new byte[]{1});

        final String section = RuntimeSourceResourceSection.render(
            List.of(new ResourceBundler.ResourceFile("assets/\u0001.bin", source, 1))
        );

        assertThat(section).contains("assets/\\001.bin");
    }

    @Test
    void writeGuardsSocketTimeoutHelpersBehindWindowsUnsupportedBranches() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "static void javan_socket_apply_receive_timeout(int fd, int timeout_millis, const char* message) {",
            "#if defined(_WIN32)",
            "javan_socket_runtime_unsupported();",
            "#else",
            "timeout.tv_usec = (long) ((timeout_millis % 1000) * 1000);",
            "static void javan_socket_wait_readable(int fd, int timeout_millis, const char* timeout_message, const char* wait_message) {",
            "static void javan_socket_connect_native_timeout(int fd, const struct sockaddr* address, socklen_t address_length, int timeout_millis) {",
            "int flags = fcntl(fd, F_GETFL, 0);",
            "timeout_value.tv_usec = (long) ((timeout % 1000) * 1000);"
        );
    }

    @Test
    void runtimeSystemErrPrintlnIntWritesOneLine() throws Exception {
        final String stderr = runRuntimeBoundaryProbeStderr(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_int(javan_system_err(), 7);
                return 0;
            }
            """,
            "512"
        );

        assertThat(stderr).isEqualTo("7\n");
    }

    @Test
    void runtimePrintObjectValuePrintsNullForNullReference() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_print_object_value(0);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("null");
    }

    @Test
    void runtimePrintObjectValuePrintsIntegerWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_integer_value_of(7));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("7\n");
    }

    @Test
    void runtimePrintObjectValuePrintsLongWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_long_value_of(9LL));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("9\n");
    }

    @Test
    void runtimePrintObjectValuePrintsFloatWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_float_value_of(1.5f));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("1.5\n");
    }

    @Test
    void runtimePrintObjectValuePrintsDoubleWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_double_value_of(2.5));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("2.5\n");
    }

    @Test
    void runtimePrintObjectValuePrintsBooleanWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_boolean_value_of(1));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("true\n");
    }

    @Test
    void runtimePrintObjectValuePrintsByteWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_byte_value_of(12));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("12\n");
    }

    @Test
    void runtimePrintObjectValuePrintsShortWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_short_value_of(34));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("34\n");
    }

    @Test
    void runtimePrintObjectValuePrintsCharacterWrapper() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_printstream_println_object(javan_system_out(), javan_character_value_of('j'));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("j\n");
    }

    @Test
    void writeTracksRuntimeAllocationsAndRegistersShutdownCleanup() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "typedef struct javan_allocation_node",
            "void* base;",
            "int kind;",
            "int type_id;",
            "int collectible;",
            "int runtime_kind;",
            "unsigned int mark;",
            "atexit(javan_allocator_cleanup)",
            "static void* javan_realloc(void* value, unsigned long size)",
            "static void* javan_export_alloc(unsigned long size)",
            "const char* value = getenv(\"JAVAN_MAX_ALLOCATION_BYTES\");",
            "if (value != NULL && value[0] != '\\0') {",
            "javan_max_allocation_bytes = limit;",
            "value = getenv(\"JAVAN_HEAP_LIMIT_BYTES\");",
            "if (value != NULL && value[0] != '\\0') {",
            "javan_heap_limit_bytes = limit;",
            "static void javan_check_allocation_size(unsigned long size)",
            "static void javan_prepare_allocation(unsigned long size)",
            "static void javan_prepare_reallocation(unsigned long old_size, unsigned long new_size)",
            "static void* javan_calloc_checked(unsigned long size)",
            "static void* javan_raw_calloc_retry(unsigned long size)",
            "javan_prepare_allocation(actual_size);",
            "javan_prepare_reallocation(node->size, actual_size);",
            "static int javan_heap_limit_growth_exceeded(unsigned long old_size, unsigned long new_size)",
            "unsigned long growth = new_size - old_size;",
            "javan_panic(\"unknown runtime allocation\")",
            "javan_find_allocation_locked(value, &previous)"
        );
    }

    @Test
    void writeIncludesAllocationLookupCacheForHotPaths() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);

        assertThat(source).contains(
            "#define JAVAN_ALLOCATION_CACHE_SIZE 4",
            "static void* javan_allocation_cache_values[JAVAN_ALLOCATION_CACHE_SIZE];",
            "static javan_allocation_node* javan_allocation_cache_nodes[JAVAN_ALLOCATION_CACHE_SIZE];",
            "static javan_allocation_node* javan_allocation_cache_lookup(void* value) {",
            "javan_allocation_cache_store(value, node);",
            "javan_allocation_cache_remove(value);",
            "if (previous == NULL) {",
            "javan_allocation_node* cached = javan_allocation_cache_lookup(value);"
        );
        assertThat(source).contains(
            "        javan_allocation_node* indexed = javan_allocation_registry_lookup(value);\n"
                + "        if (indexed != NULL) {\n"
                + "            javan_allocation_cache_store(value, indexed);\n"
                + "        }\n"
                + "        return indexed;\n"
                + "    }\n"
                + "    javan_allocation_node* prior = NULL;\n"
        );
    }

    @Test
    void writeEmitsHeapMetadataAccountingAndValidationHooks() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "#define JAVAN_HEAP_KIND_RUNTIME 1",
            "#define JAVAN_HEAP_KIND_OBJECT 2",
            "#define JAVAN_HEAP_KIND_ARRAY 3",
            "#define JAVAN_HEAP_KIND_EXPORT 4",
            "#define JAVAN_RUNTIME_KIND_OBJECT_LIST 1",
            "#define JAVAN_RUNTIME_KIND_OBJECT_MAP 3",
            "#define JAVAN_RUNTIME_KIND_STRING 5",
            "#define JAVAN_RUNTIME_KIND_PROCESS_RESULT 6",
            "#define JAVAN_RUNTIME_KIND_STRING_BUILDER 7",
            "#define JAVAN_RUNTIME_KIND_OWNED_BUFFER 8",
            "#define JAVAN_RUNTIME_KIND_INET_ADDRESS 9",
            "#define JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS 10",
            "#define JAVAN_RUNTIME_KIND_SOCKET 11",
            "#define JAVAN_RUNTIME_KIND_SERVER_SOCKET 12",
            "#define JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM 13",
            "#define JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM 14",
            "#define JAVAN_RUNTIME_KIND_HTTP_BODY_PUBLISHER 21",
            "#define JAVAN_TYPE_JAVA_LANG_THREAD -1008",
            "static unsigned long javan_total_allocations_value = 0;",
            "static unsigned long javan_live_allocated_bytes_value = 0;",
            "static unsigned long javan_peak_live_allocated_bytes_value = 0;",
            "void javan_validate_heap_metadata(void)",
            "const char* value = getenv(\"JAVAN_GC_STRESS\");",
            "javan_panic(\"heap accounting mismatch\")"
        );
    }

    @Test
    void writeMakesRuntimeStringsCollectibleAndTraversesProcessResults() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "static char* javan_string_alloc(unsigned long size)",
            "javan_update_runtime_allocation_kind((void*) value, JAVAN_RUNTIME_KIND_STRING);",
            "node->collectible = runtime_kind == JAVAN_RUNTIME_KIND_STRING",
            "|| runtime_kind == JAVAN_RUNTIME_KIND_PROCESS_RESULT",
            "typedef struct javan_process_result",
            "javan_update_runtime_allocation_kind((void*) result, JAVAN_RUNTIME_KIND_PROCESS_RESULT);",
            "javan_gc_mark_value(result->stdout_value);",
            "javan_gc_mark_value(result->stderr_value);",
            "char* stdout_value = result->stdout_value;",
            "char* stderr_value = result->stderr_value;",
            "result->stdout_value = NULL;",
            "result->stderr_value = NULL;",
            "javan_free(stdout_value);",
            "void** javan_process_stdout_roots[] = {",
            "javan_root_frame_push(javan_process_stdout_roots, 1);",
            "void** javan_directory_child_roots[] = {",
            "javan_root_frame_push(javan_directory_child_roots, 1);",
            "javan_directory_stream_insert_sorted(result, child);",
            "javan_root_frame_pop(javan_directory_child_roots);"
        );
    }

    @Test
    void writeRegistersObjectsAfterRegistryCapacityIsSafe() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);

        assertThat(source.indexOf("javan_object_registry_ensure_capacity(javan_objects.length + 1);"))
            .isLessThan(source.indexOf("javan_update_allocation_metadata(value, JAVAN_HEAP_KIND_OBJECT, type_id);"));
        assertThat(source).contains(
            "static void javan_object_registry_cleanup(void);",
            "javan_object_registry_cleanup();",
            "void** next_values = (void**) javan_raw_calloc_retry((unsigned long) next_capacity * sizeof(void*));",
            "int* next_type_ids = (int*) javan_raw_calloc_retry((unsigned long) next_capacity * sizeof(int));",
            "if (next_type_ids == NULL) {",
            "free(next_values);",
            "javan_panic(\"out of memory\");",
            "free(old_values);",
            "free(old_type_ids);",
            "static void javan_object_registry_cleanup(void)",
            "free(javan_objects.values);",
            "free(javan_objects.type_ids);"
        );
    }

    @Test
    void writeMarksObjectsArraysAndStaticRootsInHeapMetadata() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "void javan_register_static_roots(void*** roots, int count)",
            "int javan_heap_static_root_count(void)",
            "javan_update_allocation_metadata(value, JAVAN_HEAP_KIND_OBJECT, type_id);",
            "javan_update_allocation_metadata((void*) array, JAVAN_HEAP_KIND_ARRAY, kind);",
            "static unsigned long javan_array_allocation_size(unsigned long header_size, int length, unsigned long element_size)",
            "javan_panic(\"negative array length\");",
            "javan_panic(\"array allocation too large\");",
            "javan_array_allocation_size(sizeof(javan_object_array), length, sizeof(void*))",
            "javan_array_allocation_size(sizeof(javan_double_array), length, sizeof(double))",
            "static int javan_array_kind_collectible(int type_id)",
            "|| type_id == JAVAN_ARRAY_KIND_BOOLEAN",
            "static int javan_object_kind_collectible(int type_id)",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_INTEGER",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_DOUBLE",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_BOOLEAN",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_BYTE",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_SHORT",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_CHARACTER",
            "|| type_id == JAVAN_TYPE_JAVA_NIO_FILE_ATTRIBUTE_FILE_TIME",
            "|| type_id == JAVAN_TYPE_JAVA_TIME_DURATION",
            "|| type_id == JAVAN_TYPE_JAVA_LANG_THREAD",
            "node->collectible = ((kind == JAVAN_HEAP_KIND_OBJECT && javan_object_kind_collectible(type_id) != 0)",
            "|| (kind == JAVAN_HEAP_KIND_ARRAY && javan_array_kind_collectible(type_id) != 0)) ? 1 : 0;"
        );
    }

    @Test
    void writeRegistersTypeDescriptorsAndRootFramesInHeapMetadata() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "typedef struct javan_root_frame",
            "#define JAVAN_ROOT_FRAME_CACHE_LIMIT 32",
            "JavanTypeDescriptor* javan_type_descriptors_value = NULL;",
            "void javan_register_type_descriptors(JavanTypeDescriptor* descriptors, int count)",
            "void javan_root_frame_push(void*** roots, int count)",
            "void javan_root_frame_pop(void*** roots)",
            "static JAVAN_THREAD_LOCAL javan_root_frame* javan_root_frame_cache_value = NULL;",
            "static JAVAN_THREAD_LOCAL int javan_root_frame_cache_count_value = 0;",
            "static javan_root_frame* javan_root_frame_take(void) {",
            "static void javan_root_frame_release(javan_root_frame* frame) {",
            "static void javan_root_frame_cache_cleanup(void) {",
            "javan_root_frame* frame = javan_root_frame_take();",
            "javan_root_frame_release(frame);",
            "javan_root_frame_cache_cleanup();",
            "int javan_heap_type_descriptor_count(void)",
            "int javan_heap_root_frame_depth(void)",
            "int javan_heap_frame_root_count(void)",
            "javan_panic(\"root frame accounting mismatch\")",
            "javan_root_frame_cleanup();",
            "void javan_panic(const char* value) {",
            "void javan_panic_at(",
            "javan_root_frame_cleanup();",
            "exit(1);"
        );
    }

    @Test
    void writeEmitsPlatformRecursiveRuntimeLockForSharedHeapState() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "static CRITICAL_SECTION javan_runtime_lock_value;",
            "static INIT_ONCE javan_runtime_lock_once = INIT_ONCE_STATIC_INIT;",
            "static BOOL CALLBACK javan_runtime_lock_initialize_once(",
            "InitializeCriticalSection(&javan_runtime_lock_value);",
            "InitOnceExecuteOnce(",
            "EnterCriticalSection(&javan_runtime_lock_value)",
            "LeaveCriticalSection(&javan_runtime_lock_value)",
            "#include <pthread.h>",
            "static pthread_mutex_t javan_runtime_lock_value;",
            "static pthread_once_t javan_runtime_lock_once = PTHREAD_ONCE_INIT;",
            "static JAVAN_THREAD_LOCAL int javan_runtime_lock_depth_value = 0;",
            "static void javan_runtime_lock_initialize(void) {",
            "pthread_mutexattr_settype(&attributes, PTHREAD_MUTEX_RECURSIVE)",
            "pthread_mutex_init(&javan_runtime_lock_value, &attributes)",
            "void javan_runtime_lock_enter(void) {",
            "pthread_once(&javan_runtime_lock_once, javan_runtime_lock_initialize)",
            "pthread_mutex_lock(&javan_runtime_lock_value)",
            "void javan_runtime_lock_leave(void) {",
            "pthread_mutex_unlock(&javan_runtime_lock_value)",
            "static void javan_runtime_lock_reset_for_panic(void) {",
            "while (javan_runtime_lock_depth_value > 0) {",
            "javan_runtime_lock_enter();",
            "javan_runtime_lock_leave();",
            "javan_runtime_lock_reset_for_panic();"
        );
    }

    @Test
    void writeSerializesCollectorVisibleRuntimeReferenceStores() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "void javan_object_array_set(void* array, int index, void* value) {\n"
                + "    javan_runtime_lock_enter();\n"
                + "    javan_object_array* values = (javan_object_array*) javan_array_checked(array);",
            "values->values[index] = value;\n"
                + "    javan_runtime_lock_leave();",
            "if (result == NULL) {\n"
                + "        javan_panic(\"invalid array copy result\");\n"
                + "    }\n"
                + "    javan_runtime_lock_enter();\n"
                + "    *result = NULL;\n"
                + "    javan_runtime_lock_leave();\n"
                + "    void* source_root = array;"
        );
    }

    @Test
    void interruptibleJoinReadsCompletionThroughItsNativeMutex() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);

        assertThat(source).contains(
            "static int javan_thread_completion_is_signaled(javan_thread* thread) {",
            "AcquireSRWLockShared(&thread->native_completion_lock);",
            "ReleaseSRWLockShared(&thread->native_completion_lock);",
            "pthread_mutex_lock(&thread->native_completion_mutex)",
            "int signaled = thread->native_completion_signaled != 0;",
            "pthread_mutex_unlock(&thread->native_completion_mutex)",
            "int done = not_started != 0 || javan_thread_completion_is_signaled(thread) != 0;",
            "javan_runtime_lock_enter();\n"
                + "    int started = thread->started != 0;\n"
                + "    javan_runtime_lock_leave();\n"
                + "    if (started == 0) {\n"
                + "        return;\n"
                + "    }",
            "while (thread->native_completion_signaled == 0) {",
            "javan_runtime_lock_enter();\n"
                + "    javan_thread_leave_live_root(value);",
            "javan_thread_completion_signal(thread);\n"
                + "    javan_current_thread_value = NULL;\n"
                + "    javan_runtime_lock_leave();"
        );
        assertThat(source).doesNotContain(
            "int done = thread->started == 0 || thread->native_completion_signaled != 0;",
            "while (thread->started != 0 && thread->native_completion_signaled == 0) {"
        );
    }

    @Test
    void workerExitReleasesItsThreadLocalRootFrameCache() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "javan_thread_run_registered_target(value);\n"
                + "    javan_root_frame_cache_cleanup();\n"
                + "    javan_runtime_lock_enter();\n"
                + "    javan_thread_leave_live_root(value);"
        );
    }

    @Test
    void rootFrameCleanupSerializesPublishedWorkerChains() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);

        for (final String signature : java.util.List.of(
            "static void javan_root_frame_cleanup(void)",
            "static void javan_root_frame_cleanup_to("
        )) {
            final String function = runtimeFunction(source, signature);
            assertThat(function.indexOf("javan_runtime_lock_enter();"))
                .as(signature)
                .isGreaterThanOrEqualTo(0);
            assertThat(function.lastIndexOf("javan_runtime_lock_leave();"))
                .as(signature)
                .isGreaterThan(function.indexOf("javan_runtime_lock_enter();"));
        }
    }

    @Test
    void waitForNonCurrentThreadsRootsTheSelectedWorkerAcrossTheWait() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String function = runtimeFunction(
            Files.readString(runtime),
            "void javan_wait_for_non_current_threads(void)"
        );

        assertThat(function).contains(
            "void** javan_wait_for_non_current_threads_roots[] = { &next };",
            "javan_root_frame_push(javan_wait_for_non_current_threads_roots, 1);",
            "next = candidate;",
            "javan_root_frame_pop(javan_wait_for_non_current_threads_roots);"
        );
        final int selection = function.indexOf("next = candidate;");
        assertThat(function.lastIndexOf("javan_runtime_lock_enter();", selection))
            .isGreaterThanOrEqualTo(0);
        assertThat(function.indexOf("javan_runtime_lock_leave();", selection))
            .isGreaterThan(selection);
        assertThat(function.indexOf("javan_thread_join(next);"))
            .isLessThan(function.indexOf("javan_root_frame_pop(javan_wait_for_non_current_threads_roots);"));
    }

    @Test
    void writeSerializesTheSupportedJavaAtomicFamily() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);

        final Map<String, String> operations = Map.ofEntries(
            Map.entry("void javan_atomic_integer_init(", "javan_atomic_integer_checked"),
            Map.entry("int javan_atomic_integer_get(", "javan_atomic_integer_checked"),
            Map.entry("void javan_atomic_integer_set(", "javan_atomic_integer_checked"),
            Map.entry("int javan_atomic_integer_get_and_increment(", "javan_atomic_integer_checked"),
            Map.entry("int javan_atomic_integer_increment_and_get(", "javan_atomic_integer_checked"),
            Map.entry("int javan_atomic_integer_decrement_and_get(", "javan_atomic_integer_checked"),
            Map.entry("void javan_atomic_boolean_init(", "javan_atomic_boolean_checked"),
            Map.entry("int javan_atomic_boolean_get(", "javan_atomic_boolean_checked"),
            Map.entry("void javan_atomic_boolean_set(", "javan_atomic_boolean_checked"),
            Map.entry("void javan_atomic_reference_init(", "javan_atomic_reference_checked"),
            Map.entry("void* javan_atomic_reference_get(", "javan_atomic_reference_checked"),
            Map.entry("int javan_atomic_reference_compare_and_set(", "javan_atomic_reference_checked"),
            Map.entry("void javan_atomic_reference_set(", "javan_atomic_reference_checked"),
            Map.entry("void javan_atomic_long_init(", "javan_atomic_long_checked"),
            Map.entry("long long javan_atomic_long_get(", "javan_atomic_long_checked"),
            Map.entry("void javan_atomic_long_set(", "javan_atomic_long_checked"),
            Map.entry("long long javan_atomic_long_increment_and_get(", "javan_atomic_long_checked"),
            Map.entry("long long javan_atomic_long_decrement_and_get(", "javan_atomic_long_checked")
        );
        for (final Map.Entry<String, String> operation : operations.entrySet()) {
            final String function = runtimeFunction(source, operation.getKey());
            final int enter = function.indexOf("javan_runtime_lock_enter();");
            final int checked = function.indexOf(operation.getValue());
            final int leave = function.indexOf("javan_runtime_lock_leave();");
            assertThat(enter).as(operation.getKey()).isGreaterThanOrEqualTo(0);
            assertThat(enter).as(operation.getKey()).isLessThan(checked);
            assertThat(checked).as(operation.getKey()).isLessThan(leave);
        }

        final String compareAndSet = runtimeFunction(
            source,
            "int javan_atomic_reference_compare_and_set("
        );
        assertThat(compareAndSet.indexOf("if (state->value == expected_value)"))
            .isLessThan(compareAndSet.indexOf("state->value = next_value;"));
        assertThat(compareAndSet.indexOf("state->value = next_value;"))
            .isLessThan(compareAndSet.indexOf("javan_runtime_lock_leave();"));
    }

    @Test
    void atomicIntegerAndLongCountersWrapAtTheirJavaBoundaries() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* integer = javan_atomic_integer_new();
                void* long_value = javan_atomic_long_new();
                void** roots[] = { &integer, &long_value };
                javan_root_frame_push(roots, 2);

                javan_atomic_integer_init(integer, INT_MAX);
                printf("get-and=%d\\n", javan_atomic_integer_get_and_increment(integer));
                printf("after-get-and=%d\\n", javan_atomic_integer_get(integer));
                javan_atomic_integer_init(integer, INT_MAX);
                printf("increment=%d\\n", javan_atomic_integer_increment_and_get(integer));
                javan_atomic_integer_init(integer, INT_MIN);
                printf("decrement=%d\\n", javan_atomic_integer_decrement_and_get(integer));

                javan_atomic_long_init(long_value, LLONG_MAX);
                printf("long-increment=%lld\\n", javan_atomic_long_increment_and_get(long_value));
                javan_atomic_long_init(long_value, LLONG_MIN);
                printf("long-decrement=%lld\\n", javan_atomic_long_decrement_and_get(long_value));

                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            get-and=2147483647
            after-get-and=-2147483648
            increment=-2147483648
            decrement=2147483647
            long-increment=-9223372036854775808
            long-decrement=9223372036854775807
            """
        );
    }

    @Test
    void atomicCounterWrapsAvoidSignedOverflowExpressions() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);

        assertThat(runtimeFunction(source, "int javan_atomic_integer_get_and_increment(")).contains(
            "state->value = current == INT_MAX ? INT_MIN : current + 1;"
        );
        assertThat(runtimeFunction(source, "int javan_atomic_integer_increment_and_get(")).contains(
            "state->value = state->value == INT_MAX ? INT_MIN : state->value + 1;"
        );
        assertThat(runtimeFunction(source, "int javan_atomic_integer_decrement_and_get(")).contains(
            "state->value = state->value == INT_MIN ? INT_MAX : state->value - 1;"
        );
        assertThat(runtimeFunction(source, "long long javan_atomic_long_increment_and_get(")).contains(
            "state->value = state->value == LLONG_MAX ? LLONG_MIN : state->value + 1LL;"
        );
        assertThat(runtimeFunction(source, "long long javan_atomic_long_decrement_and_get(")).contains(
            "state->value = state->value == LLONG_MIN ? LLONG_MAX : state->value - 1LL;"
        );
    }

    @Test
    void writeEmitsWindowsHighResolutionNanoTimeFallback() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "LARGE_INTEGER frequency;",
            "LARGE_INTEGER counter;",
            "QueryPerformanceFrequency(&frequency)",
            "QueryPerformanceCounter(&counter)",
            "GetTickCount64() * 1000000LL"
        );
    }

    @Test
    void writeMarksWindowsProcessExecutionUnsupportedUntilPorted() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "static void javan_sleep_micros(unsigned long micros) {",
            "Sleep(millis);",
            "return javan_process_result_new(127, \"\", \"process execution unsupported on Windows\");"
        );
    }

    @Test
    void generatedRuntimeCrossCompilesToWindowsPeWhenMinGwIsAvailable() throws Exception {
        final Path compiler = findFirstExecutableOnPath("x86_64-w64-mingw32-gcc");
        assumeTrue(compiler != null, "MinGW cross compiler is not installed");

        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("windows-probe.c");
        Files.writeString(main, """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                return 0;
            }
            """);
        final Path output = tempDir.resolve("windows-probe.exe");
        final TestProcesses.Result result = TestProcesses.run(
            tempDir,
            java.util.List.of(
                compiler.toString(),
                "-std=c11",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-Wno-error=unused-function",
                "-Wno-error=unused-variable",
                main.toString(),
                runtime.toString(),
                "-lws2_32",
                "-o",
                output.toString()
            ),
            java.time.Duration.ofSeconds(60)
        );

        assertThat(result.exitCode())
            .describedAs(result.stderr())
            .isEqualTo(0);
        assertThat(output).exists();
        assertThat(Files.readAllBytes(output)).startsWith((byte) 'M', (byte) 'Z');
    }

    @Test
    void generatedRuntimeDoubleToFloatCrossCompilesToWindowsPeWhenMinGwIsAvailable() throws Exception {
        final Path compiler = findFirstExecutableOnPath("x86_64-w64-mingw32-gcc");
        assumeTrue(compiler != null, "MinGW cross compiler is not installed");
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("windows-double-to-float-probe.c");
        Files.writeString(main, """
            #include "javan_runtime.h"

            int main(void) {
                return javan_d2f(1.5) == 1.5f ? 0 : 1;
            }
            """);
        final TestProcesses.Result result = TestProcesses.run(
            tempDir,
            List.of(
                compiler.toString(),
                "-std=c11",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-Wno-error=unused-function",
                "-Wno-error=unused-variable",
                main.toString(),
                runtime.toString(),
                "-lws2_32",
                "-o",
                tempDir.resolve("windows-double-to-float-probe.exe").toString()
            ),
            java.time.Duration.ofSeconds(60)
        );

        assertThat(result.exitCode()).describedAs(result.stderr()).isZero();
    }

    @Test
    void generatedRuntimeExecutesBasicWindowsProbeWhenHostCompilerIsAvailable() throws Exception {
        assumeTrue(isWindowsHost(), "Host is not Windows");
        final Path compiler = findFirstExecutableOnPath("gcc.exe", "gcc", "x86_64-w64-mingw32-gcc.exe", "x86_64-w64-mingw32-gcc");
        assumeTrue(compiler != null, "Windows C compiler is not installed");

        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("windows-host-probe.c");
        Files.writeString(main, """
            #include "javan_runtime.h"

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_println("ok");
                return 0;
            }
            """);
        final Path output = tempDir.resolve("windows-host-probe.exe");
        final TestProcesses.Result compile = TestProcesses.run(
            tempDir,
            java.util.List.of(
                compiler.toString(),
                "-std=c11",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-Wno-error=unused-function",
                "-Wno-error=unused-variable",
                main.toString(),
                runtime.toString(),
                "-lws2_32",
                "-o",
                output.toString()
            ),
            java.time.Duration.ofSeconds(60)
        );
        assertThat(compile.exitCode())
            .describedAs(compile.stderr())
            .isEqualTo(0);

        final TestProcesses.Result run = TestProcesses.run(
            tempDir,
            java.util.List.of(output.toString()),
            java.time.Duration.ofSeconds(30)
        );
        assertThat(run.exitCode())
            .describedAs(run.stderr())
            .isEqualTo(0);
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).isEqualTo("ok\n");
    }

    @Test
    void writeEmitsSafePointMarkSweepForGeneratedObjectsAndObjectArrays() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "void javan_gc_safe_point(void)",
            "void javan_gc_collect(void)",
            "JavanTypeDescriptor* descriptor = javan_type_descriptor_for(type_id);",
            "javan_gc_mark_object_array((javan_object_array*) value);",
            "javan_gc_mark_value(((javan_thread*) value)->target);",
            "javan_gc_mark_static_roots();",
            "static void javan_gc_mark_thread_roots(void)",
            "javan_gc_mark_thread_roots();",
            "javan_gc_mark_frame_roots();",
            "javan_gc_mark_runtime_object_references();",
            "javan_gc_sweep_unmarked();",
            "javan_gc_collected_allocations_value++;",
            "const char* value = getenv(\"JAVAN_GC_SAFEPOINT_INTERVAL\");"
        );
    }

    @Test
    void writeIncludesCurrentThreadInterruptStateRuntimeHelpers() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "#define JAVAN_THREAD_LOCAL _Thread_local",
            "static JAVAN_THREAD_LOCAL char javan_last_error_value[512];",
            "static JAVAN_THREAD_LOCAL char javan_last_error_code_value[64];",
            "static JAVAN_THREAD_LOCAL int javan_last_error_set = 0;",
            "static JAVAN_THREAD_LOCAL jmp_buf* javan_panic_target = NULL;",
            "static JAVAN_THREAD_LOCAL JavanSourceContext* javan_source_context_top = NULL;",
            "static JAVAN_THREAD_LOCAL javan_root_frame* javan_root_frames_value = NULL;",
            "static JAVAN_THREAD_LOCAL javan_native_resource_frame* javan_native_resource_frames_value = NULL;",
            "static JAVAN_THREAD_LOCAL int javan_root_frame_depth_value = 0;",
            "static JAVAN_THREAD_LOCAL int javan_frame_root_count_value = 0;",
            "static JAVAN_THREAD_LOCAL void* javan_current_thread_value = NULL;",
            "} javan_thread;",
            "char* name;",
            "static long long javan_platform_thread_name_counter_value = 0;",
            "void* javan_thread_new(void) {",
            "void* javan_thread_new_virtual(void) {",
            "static javan_thread* javan_require_thread(void* value) {",
            "static void javan_thread_mark_started(javan_thread* thread) {",
            "static void javan_thread_mark_completed(javan_thread* thread) {",
            "object->virtual_thread = 0;",
            "object->name = NULL;",
            "object->id = javan_thread_next_id();",
            "thread->target = NULL;",
            "static long long javan_thread_next_id(void) {",
            "static int javan_thread_has_live_lifecycle(javan_thread* thread) {",
            "static void javan_thread_completion_reset(javan_thread* thread) {",
            "static void javan_thread_completion_signal(javan_thread* thread) {",
            "static void javan_thread_enter_live_root(void* value) {",
            "static void javan_thread_leave_live_root(void* value) {",
            "static javan_thread* javan_thread_bootstrap_current(void) {",
            "static javan_thread* javan_current_thread_object(void) {",
            "return javan_thread_bootstrap_current();",
            "void* javan_thread_current(void) {",
            "void* javan_thread_get_name(void* value) {",
            "void javan_thread_set_name(void* value, void* name) {",
            "void javan_thread_set_name_nullable(void* value, void* name) {",
            "long long javan_thread_get_id(void* value) {",
            "void javan_thread_detach_current(void) {",
            "javan_panic(\"cannot detach current thread with live root frames\")",
            "javan_panic(\"cannot detach current thread with live native resources\")",
            "javan_thread_leave_live_root(javan_current_thread_value);",
            "javan_current_thread_value = NULL;",
            "javan_thread_assign_name_text((javan_thread*) value, \"main\");",
            "void javan_thread_set_target(void* value, void* target) {",
            "void javan_thread_run_target(void* target) {",
            "javan_panic(\"Thread.start with Runnable target has no closed-world Runnable.run implementation\")",
            "void javan_thread_sleep_millis(long long millis) {",
            "if (millis < 0) {",
            "javan_panic(\"negative Thread.sleep millis\")",
            "static void javan_sleep_micros(unsigned long micros);",
            "javan_sleep_micros((unsigned long) (chunk * 1000LL));",
            "int javan_thread_interrupted(void) {",
            "void javan_thread_interrupt(void* value) {",
            "int javan_thread_is_interrupted(void* value) {",
            "int javan_thread_is_alive(void* value) {",
            "int javan_thread_is_virtual(void* value) {",
            "int alive = javan_thread_has_live_lifecycle(thread);",
            "return alive;",
            "void javan_thread_start(void* value) {",
            "javan_thread_enter_live_root(value);",
            "thread->native_completion_signaled = 0;",
            "void** javan_thread_start_roots[] = { &value, &target };",
            "javan_root_frame_push(javan_thread_start_roots, 2);",
            "javan_thread_run_target(target);",
            "javan_root_frame_pop(javan_thread_start_roots);",
            "javan_thread_leave_live_root(value);",
            "javan_thread_completion_signal(thread);",
            "pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED)",
            "void javan_thread_join(void* value) {",
            "pthread_cond_wait(&thread->native_completion_cond, &thread->native_completion_mutex)",
            "javan_panic(\"Thread.join on current thread is not supported yet\")",
            "static void** javan_thread_roots_value = NULL;",
            "static int javan_thread_root_count_value = 0;",
            "static int javan_thread_root_capacity_value = 0;",
            "static void javan_thread_root_register(void* value) {",
            "javan_panic(\"thread root already registered\");",
            "void** javan_thread_root_register_roots[] = { &value };",
            "javan_root_frame_push(javan_thread_root_register_roots, 1);",
            "javan_root_frame_pop(javan_thread_root_register_roots);",
            "static void javan_thread_root_unregister(void* value) {",
            "javan_panic(\"thread root not registered\");",
            "unsigned long javan_heap_registered_thread_roots(void) {",
            "unsigned long javan_heap_thread_objects(void) {",
            "unsigned long javan_heap_started_threads(void) {",
            "unsigned long javan_heap_completed_threads(void) {",
            "unsigned long javan_heap_active_threads(void) {",
            "unsigned long javan_heap_threads_with_target(void) {",
            "int javan_heap_current_thread_root_present(void) {",
            "&& javan_thread_root_index(javan_current_thread_value) >= 0;",
            "javan_gc_mark_value(((javan_thread*) value)->name);"
        );
    }

    @Test
    void writeIncludesCallerRootedThreadResultAbi() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String generated = Files.readString(tempDir.resolve("javan_runtime.h"))
            + Files.readString(runtime);

        assertThat(generated).contains(
            "void javan_thread_new_into(void** result);",
            "void javan_thread_new_into(void** result) {",
            "void javan_thread_new_virtual_into(void** result);",
            "void javan_thread_new_virtual_into(void** result) {",
            "void javan_virtual_thread_builder_start_into(void** result, void* value, void* runnable);",
            "void javan_virtual_thread_builder_unstarted_into(void** result, void* value, void* runnable);",
            "void javan_virtual_thread_factory_new_thread_into(void** result, void* value, void* runnable);",
            "void javan_virtual_thread_executor_submit_into(void** result, void* value, void* runnable);",
            "void javan_scheduled_thread_pool_executor_schedule_into(void** result, void* value, void* runnable, long long delay, void* unit);",
            "void javan_scheduled_thread_pool_executor_schedule_at_fixed_rate_into(void** result, void* value, void* runnable, long long initial_delay, long long period, void* unit);",
            "void javan_scheduled_thread_pool_executor_schedule_with_fixed_delay_into(void** result, void* value, void* runnable, long long initial_delay, long long delay, void* unit);"
        );
    }

    @Test
    void runtimeThreadCurrentBootstrapIsIdempotentAndRootsCurrentThreadOnce() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* first = javan_thread_current();
                void* second = javan_thread_current();
                printf("same=%d\\n", first == second ? 1 : 0);
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            same=1
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeThreadDetachCurrentDropsRootAndAllowsFreshBootstrap() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                printf("before=%lu\\n", javan_heap_registered_thread_roots());
                javan_thread_detach_current();
                printf("after=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                (void) javan_thread_current();
                printf("reboot=%lu\\n", javan_heap_registered_thread_roots());
                printf("recurrent=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            before=1
            after=0
            current=0
            reboot=1
            recurrent=1
            """
        );
    }

    @Test
    void runtimeThreadDetachCurrentRejectsLiveRootFrames() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* current = javan_thread_current();
                void** roots[] = {
                    (void**) &current
                };
                jmp_buf target;
                javan_root_frame_push(roots, 1);
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                javan_thread_detach_current();
                return 2;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("cannot detach current thread with live root frames\n");
    }

    @Test
    void runtimeThreadDetachCurrentSucceedsAfterRepeatedBalancedRootFrames() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                for (int index = 0; index < 128; index++) {
                    void* current = javan_thread_current();
                    void** roots[] = {
                        (void**) &current
                    };
                    javan_root_frame_push(roots, 1);
                    javan_root_frame_pop(roots);
                }
                printf("before=%lu:%d:%d\\n",
                    javan_heap_registered_thread_roots(),
                    javan_heap_current_thread_root_present(),
                    javan_heap_root_frame_depth());
                javan_thread_detach_current();
                printf("after=%lu:%d:%d\\n",
                    javan_heap_registered_thread_roots(),
                    javan_heap_current_thread_root_present(),
                    javan_heap_root_frame_depth());
                (void) javan_thread_current();
                printf("reboot=%lu:%d:%d\\n",
                    javan_heap_registered_thread_roots(),
                    javan_heap_current_thread_root_present(),
                    javan_heap_root_frame_depth());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            before=1:1:0
            after=0:0:0
            reboot=1:1:0
            """
        );
    }

    @Test
    void runtimeRecoverablePanicsUnwindCachedRootFramesAndStillAllowDetach() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                for (int index = 0; index < 64; index++) {
                    void* current = javan_thread_current();
                    void** roots[] = {
                        (void**) &current
                    };
                    jmp_buf target;
                    javan_panic_set_target(&target);
                    if (setjmp(target) != 0) {
                        javan_clear_error();
                        continue;
                    }
                    javan_root_frame_push(roots, 1);
                    javan_panic("recoverable cached root frame probe");
                }
                printf("before=%d:%lu:%d\\n",
                    javan_heap_root_frame_depth(),
                    javan_heap_registered_thread_roots(),
                    javan_heap_current_thread_root_present());
                javan_thread_detach_current();
                javan_gc_collect();
                printf("after=%d:%lu:%d:%lu\\n",
                    javan_heap_root_frame_depth(),
                    javan_heap_registered_thread_roots(),
                    javan_heap_current_thread_root_present(),
                    javan_heap_live_allocations());
                (void) javan_thread_current();
                printf("reboot=%d:%lu:%d\\n",
                    javan_heap_root_frame_depth(),
                    javan_heap_registered_thread_roots(),
                    javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            before=0:1:1
            after=0:0:0:0
            reboot=0:1:1
            """
        );
    }

    @Test
    void runtimeHostThreadGetsDistinctCurrentThreadAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #endif
            #include <stdio.h>

            static void* child_current = NULL;
            static unsigned long child_roots = 0;

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                child_current = javan_thread_current();
                child_roots = javan_heap_registered_thread_roots();
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* main_current = javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                printf("same=%d\\n", child_current == main_current ? 1 : 0);
                printf("during=%lu\\n", child_roots);
                printf("after=%lu\\n", javan_heap_registered_thread_roots());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            same=0
            during=2
            after=1
            """
        );
    }

    @Test
    void runtimeConcurrentHostThreadsCanAttachCollectDetachWithoutLeakingRoots() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #endif
            #include <stdint.h>
            #include <stdio.h>

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                for (int index = 0; index < 32; index++) {
                    (void) javan_thread_current();
                    if (!javan_heap_current_thread_root_present()) {
                        #if defined(_WIN32)
                        return 1U;
                        #else
                        return (void*) (uintptr_t) 1;
                        #endif
                    }
                    javan_gc_collect();
                    javan_thread_detach_current();
                    if (javan_heap_current_thread_root_present()) {
                        #if defined(_WIN32)
                        return 2U;
                        #else
                        return (void*) (uintptr_t) 2;
                        #endif
                    }
                }
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE left = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (left == NULL) {
                    return 3;
                }
                HANDLE right = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (right == NULL) {
                    CloseHandle(left);
                    return 4;
                }
                if (WaitForSingleObject(left, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(left);
                    CloseHandle(right);
                    return 5;
                }
                if (WaitForSingleObject(right, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(left);
                    CloseHandle(right);
                    return 6;
                }
                DWORD left_result = 0;
                DWORD right_result = 0;
                if (!GetExitCodeThread(left, &left_result)) {
                    CloseHandle(left);
                    CloseHandle(right);
                    return 7;
                }
                if (!GetExitCodeThread(right, &right_result)) {
                    CloseHandle(left);
                    CloseHandle(right);
                    return 8;
                }
                CloseHandle(left);
                CloseHandle(right);
                printf("left=%ld\\n", (long) left_result);
                printf("right=%ld\\n", (long) right_result);
                #else
                pthread_t left;
                pthread_t right;
                void* left_result = NULL;
                void* right_result = NULL;
                if (pthread_create(&left, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                if (pthread_create(&right, NULL, child_main, NULL) != 0) {
                    return 4;
                }
                if (pthread_join(left, &left_result) != 0) {
                    return 5;
                }
                if (pthread_join(right, &right_result) != 0) {
                    return 6;
                }
                printf("left=%ld\\n", (long) (uintptr_t) left_result);
                printf("right=%ld\\n", (long) (uintptr_t) right_result);
                #endif
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            left=0
            right=0
            roots=1
            """
        );
    }

    @Test
    void runtimeHostThreadRootFramesStayPublishedAcrossConcurrentGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int gc_done;
            static atomic_int worker_result;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* rooted = javan_thread_new();
                void** roots[] = {
                    (void**) &rooted
                };
                javan_root_frame_push(roots, 1);
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&gc_done, memory_order_acquire) == 0) {
                    wait_tick();
                }
                atomic_store_explicit(&worker_result, javan_thread_is_alive(rooted), memory_order_release);
                javan_root_frame_pop(roots);
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&gc_done, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("worker=%d\\n", atomic_load_explicit(&worker_result, memory_order_acquire));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            worker=0
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeBootstrapThreadRootFramesStayPublishedAcrossConcurrentWorkerGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdatomic.h>
            #include <stdio.h>
            #include <unistd.h>

            static atomic_int worker_ready;
            static atomic_int worker_cycles;
            static atomic_int release_worker;

            void javan_thread_run_target(void* target) {
                (void) target;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    javan_gc_collect();
                    atomic_fetch_add_explicit(&worker_cycles, 1, memory_order_release);
                    usleep(1000);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* rooted = javan_string_value_of_int(41);
                void** rooted_roots[] = {
                    (void**) &rooted
                };
                javan_root_frame_push(rooted_roots, 1);
                void* worker = javan_thread_new();
                void* target = javan_thread_new();
                void** worker_roots[] = {
                    (void**) &worker,
                    (void**) &target
                };
                javan_root_frame_push(worker_roots, 2);
                javan_thread_set_target(worker, target);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                while (atomic_load_explicit(&worker_cycles, memory_order_acquire) < 8) {
                    usleep(1000);
                }
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                javan_root_frame_pop(worker_roots);
                worker = NULL;
                target = NULL;
                javan_gc_collect();
                printf("rooted=%d\\n", javan_string_length((const char*) rooted));
                printf("live=%lu\\n", javan_heap_live_allocations());
                javan_root_frame_pop(rooted_roots);
                javan_gc_collect();
                printf("after=%lu\\n", javan_heap_live_allocations());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            rooted=2
            live=3
            after=2
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalValueSurvivesConcurrentGcAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_result;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* local = javan_thread_local_new();
                javan_thread_local_set(local, javan_string_value_of_int(41));
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }
                void* retained = javan_thread_local_get(local);
                if (retained == NULL) {
                    atomic_store_explicit(&worker_result, -1, memory_order_release);
                } else {
                    atomic_store_explicit(&worker_result, javan_string_length((const char*) retained), memory_order_release);
                }
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("worker=%d\\n", atomic_load_explicit(&worker_result, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            worker=2
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalObjectGraphSurvivesConcurrentGcAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_present;
            static atomic_int worker_map_size;
            static atomic_int worker_list_size;
            static atomic_int worker_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* local = javan_thread_local_new();
                void* key = javan_string_value_of_int(7);
                void* payload = javan_string_value_of_int(41);
                void* list = javan_arraylist_new();
                (void) javan_arraylist_add(list, payload);
                void* map = javan_hashmap_new();
                (void) javan_map_put(map, key, list);
                void* optional = javan_optional_of(map);
                javan_thread_local_set(local, optional);
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }
                void* retained_optional = javan_thread_local_get(local);
                atomic_store_explicit(&worker_present, javan_optional_is_present(retained_optional), memory_order_release);
                void* retained_map = javan_optional_or_else_throw(retained_optional);
                atomic_store_explicit(&worker_map_size, javan_map_size(retained_map), memory_order_release);
                void* retained_list = javan_map_get(retained_map, key);
                atomic_store_explicit(&worker_list_size, javan_list_size(retained_list), memory_order_release);
                void* retained_payload = javan_list_get(retained_list, 0);
                atomic_store_explicit(&worker_payload_length, javan_string_length((const char*) retained_payload), memory_order_release);
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&worker_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&worker_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&worker_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&worker_payload_length, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=2
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalSiblingRemoveKeepsRetainedGraphAliveDuringConcurrentGcAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* removed_local = javan_thread_local_new();
                void* retained_local = javan_thread_local_new();

                void* removed_key = javan_string_value_of_int(5);
                void* removed_payload = javan_string_value_of_int(55);
                void* removed_list = javan_arraylist_new();
                (void) javan_arraylist_add(removed_list, removed_payload);
                void* removed_map = javan_hashmap_new();
                (void) javan_map_put(removed_map, removed_key, removed_list);
                void* removed_optional = javan_optional_of(removed_map);
                javan_thread_local_set(removed_local, removed_optional);

                void* retained_key = javan_string_value_of_int(6);
                void* retained_payload = javan_string_value_of_int(66);
                void* retained_list = javan_arraylist_new();
                (void) javan_arraylist_add(retained_list, retained_payload);
                void* retained_map = javan_hashmap_new();
                (void) javan_map_put(retained_map, retained_key, retained_list);
                void* retained_optional = javan_optional_of(retained_map);
                javan_thread_local_set(retained_local, retained_optional);
                javan_thread_local_remove(removed_local);

                removed_key = NULL;
                removed_payload = NULL;
                removed_list = NULL;
                removed_map = NULL;
                removed_optional = NULL;
                retained_payload = NULL;
                retained_list = NULL;
                retained_map = NULL;
                retained_optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }

                void* live_optional = javan_thread_local_get(retained_local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(live_optional), memory_order_release);
                void* live_map = javan_optional_or_else_throw(live_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(live_map), memory_order_release);
                void* live_list = javan_map_get(live_map, retained_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(live_list), memory_order_release);
                void* live_payload = javan_list_get(live_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) live_payload), memory_order_release);
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=2
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalSiblingRemoveKeepsRetainedGraphAliveDuringRepeatedSafepointGcAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <pthread.h>
            #include <stdatomic.h>
            #include <stdio.h>
            #include <unistd.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void* child_main(void* argument) {
                (void) argument;
                (void) javan_thread_current();
                void* removed_local = javan_thread_local_new();
                void* retained_local = javan_thread_local_new();

                void* removed_key = javan_string_value_of_int(5);
                void* removed_payload = javan_string_value_of_int(55);
                void* removed_list = javan_arraylist_new();
                (void) javan_arraylist_add(removed_list, removed_payload);
                void* removed_map = javan_hashmap_new();
                (void) javan_map_put(removed_map, removed_key, removed_list);
                void* removed_optional = javan_optional_of(removed_map);
                javan_thread_local_set(removed_local, removed_optional);

                void* retained_key = javan_string_value_of_int(6);
                void* retained_payload = javan_string_value_of_int(66);
                void* retained_list = javan_arraylist_new();
                (void) javan_arraylist_add(retained_list, retained_payload);
                void* retained_map = javan_hashmap_new();
                (void) javan_map_put(retained_map, retained_key, retained_list);
                void* retained_optional = javan_optional_of(retained_map);
                javan_thread_local_set(retained_local, retained_optional);
                javan_thread_local_remove(removed_local);

                removed_key = NULL;
                removed_payload = NULL;
                removed_list = NULL;
                removed_map = NULL;
                removed_optional = NULL;
                retained_payload = NULL;
                retained_list = NULL;
                retained_map = NULL;
                retained_optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    usleep(1000);
                }

                void* live_optional = javan_thread_local_get(retained_local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(live_optional), memory_order_release);
                void* live_map = javan_optional_or_else_throw(live_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(live_map), memory_order_release);
                void* live_list = javan_map_get(live_map, retained_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(live_list), memory_order_release);
                void* live_payload = javan_list_get(live_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) live_payload), memory_order_release);
                javan_thread_detach_current();
                return NULL;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                for (int index = 0; index < 16; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    usleep(1000);
                }
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "16384",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=2
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalOverwriteSurvivesRepeatedSafepointGcDuringMutationAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            static void wait_half_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(500);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* local = javan_thread_local_new();
                void* prepared = NULL;
                void* final_key = NULL;
                void* final_payload = NULL;
                void* final_list = NULL;
                void* final_map = NULL;
                void* final_optional = NULL;
                void** worker_roots[] = {
                    (void**) &local,
                    (void**) &prepared,
                    (void**) &final_key,
                    (void**) &final_payload,
                    (void**) &final_list,
                    (void**) &final_map,
                    (void**) &final_optional
                };
                javan_root_frame_push(worker_roots, 7);
                prepared = javan_arraylist_new();
                for (int index = 0; index < 16; index++) {
                    void* loop_key = javan_string_value_of_int(2100 + index);
                    void* loop_payload = javan_string_value_of_int(3100 + index);
                    void* loop_list = javan_arraylist_new();
                    (void) javan_arraylist_add(loop_list, loop_payload);
                    void* loop_map = javan_hashmap_new();
                    (void) javan_map_put(loop_map, loop_key, loop_list);
                    void* loop_optional = javan_optional_of(loop_map);
                    (void) javan_arraylist_add(prepared, loop_optional);
                }
                final_key = javan_string_value_of_int(999);
                final_payload = javan_string_value_of_int(123456);
                final_list = javan_arraylist_new();
                (void) javan_arraylist_add(final_list, final_payload);
                final_map = javan_hashmap_new();
                (void) javan_map_put(final_map, final_key, final_list);
                final_optional = javan_optional_of(final_map);

                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    for (int index = 0; index < 16; index++) {
                        javan_thread_local_set(local, javan_list_get(prepared, index));
                        wait_half_tick();
                    }
                    javan_thread_local_set(local, final_optional);
                    wait_half_tick();
                }

                void* retained_optional = javan_thread_local_get(local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(retained_optional), memory_order_release);
                void* retained_map = javan_optional_or_else_throw(retained_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(retained_map), memory_order_release);
                void* retained_list = javan_map_get(retained_map, final_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(retained_list), memory_order_release);
                void* retained_payload = javan_list_get(retained_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) retained_payload), memory_order_release);
                javan_root_frame_pop(worker_roots);
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                for (int index = 0; index < 64; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_half_tick();
                }
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "32768",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=6
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalRemoveAndSiblingRetentionSurviveRepeatedSafepointGcDuringMutationAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int removed_missing;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            static void wait_half_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(500);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* removed_local = NULL;
                void* retained_local = NULL;
                void* prepared = NULL;
                void* retained_key = NULL;
                void* retained_payload = NULL;
                void* retained_list = NULL;
                void* retained_map = NULL;
                void* retained_optional = NULL;
                void** worker_roots[] = {
                    (void**) &removed_local,
                    (void**) &retained_local,
                    (void**) &prepared,
                    (void**) &retained_key,
                    (void**) &retained_payload,
                    (void**) &retained_list,
                    (void**) &retained_map,
                    (void**) &retained_optional
                };
                javan_root_frame_push(worker_roots, 8);
                removed_local = javan_thread_local_new();
                retained_local = javan_thread_local_new();
                prepared = javan_arraylist_new();
                retained_key = javan_string_value_of_int(6);
                retained_payload = javan_string_value_of_int(66);
                retained_list = javan_arraylist_new();
                (void) javan_arraylist_add(retained_list, retained_payload);
                retained_map = javan_hashmap_new();
                (void) javan_map_put(retained_map, retained_key, retained_list);
                retained_optional = javan_optional_of(retained_map);
                javan_thread_local_set(retained_local, retained_optional);
                for (int index = 0; index < 16; index++) {
                    void* loop_key = javan_string_value_of_int(8000 + index);
                    void* loop_payload = javan_string_value_of_int(9000 + index);
                    void* loop_list = javan_arraylist_new();
                    (void) javan_arraylist_add(loop_list, loop_payload);
                    void* loop_map = javan_hashmap_new();
                    (void) javan_map_put(loop_map, loop_key, loop_list);
                    void* loop_optional = javan_optional_of(loop_map);
                    (void) javan_arraylist_add(prepared, loop_optional);
                }

                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    for (int index = 0; index < 16; index++) {
                        javan_thread_local_set(removed_local, javan_list_get(prepared, index));
                        javan_thread_local_remove(removed_local);
                        wait_half_tick();
                    }
                    wait_half_tick();
                }

                atomic_store_explicit(&removed_missing, javan_thread_local_get(removed_local) == NULL, memory_order_release);
                void* live_optional = javan_thread_local_get(retained_local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(live_optional), memory_order_release);
                void* live_map = javan_optional_or_else_throw(live_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(live_map), memory_order_release);
                void* live_list = javan_map_get(live_map, retained_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(live_list), memory_order_release);
                void* live_payload = javan_list_get(live_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) live_payload), memory_order_release);
                javan_root_frame_pop(worker_roots);
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                for (int index = 0; index < 64; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_half_tick();
                }
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("removed=%d\\n", atomic_load_explicit(&removed_missing, memory_order_acquire));
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "32768",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            removed=1
            present=1
            map=1
            list=1
            payload=2
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeHostThreadThreadLocalMapGrowthSurvivesRepeatedSafepointGcAndDetachesCleanly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif
            #include <stdatomic.h>
            #include <stdio.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int retained_count;
            static atomic_int payload_checksum;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            static void wait_half_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(500);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall child_main(void* argument) {
            #else
            static void* child_main(void* argument) {
            #endif
                (void) argument;
                (void) javan_thread_current();
                void* locals = NULL;
                void* prepared = NULL;
                void** worker_roots[] = {
                    (void**) &locals,
                    (void**) &prepared
                };
                javan_root_frame_push(worker_roots, 2);
                locals = javan_arraylist_new();
                prepared = javan_arraylist_new();
                for (int index = 0; index < 16; index++) {
                    void* local = javan_thread_local_new();
                    (void) javan_arraylist_add(locals, local);

                    void* loop_key = javan_string_value_of_int(6000 + index);
                    void* loop_payload = javan_string_value_of_int(7000 + index);
                    void* loop_list = javan_arraylist_new();
                    (void) javan_arraylist_add(loop_list, loop_payload);
                    void* loop_map = javan_hashmap_new();
                    (void) javan_map_put(loop_map, loop_key, loop_list);
                    void* loop_optional = javan_optional_of(loop_map);
                    (void) javan_arraylist_add(prepared, loop_optional);
                }

                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    for (int index = 0; index < 16; index++) {
                        javan_thread_local_set(
                            javan_list_get(locals, index),
                            javan_list_get(prepared, index)
                        );
                        wait_half_tick();
                    }
                    wait_half_tick();
                }

                int count = 0;
                int checksum = 0;
                for (int index = 0; index < 16; index++) {
                    void* retained_optional = javan_thread_local_get(javan_list_get(locals, index));
                    if (javan_optional_is_present(retained_optional) != 0) {
                        count++;
                    }
                    void* retained_map = javan_optional_or_else_throw(retained_optional);
                    void* retained_list = javan_map_get(retained_map, javan_string_value_of_int(6000 + index));
                    void* retained_payload = javan_list_get(retained_list, 0);
                    checksum += javan_string_length((const char*) retained_payload);
                }
                atomic_store_explicit(&retained_count, count, memory_order_release);
                atomic_store_explicit(&payload_checksum, checksum, memory_order_release);
                javan_root_frame_pop(worker_roots);
                javan_thread_detach_current();
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, child_main, NULL, 0, NULL);
                if (thread == NULL) {
                    return 3;
                }
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, child_main, NULL) != 0) {
                    return 3;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                for (int index = 0; index < 64; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_half_tick();
                }
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                #if defined(_WIN32)
                if (WaitForSingleObject(thread, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(thread);
                    return 4;
                }
                CloseHandle(thread);
                #else
                if (pthread_join(thread, NULL) != 0) {
                    return 4;
                }
                #endif
                javan_gc_collect();
                printf("count=%d\\n", atomic_load_explicit(&retained_count, memory_order_acquire));
                printf("checksum=%d\\n", atomic_load_explicit(&payload_checksum, memory_order_acquire));
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "65536",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            count=16
            checksum=64
            threads=1
            live=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalObjectGraphSurvivesConcurrentGcAndCleansUpAfterJoin() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_present;
            static atomic_int worker_map_size;
            static atomic_int worker_list_size;
            static atomic_int worker_payload_length;
            static atomic_ulong live_during_gc;
            static atomic_ulong threads_during_gc;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* local = target;
                void* key = javan_string_value_of_int(4);
                void* payload = javan_string_value_of_int(44);
                void* list = javan_arraylist_new();
                (void) javan_arraylist_add(list, payload);
                void* map = javan_hashmap_new();
                (void) javan_map_put(map, key, list);
                void* optional = javan_optional_of(map);
                javan_thread_local_set(local, optional);
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }
                void* retained_optional = javan_thread_local_get(local);
                atomic_store_explicit(&worker_present, javan_optional_is_present(retained_optional), memory_order_release);
                void* retained_map = javan_optional_or_else_throw(retained_optional);
                atomic_store_explicit(&worker_map_size, javan_map_size(retained_map), memory_order_release);
                void* retained_list = javan_map_get(retained_map, key);
                atomic_store_explicit(&worker_list_size, javan_list_size(retained_list), memory_order_release);
                void* retained_payload = javan_list_get(retained_list, 0);
                atomic_store_explicit(&worker_payload_length, javan_string_length((const char*) retained_payload), memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* local = javan_thread_local_new();
                void** roots[] = {
                    (void**) &worker,
                    (void**) &local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_set_target(worker, local);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&live_during_gc, javan_heap_live_allocations(), memory_order_release);
                atomic_store_explicit(&threads_during_gc, javan_heap_thread_objects(), memory_order_release);
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                worker = NULL;
                local = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&worker_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&worker_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&worker_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&worker_payload_length, memory_order_acquire));
                printf("threads-live=%lu\\n", atomic_load_explicit(&threads_during_gc, memory_order_acquire));
                printf("live-live=%lu\\n", atomic_load_explicit(&live_during_gc, memory_order_acquire));
                printf("threads-after=%lu\\n", javan_heap_thread_objects());
                printf("live-after=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=2
            threads-live=2
            live-live=20
            threads-after=1
            live-after=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalOverwriteCollectsPreviousGraphDuringConcurrentGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_present;
            static atomic_int worker_map_size;
            static atomic_int worker_list_size;
            static atomic_int worker_payload_length;
            static atomic_ulong live_during_gc;
            static atomic_ulong threads_during_gc;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* local = target;
                void* old_key = javan_string_value_of_int(1);
                void* old_payload = javan_string_value_of_int(11);
                void* old_list = javan_arraylist_new();
                (void) javan_arraylist_add(old_list, old_payload);
                void* old_map = javan_hashmap_new();
                (void) javan_map_put(old_map, old_key, old_list);
                void* old_optional = javan_optional_of(old_map);
                javan_thread_local_set(local, old_optional);

                void* new_key = javan_string_value_of_int(2);
                void* new_payload = javan_string_value_of_int(22);
                void* new_list = javan_arraylist_new();
                (void) javan_arraylist_add(new_list, new_payload);
                void* new_map = javan_hashmap_new();
                (void) javan_map_put(new_map, new_key, new_list);
                void* new_optional = javan_optional_of(new_map);
                javan_thread_local_set(local, new_optional);

                old_key = NULL;
                old_payload = NULL;
                old_list = NULL;
                old_map = NULL;
                old_optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }
                void* retained_optional = javan_thread_local_get(local);
                atomic_store_explicit(&worker_present, javan_optional_is_present(retained_optional), memory_order_release);
                void* retained_map = javan_optional_or_else_throw(retained_optional);
                atomic_store_explicit(&worker_map_size, javan_map_size(retained_map), memory_order_release);
                void* retained_list = javan_map_get(retained_map, new_key);
                atomic_store_explicit(&worker_list_size, javan_list_size(retained_list), memory_order_release);
                void* retained_payload = javan_list_get(retained_list, 0);
                atomic_store_explicit(&worker_payload_length, javan_string_length((const char*) retained_payload), memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* local = javan_thread_local_new();
                void** roots[] = {
                    (void**) &worker,
                    (void**) &local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_set_target(worker, local);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&live_during_gc, javan_heap_live_allocations(), memory_order_release);
                atomic_store_explicit(&threads_during_gc, javan_heap_thread_objects(), memory_order_release);
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                worker = NULL;
                local = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&worker_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&worker_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&worker_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&worker_payload_length, memory_order_acquire));
                printf("threads-live=%lu\\n", atomic_load_explicit(&threads_during_gc, memory_order_acquire));
                printf("live-live=%lu\\n", atomic_load_explicit(&live_during_gc, memory_order_acquire));
                printf("threads-after=%lu\\n", javan_heap_thread_objects());
                printf("live-after=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=2
            threads-live=2
            live-live=20
            threads-after=1
            live-after=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalRemoveCollectsRemovedGraphDuringConcurrentGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_missing;
            static atomic_ulong live_during_gc;
            static atomic_ulong threads_during_gc;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* local = target;
                void* key = javan_string_value_of_int(3);
                void* payload = javan_string_value_of_int(33);
                void* list = javan_arraylist_new();
                (void) javan_arraylist_add(list, payload);
                void* map = javan_hashmap_new();
                (void) javan_map_put(map, key, list);
                void* optional = javan_optional_of(map);
                javan_thread_local_set(local, optional);
                javan_thread_local_remove(local);
                key = NULL;
                payload = NULL;
                list = NULL;
                map = NULL;
                optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }
                atomic_store_explicit(&worker_missing, javan_thread_local_get(local) == NULL, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* local = javan_thread_local_new();
                void** roots[] = {
                    (void**) &worker,
                    (void**) &local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_set_target(worker, local);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&live_during_gc, javan_heap_live_allocations(), memory_order_release);
                atomic_store_explicit(&threads_during_gc, javan_heap_thread_objects(), memory_order_release);
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                worker = NULL;
                local = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("missing=%d\\n", atomic_load_explicit(&worker_missing, memory_order_acquire));
                printf("threads-live=%lu\\n", atomic_load_explicit(&threads_during_gc, memory_order_acquire));
                printf("live-live=%lu\\n", atomic_load_explicit(&live_during_gc, memory_order_acquire));
                printf("threads-after=%lu\\n", javan_heap_thread_objects());
                printf("live-after=%lu\\n", javan_heap_live_allocations());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            missing=1
            threads-live=2
            live-live=10
            threads-after=1
            live-after=2
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalSiblingRemoveKeepsOtherGraphAliveDuringConcurrentGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int removed_missing;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* locals = target;
                void* removed_local = javan_list_get(locals, 0);
                void* retained_local = javan_list_get(locals, 1);

                void* removed_key = javan_string_value_of_int(5);
                void* removed_payload = javan_string_value_of_int(55);
                void* removed_list = javan_arraylist_new();
                (void) javan_arraylist_add(removed_list, removed_payload);
                void* removed_map = javan_hashmap_new();
                (void) javan_map_put(removed_map, removed_key, removed_list);
                void* removed_optional = javan_optional_of(removed_map);
                javan_thread_local_set(removed_local, removed_optional);

                void* retained_key = javan_string_value_of_int(6);
                void* retained_payload = javan_string_value_of_int(66);
                void* retained_list = javan_arraylist_new();
                (void) javan_arraylist_add(retained_list, retained_payload);
                void* retained_map = javan_hashmap_new();
                (void) javan_map_put(retained_map, retained_key, retained_list);
                void* retained_optional = javan_optional_of(retained_map);
                javan_thread_local_set(retained_local, retained_optional);
                javan_thread_local_remove(removed_local);

                removed_key = NULL;
                removed_payload = NULL;
                removed_list = NULL;
                removed_map = NULL;
                removed_optional = NULL;
                retained_payload = NULL;
                retained_list = NULL;
                retained_map = NULL;
                retained_optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }

                atomic_store_explicit(&removed_missing, javan_thread_local_get(removed_local) == NULL, memory_order_release);
                void* live_optional = javan_thread_local_get(retained_local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(live_optional), memory_order_release);
                void* live_map = javan_optional_or_else_throw(live_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(live_map), memory_order_release);
                void* live_list = javan_map_get(live_map, retained_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(live_list), memory_order_release);
                void* live_payload = javan_list_get(live_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) live_payload), memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* removed_local = javan_thread_local_new();
                void* retained_local = javan_thread_local_new();
                void* locals = javan_arraylist_new();
                (void) javan_arraylist_add(locals, removed_local);
                (void) javan_arraylist_add(locals, retained_local);
                void** roots[] = {
                    (void**) &worker,
                    (void**) &removed_local,
                    (void**) &retained_local,
                    (void**) &locals
                };
                javan_root_frame_push(roots, 4);
                javan_thread_set_target(worker, locals);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                worker = NULL;
                removed_local = NULL;
                retained_local = NULL;
                locals = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("removed=%d\\n", atomic_load_explicit(&removed_missing, memory_order_acquire));
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("clean=%d\\n", javan_heap_live_allocations() == 2);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            removed=1
            present=1
            map=1
            list=1
            payload=2
            clean=1
            """
        );
    }

    @Test
    void runtimeThreadLifecycleInventoryTracksCurrentThreadState() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                printf(
                    "roots=%lu\\nthreads=%lu\\nstarted=%lu\\ncompleted=%lu\\nactive=%lu\\ntargets=%lu\\ncurrent=%d\\n",
                    javan_heap_registered_thread_roots(),
                    javan_heap_thread_objects(),
                    javan_heap_started_threads(),
                    javan_heap_completed_threads(),
                    javan_heap_active_threads(),
                    javan_heap_threads_with_target(),
                    javan_heap_current_thread_root_present()
                );
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            roots=1
            threads=1
            started=1
            completed=0
            active=0
            targets=0
            current=1
            """
        );
    }

    @Test
    void runtimeThreadTargetSurvivesPreStartCollectionThroughWorkerField() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* worker = javan_thread_new();
                void* target = javan_thread_new();
                void** roots[] = {
                    (void**) &worker
                };
                javan_root_frame_push(roots, 1);
                javan_thread_set_target(worker, target);
                target = NULL;
                javan_gc_collect();
                printf("targets=%lu\\n", javan_heap_threads_with_target());
                printf("alive=%d\\n", javan_thread_is_alive(worker));
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            targets=1
            alive=0
            """
        );
    }

    @Test
    void runtimeThreadLifecycleInventoryDropsFinishedNonCurrentThreadObjectsAfterCollection() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            void javan_thread_run_target(void* target) {
                (void) target;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* worker = javan_thread_new();
                void* target = javan_thread_new();
                javan_thread_set_target(worker, target);
                javan_thread_start(worker);
                javan_thread_join(worker);
                worker = NULL;
                target = NULL;
                javan_gc_collect();
                printf(
                    "roots=%lu\\nthreads=%lu\\nstarted=%lu\\ncompleted=%lu\\nactive=%lu\\ntargets=%lu\\ncurrent=%d\\n",
                    javan_heap_registered_thread_roots(),
                    javan_heap_thread_objects(),
                    javan_heap_started_threads(),
                    javan_heap_completed_threads(),
                    javan_heap_active_threads(),
                    javan_heap_threads_with_target(),
                    javan_heap_current_thread_root_present()
                );
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            roots=1
            threads=1
            started=1
            completed=0
            active=0
            targets=0
            current=1
            """
        );
    }

    @Test
    void runtimeCompletedThreadDoesNotRetainTargetAfterCollectionWhenWorkerStaysReachable() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            void javan_thread_run_target(void* target) {
                (void) target;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* worker = javan_thread_new();
                void* target = javan_thread_new();
                void** roots[] = {
                    (void**) &worker
                };
                javan_root_frame_push(roots, 1);
                javan_thread_set_target(worker, target);
                javan_thread_start(worker);
                javan_thread_join(worker);
                target = NULL;
                javan_gc_collect();
                printf("targets=%lu\\n", javan_heap_threads_with_target());
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("targets=0\n");
    }

    @Test
    void runtimeCompletedReachableWorkerClearsThreadLocalStorageOnCompletion() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            void javan_thread_run_target(void* target) {
                void* retained = javan_thread_new();
                javan_thread_local_set(target, retained);
                retained = NULL;
                javan_gc_collect();
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* local = javan_thread_local_new();
                void** roots[] = {
                    (void**) &worker,
                    (void**) &local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_set_target(worker, local);
                javan_thread_start(worker);
                javan_thread_join(worker);
                javan_gc_collect();
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            threads=2
            roots=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalSiblingRemoveKeepsOtherGraphAliveDuringRepeatedSafepointGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int removed_missing;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* locals = target;
                void* removed_local = javan_list_get(locals, 0);
                void* retained_local = javan_list_get(locals, 1);

                void* removed_key = javan_string_value_of_int(5);
                void* removed_payload = javan_string_value_of_int(55);
                void* removed_list = javan_arraylist_new();
                (void) javan_arraylist_add(removed_list, removed_payload);
                void* removed_map = javan_hashmap_new();
                (void) javan_map_put(removed_map, removed_key, removed_list);
                void* removed_optional = javan_optional_of(removed_map);
                javan_thread_local_set(removed_local, removed_optional);

                void* retained_key = javan_string_value_of_int(6);
                void* retained_payload = javan_string_value_of_int(66);
                void* retained_list = javan_arraylist_new();
                (void) javan_arraylist_add(retained_list, retained_payload);
                void* retained_map = javan_hashmap_new();
                (void) javan_map_put(retained_map, retained_key, retained_list);
                void* retained_optional = javan_optional_of(retained_map);
                javan_thread_local_set(retained_local, retained_optional);
                javan_thread_local_remove(removed_local);

                removed_key = NULL;
                removed_payload = NULL;
                removed_list = NULL;
                removed_map = NULL;
                removed_optional = NULL;
                retained_payload = NULL;
                retained_list = NULL;
                retained_map = NULL;
                retained_optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_tick();
                }

                atomic_store_explicit(&removed_missing, javan_thread_local_get(removed_local) == NULL, memory_order_release);
                void* live_optional = javan_thread_local_get(retained_local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(live_optional), memory_order_release);
                void* live_map = javan_optional_or_else_throw(live_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(live_map), memory_order_release);
                void* live_list = javan_map_get(live_map, retained_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(live_list), memory_order_release);
                void* live_payload = javan_list_get(live_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) live_payload), memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* removed_local = javan_thread_local_new();
                void* retained_local = javan_thread_local_new();
                void* locals = javan_arraylist_new();
                (void) javan_arraylist_add(locals, removed_local);
                (void) javan_arraylist_add(locals, retained_local);
                void** roots[] = {
                    (void**) &worker,
                    (void**) &removed_local,
                    (void**) &retained_local,
                    (void**) &locals
                };
                javan_root_frame_push(roots, 4);
                javan_thread_set_target(worker, locals);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                for (int index = 0; index < 64; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_tick();
                }
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                worker = NULL;
                removed_local = NULL;
                retained_local = NULL;
                locals = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("removed=%d\\n", atomic_load_explicit(&removed_missing, memory_order_acquire));
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("clean=%d\\n", javan_heap_live_allocations() == 2);
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "16384",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            removed=1
            present=1
            map=1
            list=1
            payload=2
            clean=1
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalOverwriteSurvivesRepeatedParentSafepointGcDuringMutation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int worker_done;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            static void wait_half_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(500);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* local = target;
                void* prepared = NULL;
                void* final_key = NULL;
                void* final_payload = NULL;
                void* final_list = NULL;
                void* final_map = NULL;
                void* final_optional = NULL;
                void** worker_roots[] = {
                    (void**) &prepared,
                    (void**) &final_key,
                    (void**) &final_payload,
                    (void**) &final_list,
                    (void**) &final_map,
                    (void**) &final_optional
                };
                javan_root_frame_push(worker_roots, 6);
                prepared = javan_arraylist_new();
                for (int index = 0; index < 64; index++) {
                    void* loop_key = javan_string_value_of_int(2000 + index);
                    void* loop_payload = javan_string_value_of_int(3000 + index);
                    void* loop_list = javan_arraylist_new();
                    (void) javan_arraylist_add(loop_list, loop_payload);
                    void* loop_map = javan_hashmap_new();
                    (void) javan_map_put(loop_map, loop_key, loop_list);
                    void* loop_optional = javan_optional_of(loop_map);
                    (void) javan_arraylist_add(prepared, loop_optional);
                }
                final_key = javan_string_value_of_int(999);
                final_payload = javan_string_value_of_int(123456);
                final_list = javan_arraylist_new();
                (void) javan_arraylist_add(final_list, final_payload);
                final_map = javan_hashmap_new();
                (void) javan_map_put(final_map, final_key, final_list);
                final_optional = javan_optional_of(final_map);

                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                for (int index = 0; index < 16; index++) {
                    javan_thread_local_set(local, javan_list_get(prepared, index));
                    wait_tick();
                }
                javan_thread_local_set(local, final_optional);

                void* retained_optional = javan_thread_local_get(local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(retained_optional), memory_order_release);
                void* retained_map = javan_optional_or_else_throw(retained_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(retained_map), memory_order_release);
                void* retained_list = javan_map_get(retained_map, final_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(retained_list), memory_order_release);
                void* retained_payload = javan_list_get(retained_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) retained_payload), memory_order_release);
                javan_root_frame_pop(worker_roots);
                prepared = NULL;
                final_key = NULL;
                final_payload = NULL;
                final_list = NULL;
                final_map = NULL;
                final_optional = NULL;
                atomic_store_explicit(&worker_done, 1, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* local = javan_thread_local_new();
                void** roots[] = {
                    (void**) &worker,
                    (void**) &local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_set_target(worker, local);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                for (int index = 0; index < 64; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_half_tick();
                    if (atomic_load_explicit(&worker_done, memory_order_acquire) != 0) {
                        break;
                    }
                }
                javan_thread_join(worker);
                worker = NULL;
                local = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("clean=%d\\n", javan_heap_live_allocations() == 2);
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "32768",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            present=1
            map=1
            list=1
            payload=6
            clean=1
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerThreadLocalRemoveAndSiblingRetentionSurviveRepeatedParentSafepointGcDuringMutation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int worker_done;
            static atomic_int removed_missing;
            static atomic_int retained_present;
            static atomic_int retained_map_size;
            static atomic_int retained_list_size;
            static atomic_int retained_payload_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            static void wait_half_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(500);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* locals = target;
                void* removed_local = javan_list_get(locals, 0);
                void* retained_local = javan_list_get(locals, 1);
                void* prepared = NULL;

                void* retained_key = NULL;
                void* retained_payload = NULL;
                void* retained_list = NULL;
                void* retained_map = NULL;
                void* retained_optional = NULL;
                void** retained_roots[] = {
                    (void**) &prepared,
                    (void**) &retained_key,
                    (void**) &retained_payload,
                    (void**) &retained_list,
                    (void**) &retained_map,
                    (void**) &retained_optional
                };
                javan_root_frame_push(retained_roots, 6);
                prepared = javan_arraylist_new();
                retained_key = javan_string_value_of_int(6);
                retained_payload = javan_string_value_of_int(66);
                retained_list = javan_arraylist_new();
                (void) javan_arraylist_add(retained_list, retained_payload);
                retained_map = javan_hashmap_new();
                (void) javan_map_put(retained_map, retained_key, retained_list);
                retained_optional = javan_optional_of(retained_map);
                javan_thread_local_set(retained_local, retained_optional);

                for (int index = 0; index < 16; index++) {
                    void* loop_key = javan_string_value_of_int(4000 + index);
                    void* loop_payload = javan_string_value_of_int(5000 + index);
                    void* loop_list = javan_arraylist_new();
                    (void) javan_arraylist_add(loop_list, loop_payload);
                    void* loop_map = javan_hashmap_new();
                    (void) javan_map_put(loop_map, loop_key, loop_list);
                    void* loop_optional = javan_optional_of(loop_map);
                    (void) javan_arraylist_add(prepared, loop_optional);
                }

                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                for (int index = 0; index < 16; index++) {
                    javan_thread_local_set(removed_local, javan_list_get(prepared, index));
                    javan_thread_local_remove(removed_local);
                    wait_tick();
                }
                atomic_store_explicit(&removed_missing, javan_thread_local_get(removed_local) == NULL, memory_order_release);
                void* live_optional = javan_thread_local_get(retained_local);
                atomic_store_explicit(&retained_present, javan_optional_is_present(live_optional), memory_order_release);
                void* live_map = javan_optional_or_else_throw(live_optional);
                atomic_store_explicit(&retained_map_size, javan_map_size(live_map), memory_order_release);
                void* live_list = javan_map_get(live_map, retained_key);
                atomic_store_explicit(&retained_list_size, javan_list_size(live_list), memory_order_release);
                void* live_payload = javan_list_get(live_list, 0);
                atomic_store_explicit(&retained_payload_length, javan_string_length((const char*) live_payload), memory_order_release);
                javan_root_frame_pop(retained_roots);
                retained_key = NULL;
                retained_payload = NULL;
                retained_list = NULL;
                retained_map = NULL;
                retained_optional = NULL;
                atomic_store_explicit(&worker_done, 1, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* removed_local = javan_thread_local_new();
                void* retained_local = javan_thread_local_new();
                void* locals = javan_arraylist_new();
                (void) javan_arraylist_add(locals, removed_local);
                (void) javan_arraylist_add(locals, retained_local);
                void** roots[] = {
                    (void**) &worker,
                    (void**) &removed_local,
                    (void**) &retained_local,
                    (void**) &locals
                };
                javan_root_frame_push(roots, 4);
                javan_thread_set_target(worker, locals);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                for (int index = 0; index < 64; index++) {
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    wait_half_tick();
                    if (atomic_load_explicit(&worker_done, memory_order_acquire) != 0) {
                        break;
                    }
                }
                javan_thread_join(worker);
                worker = NULL;
                removed_local = NULL;
                retained_local = NULL;
                locals = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("removed=%d\\n", atomic_load_explicit(&removed_missing, memory_order_acquire));
                printf("present=%d\\n", atomic_load_explicit(&retained_present, memory_order_acquire));
                printf("map=%d\\n", atomic_load_explicit(&retained_map_size, memory_order_acquire));
                printf("list=%d\\n", atomic_load_explicit(&retained_list_size, memory_order_acquire));
                printf("payload=%d\\n", atomic_load_explicit(&retained_payload_length, memory_order_acquire));
                printf("clean=%d\\n", javan_heap_live_allocations() == 2);
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "32768",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            removed=1
            present=1
            map=1
            list=1
            payload=2
            clean=1
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeSharedThreadLocalKeyRemainsThreadIsolatedAcrossWorkerRemoveAndConcurrentGc() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #if defined(_WIN32)
            #include <windows.h>
            #else
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_missing;
            static atomic_int parent_before_present;
            static atomic_int parent_before_map_size;
            static atomic_int parent_before_list_size;
            static atomic_int parent_before_payload_length;
            static atomic_int parent_after_present;
            static atomic_int parent_after_map_size;
            static atomic_int parent_after_list_size;
            static atomic_int parent_after_payload_length;
            static atomic_int parent_cleared;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            void javan_thread_run_target(void* target) {
                void* shared_local = target;
                void* worker_key = javan_string_value_of_int(8);
                void* worker_payload = javan_string_value_of_int(88);
                void* worker_list = javan_arraylist_new();
                (void) javan_arraylist_add(worker_list, worker_payload);
                void* worker_map = javan_hashmap_new();
                (void) javan_map_put(worker_map, worker_key, worker_list);
                void* worker_optional = javan_optional_of(worker_map);
                javan_thread_local_set(shared_local, worker_optional);
                worker_key = NULL;
                worker_payload = NULL;
                worker_list = NULL;
                worker_map = NULL;
                worker_optional = NULL;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_thread_local_remove(shared_local);
                atomic_store_explicit(&worker_missing, javan_thread_local_get(shared_local) == NULL, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* shared_local = javan_thread_local_new();
                void* parent_key = javan_string_value_of_int(777);
                void* parent_payload = javan_string_value_of_int(777);
                void* parent_list = javan_arraylist_new();
                (void) javan_arraylist_add(parent_list, parent_payload);
                void* parent_map = javan_hashmap_new();
                (void) javan_map_put(parent_map, parent_key, parent_list);
                void* parent_optional = javan_optional_of(parent_map);
                void** roots[] = {
                    (void**) &worker,
                    (void**) &shared_local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_local_set(shared_local, parent_optional);
                javan_thread_set_target(worker, shared_local);
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                javan_gc_collect();

                void* parent_live_optional = javan_thread_local_get(shared_local);
                atomic_store_explicit(&parent_before_present, javan_optional_is_present(parent_live_optional), memory_order_release);
                void* parent_live_map = javan_optional_or_else_throw(parent_live_optional);
                atomic_store_explicit(&parent_before_map_size, javan_map_size(parent_live_map), memory_order_release);
                void* parent_live_list = javan_map_get(parent_live_map, parent_key);
                atomic_store_explicit(&parent_before_list_size, javan_list_size(parent_live_list), memory_order_release);
                void* parent_live_payload = javan_list_get(parent_live_list, 0);
                atomic_store_explicit(&parent_before_payload_length, javan_string_length((const char*) parent_live_payload), memory_order_release);

                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);

                parent_live_optional = javan_thread_local_get(shared_local);
                atomic_store_explicit(&parent_after_present, javan_optional_is_present(parent_live_optional), memory_order_release);
                parent_live_map = javan_optional_or_else_throw(parent_live_optional);
                atomic_store_explicit(&parent_after_map_size, javan_map_size(parent_live_map), memory_order_release);
                parent_live_list = javan_map_get(parent_live_map, parent_key);
                atomic_store_explicit(&parent_after_list_size, javan_list_size(parent_live_list), memory_order_release);
                parent_live_payload = javan_list_get(parent_live_list, 0);
                atomic_store_explicit(&parent_after_payload_length, javan_string_length((const char*) parent_live_payload), memory_order_release);

                javan_thread_local_remove(shared_local);
                atomic_store_explicit(&parent_cleared, javan_thread_local_get(shared_local) == NULL, memory_order_release);
                worker = NULL;
                shared_local = NULL;
                parent_key = NULL;
                parent_payload = NULL;
                parent_list = NULL;
                parent_map = NULL;
                parent_optional = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("worker-missing=%d\\n", atomic_load_explicit(&worker_missing, memory_order_acquire));
                printf("before-present=%d\\n", atomic_load_explicit(&parent_before_present, memory_order_acquire));
                printf("before-map=%d\\n", atomic_load_explicit(&parent_before_map_size, memory_order_acquire));
                printf("before-list=%d\\n", atomic_load_explicit(&parent_before_list_size, memory_order_acquire));
                printf("before-payload=%d\\n", atomic_load_explicit(&parent_before_payload_length, memory_order_acquire));
                printf("after-present=%d\\n", atomic_load_explicit(&parent_after_present, memory_order_acquire));
                printf("after-map=%d\\n", atomic_load_explicit(&parent_after_map_size, memory_order_acquire));
                printf("after-list=%d\\n", atomic_load_explicit(&parent_after_list_size, memory_order_acquire));
                printf("after-payload=%d\\n", atomic_load_explicit(&parent_after_payload_length, memory_order_acquire));
                printf("parent-cleared=%d\\n", atomic_load_explicit(&parent_cleared, memory_order_acquire));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            worker-missing=1
            before-present=1
            before-map=1
            before-list=1
            before-payload=3
            after-present=1
            after-map=1
            after-list=1
            after-payload=3
            parent-cleared=1
            roots=1
            current=1
            """
        );
    }

    @Test
    void runtimeCompletedReachableWorkerClearsNestedThreadLocalObjectGraphOnCompletion() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            void javan_thread_run_target(void* target) {
                void* local = target;
                void* key = javan_string_value_of_int(7);
                void* payload = javan_string_value_of_int(77);
                void* list = javan_arraylist_new();
                (void) javan_arraylist_add(list, payload);
                void* map = javan_hashmap_new();
                (void) javan_map_put(map, key, list);
                void* optional = javan_optional_of(map);
                javan_thread_local_set(local, optional);
                key = NULL;
                payload = NULL;
                list = NULL;
                map = NULL;
                optional = NULL;
                javan_gc_collect();
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* local = javan_thread_local_new();
                void** roots[] = {
                    (void**) &worker,
                    (void**) &local
                };
                javan_root_frame_push(roots, 2);
                javan_thread_set_target(worker, local);
                javan_thread_start(worker);
                javan_thread_join(worker);
                worker = NULL;
                local = NULL;
                javan_root_frame_pop(roots);
                javan_gc_collect();
                printf("clean=%d\\n", javan_heap_live_allocations() == 2);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("clean=1\n");
    }

    @Test
    void runtimeDetachedReachableCurrentThreadClearsThreadLocalStorageOnDetach() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void* current_root = NULL;
            static void* local_root = NULL;

            int main(void) {
                void** static_roots[] = {
                    (void**) &current_root,
                    (void**) &local_root
                };
                javan_register_static_roots(static_roots, 2);
                current_root = javan_thread_current();
                local_root = javan_thread_local_new();
                javan_thread_local_set(local_root, javan_thread_new());
                javan_thread_detach_current();
                javan_gc_collect();
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            threads=1
            roots=0
            """
        );
    }

    @Test
    void runtimeDetachedReachableCurrentThreadClearsNestedThreadLocalObjectGraphOnDetach() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void* current_root = NULL;
            static void* local_root = NULL;

            int main(void) {
                void** static_roots[] = {
                    (void**) &current_root,
                    (void**) &local_root
                };
                javan_register_static_roots(static_roots, 2);
                current_root = javan_thread_current();
                local_root = javan_thread_local_new();
                void* key = javan_string_value_of_int(8);
                void* payload = javan_string_value_of_int(88);
                void* list = javan_arraylist_new();
                (void) javan_arraylist_add(list, payload);
                void* map = javan_hashmap_new();
                (void) javan_map_put(map, key, list);
                void* optional = javan_optional_of(map);
                javan_thread_local_set(local_root, optional);
                key = NULL;
                payload = NULL;
                list = NULL;
                map = NULL;
                optional = NULL;
                javan_thread_detach_current();
                javan_gc_collect();
                printf("after=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("after=3\n");
    }

    @Test
    void runtimeCurrentThreadParkConsumesExistingPermitImmediately() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* current = javan_thread_current();
                javan_thread_unpark(current);
                javan_thread_park();
                printf("ok\\n");
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("ok\n");
    }

    @Test
    void runtimeCurrentThreadParkNanosConsumesExistingPermitImmediately() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* current = javan_thread_current();
                javan_thread_unpark(current);
                javan_thread_park_nanos(1000000LL);
                printf("ok\\n");
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("ok\n");
    }

    @Test
    void runtimeCurrentThreadParkUntilPastDeadlineReturnsImmediately() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                javan_thread_current();
                javan_thread_park_until(javan_system_current_time_millis() - 1LL);
                printf("ok\\n");
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("ok\n");
    }

    @Test
    void runtimeCurrentThreadParkReturnsWithoutClearingInterruptState() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* current = javan_thread_current();
                javan_thread_interrupt(current);
                javan_thread_park();
                printf("interrupted=%d\\n", javan_thread_is_interrupted(current));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("interrupted=1\n");
    }

    @Test
    void runtimeThreadLifecycleCountersShowCompletedWorkerBeforeCollection() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            void javan_thread_run_target(void* target) {
                (void) target;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* target = javan_thread_new();
                void** roots[] = {
                    (void**) &worker
                };
                javan_root_frame_push(roots, 1);
                javan_thread_set_target(worker, target);
                javan_thread_start(worker);
                javan_thread_join(worker);
                printf("alive=%d\\n", javan_thread_is_alive(worker));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("threads=%lu\\n", javan_heap_thread_objects());
                printf("started=%lu\\n", javan_heap_started_threads());
                printf("completed=%lu\\n", javan_heap_completed_threads());
                printf("active=%lu\\n", javan_heap_active_threads());
                printf("targets=%lu\\n", javan_heap_threads_with_target());
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            alive=0
            roots=1
            threads=3
            started=2
            completed=1
            active=0
            targets=0
            """
        );
    }

    @Test
    void runtimeThreadDuplicateStartFailsClearly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <unistd.h>

            void javan_thread_run_target(void* target) {
                (void) target;
                usleep(200000);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* worker = javan_thread_new();
                void* target_thread = javan_thread_new();
                javan_thread_set_target(worker, target_thread);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                    javan_thread_join(worker);
                    return 0;
                }
                javan_thread_start(worker);
                javan_thread_start(worker);
                return 2;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            Thread.start duplicate is not supported yet
            roots=2
            """
        );
    }

    @Test
    void runtimeThreadJoinCurrentThreadFailsClearly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* current = javan_thread_current();
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                    return 0;
                }
                javan_thread_join(current);
                return 2;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            Thread.join on current thread is not supported yet
            roots=1
            """
        );
    }

    @Test
    void runtimeThreadStartKeepsWorkerAliveDuringTargetCollection() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void* worker_ref = NULL;

            void javan_thread_run_target(void* target) {
                printf("alive=%d\\n", javan_thread_is_alive(worker_ref));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("targets=%lu\\n", javan_heap_threads_with_target());
                javan_gc_collect();
                printf("alive_after_gc=%d\\n", javan_thread_is_alive(worker_ref));
                (void) target;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                worker_ref = javan_thread_new();
                void* target = javan_thread_new();
                javan_thread_set_target(worker_ref, target);
                javan_thread_start(worker_ref);
                javan_thread_join(worker_ref);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            alive=1
            roots=2
            targets=1
            alive_after_gc=1
            """
        );
    }

    @Test
    void runtimeThreadRootRegistryTracksInlineWorkerLifetime() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            void javan_thread_run_target(void* target) {
                (void) target;
                printf("during=%lu\\n", javan_heap_registered_thread_roots());
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                void* target = javan_thread_new();
                javan_thread_set_target(worker, target);
                printf("before=%lu\\n", javan_heap_registered_thread_roots());
                javan_thread_start(worker);
                javan_thread_join(worker);
                printf("after=%lu\\n", javan_heap_registered_thread_roots());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            before=1
            during=2
            after=1
            """
        );
    }

    @Test
    void runtimeThreadStartReturnsBeforeWorkerCompletionAndJoinWaitsExplicitly() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #include <unistd.h>

            static atomic_int allow_worker_report;
            static atomic_int release_worker;
            static void* worker_ref = NULL;

            void javan_thread_run_target(void* target) {
                (void) target;
                while (atomic_load_explicit(&allow_worker_report, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                printf("worker-current=%d\\n", javan_thread_current() == worker_ref);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    usleep(1000);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                worker_ref = javan_thread_new();
                void* target = javan_thread_new();
                javan_thread_set_target(worker_ref, target);
                javan_thread_start(worker_ref);
                printf("alive-after-start=%d\\n", javan_thread_is_alive(worker_ref));
                printf("roots-after-start=%lu\\n", javan_heap_registered_thread_roots());
                printf("active-after-start=%lu\\n", javan_heap_active_threads());
                atomic_store_explicit(&allow_worker_report, 1, memory_order_release);
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker_ref);
                printf("alive-after-join=%d\\n", javan_thread_is_alive(worker_ref));
                printf("roots-after-join=%lu\\n", javan_heap_registered_thread_roots());
                printf("active-after-join=%lu\\n", javan_heap_active_threads());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            alive-after-start=1
            roots-after-start=2
            active-after-start=1
            worker-current=1
            alive-after-join=0
            roots-after-join=1
            active-after-join=0
            """
        );
    }

    @Test
    void runtimeStartedWorkerAndParentCanCollectConcurrentlyWithoutLosingThreadRoots() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #include <unistd.h>

            static atomic_int worker_ready;
            static atomic_int worker_done;
            static atomic_int worker_fail;
            static atomic_int parent_fail;
            static void* worker_ref = NULL;

            void javan_thread_run_target(void* target) {
                (void) target;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                for (int index = 0; index < 64; index++) {
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&worker_fail, 1, memory_order_release);
                        break;
                    }
                    (void) javan_thread_new();
                    javan_gc_collect();
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&worker_fail, 2, memory_order_release);
                        break;
                    }
                    usleep(1000);
                }
                atomic_store_explicit(&worker_done, 1, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                worker_ref = javan_thread_new();
                void* target = javan_thread_new();
                javan_thread_set_target(worker_ref, target);
                javan_thread_start(worker_ref);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                for (int index = 0; index < 64; index++) {
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&parent_fail, 1, memory_order_release);
                        break;
                    }
                    (void) javan_thread_new();
                    javan_gc_collect();
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&parent_fail, 2, memory_order_release);
                        break;
                    }
                    if (atomic_load_explicit(&worker_done, memory_order_acquire) != 0) {
                        break;
                    }
                }
                javan_thread_join(worker_ref);
                target = NULL;
                worker_ref = NULL;
                javan_gc_collect();
                printf("worker=%d\\n", atomic_load_explicit(&worker_fail, memory_order_acquire));
                printf("parent=%d\\n", atomic_load_explicit(&parent_fail, memory_order_acquire));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("active=%lu\\n", javan_heap_active_threads());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            worker=0
            parent=0
            roots=1
            active=0
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerAndParentSurviveSafepointTriggeredGcConcurrently() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #include <unistd.h>

            static atomic_int worker_ready;
            static atomic_int worker_done;
            static atomic_int worker_fail;
            static atomic_int parent_fail;
            static void* worker_ref = NULL;

            void javan_thread_run_target(void* target) {
                (void) target;
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                for (int index = 0; index < 64; index++) {
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&worker_fail, 1, memory_order_release);
                        break;
                    }
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&worker_fail, 2, memory_order_release);
                        break;
                    }
                    usleep(1000);
                }
                atomic_store_explicit(&worker_done, 1, memory_order_release);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                worker_ref = javan_thread_new();
                void* target = javan_thread_new();
                javan_thread_set_target(worker_ref, target);
                javan_thread_start(worker_ref);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                for (int index = 0; index < 64; index++) {
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&parent_fail, 1, memory_order_release);
                        break;
                    }
                    (void) javan_thread_new();
                    javan_gc_safe_point();
                    if (!javan_heap_current_thread_root_present()) {
                        atomic_store_explicit(&parent_fail, 2, memory_order_release);
                        break;
                    }
                    if (atomic_load_explicit(&worker_done, memory_order_acquire) != 0) {
                        break;
                    }
                }
                javan_thread_join(worker_ref);
                target = NULL;
                worker_ref = NULL;
                javan_gc_collect();
                printf("worker=%d\\n", atomic_load_explicit(&worker_fail, memory_order_acquire));
                printf("parent=%d\\n", atomic_load_explicit(&parent_fail, memory_order_acquire));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("active=%lu\\n", javan_heap_active_threads());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096",
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );

        assertThat(stdout).isEqualTo(
            """
            worker=0
            parent=0
            roots=1
            active=0
            current=1
            """
        );
    }

    @Test
    void runtimeStartedWorkerPanicStateDoesNotLeakBackToMainThread() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            static int worker_line = 0;
            static char worker_detail[64];

            void javan_thread_run_target(void* target) {
                (void) target;
                jmp_buf panic_target;
                javan_panic_set_target(&panic_target);
                if (setjmp(panic_target) != 0) {
                    worker_line = javan_last_error_line();
                    snprintf(worker_detail, sizeof(worker_detail), "%s", javan_last_error_detail());
                    javan_clear_error();
                    return;
                }
                JavanSourceContext context;
                javan_source_enter(
                    &context,
                    "JAVAN-RUNTIME-PANIC",
                    "runtime helper failure",
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    33,
                    7,
                    "",
                    "why",
                    "fix"
                );
                javan_panic("worker failure");
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                memset(worker_detail, 0, sizeof(worker_detail));
                void* worker = javan_thread_new();
                javan_thread_set_target(worker, javan_thread_new());
                javan_thread_start(worker);
                javan_thread_join(worker);
                printf("worker=%d:%s\\n", worker_line, worker_detail);
                printf("main=%s\\n", javan_last_error() == NULL ? "clear" : "dirty");
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            worker=33:worker failure
            main=clear
            """
        );
    }

    @Test
    void runtimeThreadRootRegistryGrowsAcrossManyConcurrentStartedWorkers() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #include <unistd.h>

            static atomic_int ready_count;
            static atomic_int release_workers;
            static void* workers[6];

            void javan_thread_run_target(void* target) {
                (void) target;
                atomic_fetch_add_explicit(&ready_count, 1, memory_order_release);
                while (atomic_load_explicit(&release_workers, memory_order_acquire) == 0) {
                    usleep(1000);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                for (int index = 0; index < 6; index++) {
                    workers[index] = javan_thread_new();
                    javan_thread_set_target(workers[index], javan_thread_new());
                    javan_thread_start(workers[index]);
                }
                while (atomic_load_explicit(&ready_count, memory_order_acquire) < 6) {
                    usleep(1000);
                }
                javan_gc_collect();
                printf("roots-live=%lu\\n", javan_heap_registered_thread_roots());
                printf("active-live=%lu\\n", javan_heap_active_threads());
                atomic_store_explicit(&release_workers, 1, memory_order_release);
                for (int index = 0; index < 6; index++) {
                    javan_thread_join(workers[index]);
                }
                javan_gc_collect();
                printf("roots-after=%lu\\n", javan_heap_registered_thread_roots());
                printf("active-after=%lu\\n", javan_heap_active_threads());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            roots-live=7
            active-live=6
            roots-after=1
            active-after=0
            """
        );
    }

    @Test
    void runtimeParentCollectionPreservesBlockedWorkerLocalRootedObject() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <stdatomic.h>
            #include <unistd.h>

            static atomic_int worker_ready;
            static atomic_int release_worker;
            static atomic_int worker_result;

            void javan_thread_run_target(void* target) {
                (void) target;
                void* rooted_thread = javan_thread_new();
                void** roots[] = {
                    (void**) &rooted_thread
                };
                javan_root_frame_push(roots, 1);
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&release_worker, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                atomic_store_explicit(&worker_result, javan_thread_is_alive(rooted_thread), memory_order_release);
                javan_root_frame_pop(roots);
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                (void) javan_thread_current();
                void* worker = javan_thread_new();
                javan_thread_set_target(worker, javan_thread_new());
                javan_thread_start(worker);
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    usleep(1000);
                }
                javan_gc_collect();
                atomic_store_explicit(&release_worker, 1, memory_order_release);
                javan_thread_join(worker);
                javan_gc_collect();
                printf("worker=%d\\n", atomic_load_explicit(&worker_result, memory_order_acquire));
                printf("roots=%lu\\n", javan_heap_registered_thread_roots());
                printf("active=%lu\\n", javan_heap_active_threads());
                printf("current=%d\\n", javan_heap_current_thread_root_present());
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo(
            """
            worker=0
            roots=1
            active=0
            current=1
            """
        );
    }

    @Test
    void writeCollectsRuntimeContainersWithExplicitOwnedStorage() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "javan_update_runtime_allocation_kind((void*) list, JAVAN_RUNTIME_KIND_OBJECT_LIST);",
            "javan_update_runtime_allocation_kind((void*) iterator, JAVAN_RUNTIME_KIND_OBJECT_ITERATOR);",
            "javan_update_runtime_allocation_kind((void*) map, JAVAN_RUNTIME_KIND_OBJECT_MAP);",
            "javan_update_runtime_allocation_kind((void*) builder, JAVAN_RUNTIME_KIND_STRING_BUILDER);",
            "javan_update_runtime_allocation_kind((void*) optional, JAVAN_RUNTIME_KIND_OPTIONAL);",
            "javan_update_runtime_allocation_kind((void*) address, JAVAN_RUNTIME_KIND_INET_ADDRESS);",
            "javan_update_runtime_allocation_kind((void*) socket_address, JAVAN_RUNTIME_KIND_INET_SOCKET_ADDRESS);",
            "javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SOCKET);",
            "javan_update_runtime_allocation_kind((void*) socket, JAVAN_RUNTIME_KIND_SERVER_SOCKET);",
            "javan_update_runtime_allocation_kind(stream_root, JAVAN_RUNTIME_KIND_SOCKET_INPUT_STREAM);",
            "javan_update_runtime_allocation_kind(stream_root, JAVAN_RUNTIME_KIND_SOCKET_OUTPUT_STREAM);",
            "javan_update_runtime_allocation_kind((void*) list->values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "javan_update_runtime_allocation_kind((void*) next_keys, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "javan_update_runtime_allocation_kind((void*) next_values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "javan_update_runtime_allocation_kind((void*) next, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "static void javan_validate_owned_runtime_buffer_reference(void* value)",
            "static void javan_validate_runtime_container_references(javan_allocation_node* node)",
            "invalid runtime inet address metadata",
            "invalid runtime inet socket address metadata",
            "invalid runtime socket metadata",
            "invalid runtime server socket metadata",
            "invalid runtime socket input stream metadata",
            "invalid runtime socket output stream metadata",
            "invalid runtime http request builder metadata",
            "invalid runtime http request metadata",
            "invalid runtime http body publisher metadata",
            "javan_validate_owned_runtime_buffer_reference((void*) list->values);",
            "javan_validate_owned_runtime_buffer_reference((void*) map->keys);",
            "javan_validate_owned_runtime_buffer_reference((void*) map->values);",
            "javan_validate_owned_runtime_buffer_reference((void*) map->key_hashes);",
            "javan_validate_owned_runtime_buffer_reference((void*) map->index_buckets);",
            "javan_validate_owned_runtime_buffer_reference((void*) builder->values);",
            "static void javan_validate_runtime_managed_reference(void* value)",
            "javan_validate_runtime_managed_reference((void*) builder->headers);",
            "javan_validate_runtime_managed_reference((void*) request->headers);",
            "javan_validate_runtime_managed_reference(publisher->value);",
            "builder->values != NULL && (builder->capacity < 0 || builder->length > builder->capacity)",
            "static void* javan_realloc_tracked(void* value, unsigned long size, int validate_after)",
            "static void* javan_realloc_owned_buffer(void* value, unsigned long size)",
            "static void javan_free_owned_runtime_buffer(void* value)",
            "static void javan_release_runtime_owned_buffers(javan_allocation_node* node)",
            "static void javan_gc_mark_runtime_list(javan_object_list* list)",
            "static void javan_gc_mark_runtime_map(javan_object_map* map)",
            "static void javan_gc_mark_runtime_children(void* value, int runtime_kind)",
            "javan_gc_mark_value((void*) list->values);",
            "javan_gc_mark_value((void*) map->keys);",
            "javan_gc_mark_value((void*) map->values);",
            "javan_gc_mark_value((void*) map->key_hashes);",
            "javan_gc_mark_value((void*) map->index_buckets);",
            "javan_gc_mark_value((void*) iterator->list);",
            "javan_gc_mark_value((void*) builder->values);",
            "javan_gc_mark_value(optional->value);",
            "javan_gc_mark_value((void*) address->host_address);",
            "javan_gc_mark_value((void*) address->host_name);",
            "javan_gc_mark_value((void*) address->address);",
            "javan_gc_mark_value((void*) socket->local_address);",
            "javan_gc_mark_value((void*) socket->remote_address);",
            "javan_gc_mark_value((void*) stream->socket);",
            "javan_gc_mark_value((void*) builder->headers);",
            "javan_gc_mark_value(request->body);",
            "javan_gc_mark_value(publisher->value);",
            "javan_root_frame_push(javan_map_owner_roots, 5);",
            "javan_root_frame_push(javan_map_growth_roots, 5);",
            "map->keys = next_keys;",
            "map->values = next_values;",
            "map->key_hashes = next_hashes;",
            "map->index_buckets = next_buckets;",
            "void** javan_list_array_roots[] = {",
            "javan_root_frame_push(javan_list_array_roots, 1);",
            "javan_root_frame_pop(javan_list_array_roots);",
            "void** javan_list_copy_roots[] = {",
            "javan_root_frame_push(javan_list_copy_roots, 1);",
            "javan_root_frame_pop(javan_list_copy_roots);",
            "void** javan_list_iterator_roots[] = {",
            "javan_root_frame_push(javan_list_iterator_roots, 1);",
            "javan_root_frame_pop(javan_list_iterator_roots);",
            "void* values[count > 0 ? count : 1];",
            "void** roots[count > 0 ? count : 1];",
            "roots[index] = &values[index];",
            "javan_root_frame_push(roots, count);",
            "javan_root_frame_pop(roots);",
            "void** javan_map_copy_roots[] = {",
            "javan_root_frame_push(javan_map_copy_roots, 1);",
            "javan_root_frame_pop(javan_map_copy_roots);",
            "void** javan_map_values_roots[] = {",
            "javan_root_frame_push(javan_map_values_roots, 1);",
            "javan_root_frame_pop(javan_map_values_roots);"
        );
    }

    @Test
    void writeIncludesSocketOptionRuntimeStateAndHelpers() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "int tcp_no_delay;",
            "int keep_alive;",
            "int reuse_address;",
            "static int javan_socket_getsockopt_flag(int fd, int level, int option_name, const char* message)",
            "static void javan_socket_setsockopt_flag(int fd, int level, int option_name, int enabled, const char* message)",
            "socket->tcp_no_delay = tcp_no_delay;",
            "socket->keep_alive = keep_alive;",
            "socket->reuse_address = javan_socket_getsockopt_flag(fd, SOL_SOCKET, SO_REUSEADDR, \"server socket SO_REUSEADDR lookup failed\");",
            "int javan_socket_get_tcp_no_delay(void* value)",
            "void javan_socket_set_tcp_no_delay(void* value, int enabled)",
            "int javan_socket_get_keep_alive(void* value)",
            "void javan_socket_set_keep_alive(void* value, int enabled)",
            "int javan_server_socket_get_reuse_address(void* value)",
            "void javan_server_socket_set_reuse_address(void* value, int enabled)",
            "(socket->tcp_no_delay != 0 && socket->tcp_no_delay != 1)",
            "(socket->keep_alive != 0 && socket->keep_alive != 1)",
            "(socket->reuse_address != 0 && socket->reuse_address != 1)"
        );
    }

    @Test
    void writeEmitsHttpPostHeaderAndByteArrayHelpers() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "void* javan_http_request_builder_header(void* value, void* name_value, void* header_value) {",
            "void* javan_http_request_builder_post(void* value, void* body_publisher_value) {",
            "void* javan_http_request_builder_put(void* value, void* body_publisher_value) {",
            "void* javan_http_body_publisher_string(void* value) {",
            "void* javan_http_body_publisher_byte_array(void* value) {",
            "void* javan_http_body_handler_byte_array(void) {",
            "javan_http_header_text_checked",
            "javan_http_body_publisher_length",
            "javan_http_body_publisher_bytes",
            "Content-Length: %lu\\r\\n",
            "javan_byte_array_from((const signed char*) body_start, (int) response_body_length);"
        );
    }

    @Test
    void writePublishesMapBackingArraysBeforeSecondGrowthAllocationCanCollect() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final String source = Files.readString(runtime);
        final int keysRealloc = source.indexOf("next_keys = (void**) javan_realloc_owned_buffer(old_keys, (unsigned long) next_capacity * sizeof(void*));");
        final int keysPublish = source.indexOf("map->keys = next_keys;", keysRealloc);
        final int valuesRealloc = source.indexOf("next_values = (void**) javan_realloc_owned_buffer(old_values, (unsigned long) next_capacity * sizeof(void*));");
        final int valuesPublish = source.indexOf("map->values = next_values;", valuesRealloc);

        assertThat(keysRealloc).isGreaterThanOrEqualTo(0);
        assertThat(keysPublish).isGreaterThan(keysRealloc);
        assertThat(keysPublish).isLessThan(valuesRealloc);
        assertThat(valuesPublish).isGreaterThan(valuesRealloc);
        assertThat(valuesPublish).isLessThan(source.indexOf("memset(next_keys + map->capacity", valuesPublish));
    }

    @Test
    void writeRootsRuntimeStringSourcesAcrossAllocatingHelpers() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "void** javan_string_chars_roots[] = {",
            "javan_root_frame_push(javan_string_chars_roots, 1);",
            "javan_root_frame_pop(javan_string_chars_roots);",
            "void** javan_string_replace_roots[] = {",
            "javan_root_frame_push(javan_string_replace_roots, 1);",
            "javan_root_frame_pop(javan_string_replace_roots);",
            "void** javan_string_substring_roots[] = {",
            "javan_root_frame_push(javan_string_substring_roots, 1);",
            "javan_root_frame_pop(javan_string_substring_roots);",
            "void** javan_string_copy_roots[] = {",
            "javan_root_frame_push(javan_string_copy_roots, 1);",
            "javan_root_frame_pop(javan_string_copy_roots);",
            "void* javan_concat_values[argc > 0 ? argc : 1];",
            "void** javan_concat_roots[argc > 0 ? argc : 1];",
            "javan_root_frame_push(javan_concat_roots, argc);",
            "javan_root_frame_pop(javan_concat_roots);",
            "void** javan_builder_append_roots[] = {",
            "javan_root_frame_push(javan_builder_append_roots, 2);",
            "javan_root_frame_pop(javan_builder_append_roots);",
            "void** javan_path_of_roots[] = {",
            "javan_root_frame_push(javan_path_of_roots, 2);",
            "void** javan_path_resolve_roots[] = {",
            "javan_root_frame_push(javan_path_resolve_roots, 2);",
            "void** javan_path_normalize_roots[] = {",
            "javan_root_frame_push(javan_path_normalize_roots, 1);",
            "void** javan_path_relativize_roots[] = {",
            "javan_root_frame_push(javan_path_relativize_roots, 1);",
            "void** javan_string_export_roots[] = {",
            "javan_root_frame_push(javan_string_export_roots, 1);",
            "void** javan_byte_export_roots[] = {",
            "javan_root_frame_push(javan_byte_export_roots, 1);",
            "void** javan_array_copy_roots[] = {",
            "(void**) &source_root,",
            "void javan_arrays_copy_of_object_into(void** result, void* array, int new_length)",
            "void javan_arrays_copy_of_int_into(void** result, void* array, int new_length)",
            "javan_root_frame_push(javan_array_copy_roots, 2);",
            "*result = allocate(new_length);",
            "javan_array_header* target = javan_array_checked(*result);",
            "javan_root_frame_pop(javan_array_copy_roots);",
            "void** javan_array_range_copy_roots[] = {",
            "javan_root_frame_push(javan_array_range_copy_roots, 1);",
            "javan_root_frame_pop(javan_array_range_copy_roots);",
            "void* source_root = path_value;",
            "void* result_root = NULL;",
            "void** javan_directory_result_roots[] = {",
            "javan_root_frame_push(javan_directory_result_roots, 2);",
            "result_root = javan_list_new_with_capacity(0, 1);",
            "void* child = javan_path_resolve(source_root, (void*) entry->d_name);",
            "javan_root_frame_pop(javan_directory_result_roots);"
        );
    }

    @Test
    void runtimeMapReallocGrowthCollectsSafelyUnderHeapPressure() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                int checksum = 0;
                for (int round = 0; round < 32; round++) {
                    void* map = javan_hashmap_new();
                    void** map_roots[] = {
                        (void**) &map
                    };
                    javan_root_frame_push(map_roots, 1);
                    for (int index = 0; index < 12; index++) {
                        void* key = NULL;
                        void* value = NULL;
                        void** entry_roots[] = {
                            (void**) &map,
                            (void**) &key,
                            (void**) &value
                        };
                        javan_root_frame_push(entry_roots, 3);
                        key = javan_string_value_of_int((round * 100) + index);
                        value = javan_string_value_of_int(index);
                        (void) javan_map_put(map, key, value);
                        javan_root_frame_pop(entry_roots);
                    }
                    checksum += javan_map_size(map);
                    javan_root_frame_pop(map_roots);
                }
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("checksum=%d\\n", checksum);
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("bytes=%lu\\n", javan_heap_live_bytes());
                return 0;
            }
            """,
            "8192"
        );

        assertThat(stdout).isEqualTo("checksum=384\nlive=0\nbytes=0\n");
    }

    @Test
    void runtimeMapStringLookupScalesWithoutLinearKeyScans() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* map = javan_hashmap_new();
                void** roots[] = {
                    (void**) &map
                };
                javan_root_frame_push(roots, 1);
                for (int index = 0; index < 4096; index++) {
                    void* key = javan_string_value_of_int(index);
                    (void) javan_map_put(map, key, key);
                }
                int checksum = 0;
                for (int index = 0; index < 4096; index++) {
                    void* key = javan_string_value_of_int(index);
                    void* value = javan_map_get(map, key);
                    checksum += value == NULL ? 0 : javan_string_length((const char*) value);
                }
                printf("size=%d\\n", javan_map_size(map));
                printf("checksum=%d\\n", checksum);
                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("live=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "67108864",
            Map.of("JAVAN_GC_STRESS", "0"),
            java.time.Duration.ofSeconds(10)
        );

        assertThat(stdout).isEqualTo("size=4096\nchecksum=15274\nlive=0\n");
    }

    @Test
    void runtimeMapHashIndexPreservesCollisionsEqualityRemovalAndReuse() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* map = javan_hashmap_new();
                const char* aa_part[] = {"Aa"};
                const char* bb_part[] = {"BB"};
                void* managed_aa = NULL;
                void* managed_bb = NULL;
                void* identity = NULL;
                void* other_identity = NULL;
                void** roots[] = {
                    (void**) &map,
                    (void**) &managed_aa,
                    (void**) &managed_bb,
                    (void**) &identity,
                    (void**) &other_identity
                };
                javan_root_frame_push(roots, 5);
                managed_aa = javan_string_concat("\\001", 1, aa_part);
                managed_bb = javan_string_concat("\\001", 1, bb_part);
                identity = javan_arraylist_new();
                other_identity = javan_arraylist_new();

                (void) javan_map_put(map, "Aa", "first");
                (void) javan_map_put(map, "BB", "second");
                printf("collision=%s:%s:%d\\n",
                    (char*) javan_map_get(map, managed_aa),
                    (char*) javan_map_get(map, managed_bb),
                    javan_map_size(map));

                printf("previous=%s\\n", (char*) javan_map_put(map, managed_aa, "updated"));
                printf("equal=%s:%d\\n", (char*) javan_map_get(map, "Aa"), javan_map_size(map));
                printf("removed=%s:%d:%s\\n",
                    (char*) javan_map_remove(map, managed_bb),
                    javan_map_contains_key(map, "BB"),
                    (char*) javan_map_get(map, managed_aa));

                (void) javan_map_put(map, NULL, "null-key");
                (void) javan_map_put(map, identity, "identity");
                printf("kinds=%s:%s:%d\\n",
                    (char*) javan_map_get(map, NULL),
                    (char*) javan_map_get(map, identity),
                    javan_map_contains_key(map, other_identity));

                javan_map_clear(map);
                (void) javan_map_put(map, managed_bb, "reused");
                printf("reuse=%s:%d\\n", (char*) javan_map_get(map, "BB"), javan_map_size(map));

                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("live=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "1048576"
        );

        assertThat(stdout).isEqualTo(
            """
            collision=first:second:2
            previous=first
            equal=updated:2
            removed=second:0:updated
            kinds=null-key:identity:0
            reuse=reused:1
            live=0
            """
        );
    }

    @Test
    void runtimeMapBoxedFloatingKeysUseJavaSignedZeroAndNanEquality() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <math.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* map = javan_hashmap_new();
                void* float_positive_zero = javan_float_value_of(0.0f);
                void* float_negative_zero = javan_float_value_of(-0.0f);
                void* float_nan = javan_float_value_of(NAN);
                void* float_nan_equal = javan_float_value_of(NAN);
                void* double_positive_zero = javan_double_value_of(0.0);
                void* double_negative_zero = javan_double_value_of(-0.0);
                void* double_nan = javan_double_value_of(NAN);
                void* double_nan_equal = javan_double_value_of(NAN);
                void* query = NULL;
                void** roots[] = {
                    (void**) &map,
                    (void**) &float_positive_zero,
                    (void**) &float_negative_zero,
                    (void**) &float_nan,
                    (void**) &float_nan_equal,
                    (void**) &double_positive_zero,
                    (void**) &double_negative_zero,
                    (void**) &double_nan,
                    (void**) &double_nan_equal,
                    (void**) &query
                };
                javan_root_frame_push(roots, 10);

                (void) javan_map_put(map, float_positive_zero, "float-positive");
                (void) javan_map_put(map, float_negative_zero, "float-negative");
                (void) javan_map_put(map, float_nan, "float-nan-first");
                (void) javan_map_put(map, float_nan_equal, "float-nan-updated");
                (void) javan_map_put(map, double_positive_zero, "double-positive");
                (void) javan_map_put(map, double_negative_zero, "double-negative");
                (void) javan_map_put(map, double_nan, "double-nan-first");
                (void) javan_map_put(map, double_nan_equal, "double-nan-updated");

                query = javan_float_value_of(0.0f);
                printf("float-positive=%s\\n", (char*) javan_map_get(map, query));
                query = javan_float_value_of(-0.0f);
                printf("float-negative=%s\\n", (char*) javan_map_get(map, query));
                query = javan_float_value_of(NAN);
                printf("float-nan=%s\\n", (char*) javan_map_get(map, query));
                query = javan_double_value_of(0.0);
                printf("double-positive=%s\\n", (char*) javan_map_get(map, query));
                query = javan_double_value_of(-0.0);
                printf("double-negative=%s\\n", (char*) javan_map_get(map, query));
                query = javan_double_value_of(NAN);
                printf("double-nan=%s\\n", (char*) javan_map_get(map, query));
                printf("size=%d\\n", javan_map_size(map));

                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("live=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "1048576"
        );

        assertThat(stdout).isEqualTo(
            """
            float-positive=float-positive
            float-negative=float-negative
            float-nan=float-nan-updated
            double-positive=double-positive
            double-negative=double-negative
            double-nan=double-nan-updated
            size=6
            live=0
            """
        );
    }

    @Test
    void runtimeListReallocPublishesOwnedBufferBeforeValidation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* list = javan_arraylist_new();
                void** roots[] = {
                    (void**) &list
                };
                javan_root_frame_push(roots, 1);
                int checksum = 0;
                for (int index = 0; index < 20; index++) {
                    void* value = javan_string_value_of_int(index);
                    (void) javan_arraylist_add(list, value);
                    checksum += javan_string_length((const char*) javan_list_get(list, index));
                }
                javan_validate_heap_metadata();
                printf("size=%d\\n", javan_list_size(list));
                printf("checksum=%d\\n", checksum);
                printf("live=%lu\\n", javan_heap_live_allocations());
                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("after=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "2048"
        );

        assertThat(stdout).isEqualTo("size=20\nchecksum=30\nlive=22\nafter=0\n");
    }

    @Test
    void runtimeStringBuilderReallocPublishesOwnedBufferBeforeValidation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* builder = javan_stringbuilder_new();
                void** roots[] = {
                    (void**) &builder
                };
                javan_root_frame_push(roots, 1);
                for (int index = 0; index < 64; index++) {
                    (void) javan_stringbuilder_append_string(builder, "ab");
                }
                javan_validate_heap_metadata();
                printf("length=%d\\n", javan_stringbuilder_length(builder));
                printf("live=%lu\\n", javan_heap_live_allocations());
                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("after=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "2048"
        );

        assertThat(stdout).isEqualTo("length=128\nlive=2\nafter=0\n");
    }

    @Test
    void writeRejectsStringBuilderRequiredSizeBeforeSignedOverflow() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "if (required == INT_MAX) {",
            "javan_panic(\"string builder length overflow\");",
            "if (builder->values != NULL && required <= builder->capacity) {",
            "if (next_capacity > (INT_MAX - 2) / 2) {",
            "next_capacity = next_capacity * 2 + 2;",
            "char* next = (char*) javan_realloc_owned_buffer(builder->values, (unsigned long) next_capacity + 1UL);"
        );
    }

    @Test
    void runtimeListOfVarargsRootsElementsAcrossListAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 6; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                seed_dead_strings();
                const char* left_values[] = {"left"};
                const char* right_values[] = {"right"};
                void* left = javan_string_concat("value-\\001", 1, left_values);
                void* right = javan_string_concat("value-\\001", 1, right_values);
                void* list = javan_list_of(2, left, right);
                void** roots[] = {
                    (void**) &list
                };
                javan_root_frame_push(roots, 1);
                javan_gc_collect();
                printf("%s:%s\\n", (char*) javan_list_get(list, 0), (char*) javan_list_get(list, 1));
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("bytes-positive=%d\\n", javan_heap_live_bytes() > 0);
                javan_root_frame_pop(roots);
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("after=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("value-left:value-right\nlive=4\nbytes-positive=1\nafter=0\n");
    }

    @Test
    void runtimePathNormalizeRootsSourceAcrossAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 5; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                seed_dead_strings();
                const char* values[] = {"service"};
                char* input = (char*) javan_string_concat("/tmp//\\001//../app", 1, values);
                char* normalized = (char*) javan_path_normalize(input);
                printf("%s\\n", normalized);
                return 0;
            }
            """,
            "112"
        );

        assertThat(stdout).isEqualTo("/tmp/app\n");
    }

    @Test
    void runtimePathRelativizeRootsChildBaseForSuffixAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 5; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                seed_dead_strings();
                const char* values[] = {"service"};
                char* child = (char*) javan_string_concat("/tmp/\\001/app/Main.java", 1, values);
                char* relative = (char*) javan_path_relativize("/tmp/service", child);
                printf("%s\\n", relative);
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("app/Main.java\n");
    }

    @Test
    void runtimeStringExportRootsSourceAcrossExportAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 5; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                seed_dead_strings();
                const char* values[] = {"safe"};
                char* input = (char*) javan_string_concat("export-\\001", 1, values);
                char* exported = javan_string_export(input);
                printf("%s\\n", exported);
                javan_free(exported);
                return 0;
            }
            """,
            "112"
        );

        assertThat(stdout).isEqualTo("export-safe\n");
    }

    @Test
    void runtimeStringFromCopiesBorrowedInputAndPreservesNull() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                char input[5] = {'Y', 'u', 'n', 'a', 0};
                char* copied = (char*) javan_string_from(input);
                input[0] = 'L';
                printf("%s\\n", copied);
                printf("%s\\n", javan_string_from(NULL) == NULL ? "null" : "not-null");
                copied = NULL;
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("bytes=%lu\\n", javan_heap_live_bytes());
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("Yuna\nnull\nlive=0\nbytes=0\n");
    }

    @Test
    void runtimeByteArrayExportRootsArrayAcrossExportAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 5; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                seed_dead_strings();
                signed char data[64];
                for (int index = 0; index < 64; index++) {
                    data[index] = (signed char) (index + 1);
                }
                void* array = javan_byte_array_from(data, 64);
                JavanByteArray exported = javan_byte_array_export(array);
                printf("%d:%d:%d\\n", exported.length, exported.data[0], exported.data[63]);
                javan_free(exported.data);
                return 0;
            }
            """,
            "220"
        );

        assertThat(stdout).isEqualTo("64:1:64\n");
    }

    @Test
    void runtimeRepeatedStringExportFreeReturnsToZeroLiveHeap() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                int checksum = 0;
                for (int index = 0; index < 256; index++) {
                    const char* values[] = {"safe"};
                    char* input = (char*) javan_string_concat("export-\\001", 1, values);
                    char* exported = javan_string_export(input);
                    checksum += exported[0];
                    javan_free(exported);
                }
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("checksum=%d\\n", checksum);
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("bytes=%lu\\n", javan_heap_live_bytes());
                return 0;
            }
            """,
            "512"
        );

        assertThat(stdout).isEqualTo("checksum=25856\nlive=0\nbytes=0\n");
    }

    @Test
    void runtimeStringStartsWithFromMatchesJavaOffsetSemantics() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_string_starts_with_from("javan native", "native", 6));
                printf("%d\\n", javan_string_starts_with_from("javan native", "native", 7));
                printf("%d\\n", javan_string_starts_with_from("javan native", "javan", -1));
                printf("%d\\n", javan_string_starts_with_from("javan native", "", 12));
                printf("%d\\n", javan_string_starts_with_from("javan native", "", 13));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("1\n0\n0\n1\n0\n");
    }

    @Test
    void runtimeRepeatedByteArrayExportFreeReturnsToZeroLiveHeap() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                signed char data[8] = {3, 1, 4, 1, 5, 9, 2, 6};
                int checksum = 0;
                for (int index = 0; index < 256; index++) {
                    void* array = javan_byte_array_from(data, 8);
                    JavanByteArray exported = javan_byte_array_export(array);
                    checksum += exported.data[0];
                    checksum += exported.data[7];
                    javan_free(exported.data);
                }
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("checksum=%d\\n", checksum);
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("bytes=%lu\\n", javan_heap_live_bytes());
                return 0;
            }
            """,
            "512"
        );

        assertThat(stdout).isEqualTo("checksum=2304\nlive=0\nbytes=0\n");
    }

    @Test
    void runtimePanicTargetRecordsLastErrorWithoutStderr() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    printf(
                        "%s:%s:%s:%d:%d:%s\\n",
                        javan_last_error_code(),
                        javan_last_error_summary(),
                        javan_last_error_class() == NULL ? "null" : javan_last_error_class(),
                        javan_last_error_line(),
                        javan_last_error_bytecode_offset(),
                        javan_last_error_detail()
                    );
                    javan_clear_error();
                    printf("%s\\n", javan_last_error() == NULL ? "clear" : "dirty");
                    printf("%s:%d:%d\\n", javan_last_error_code() == NULL ? "clear-code" : "dirty-code", javan_last_error_line(), javan_last_error_bytecode_offset());
                    return 0;
                }
                javan_panic("recoverable failure");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("""
            recoverable failure
            JAVAN-RUNTIME-PANIC:native runtime panic:null:-1:-1:recoverable failure
            clear
            clear-code:-1:-1
            """);
    }

    @Test
    void runtimeResultErrorOwnsDiagnosticFieldsAfterLastErrorClears() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    JavanResult result = javan_result_error_from_last_error();
                    javan_clear_error();
                    printf("%d:%s:%s:%d:%d\\n", result.ok, result.code, result.detail, result.line, result.bytecode_offset);
                    printf("borrowed:%s\\n", javan_last_error() == NULL ? "clear" : "dirty");
                    javan_gc_collect();
                    javan_validate_heap_metadata();
                    printf("live-before-free=%lu\\n", javan_heap_live_allocations());
                    javan_result_free(&result);
                    printf("freed:%s:%d\\n", result.message == NULL ? "yes" : "no", result.line);
                    javan_gc_collect();
                    javan_validate_heap_metadata();
                    printf("live-after-free=%lu\\n", javan_heap_live_allocations());
                    return 0;
                }
                javan_panic("owned failure");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("""
            0:JAVAN-RUNTIME-PANIC:owned failure:-1:-1
            borrowed:clear
            live-before-free=0
            freed:yes:-1
            live-after-free=0
            """);
    }

    @Test
    void runtimeSourceMappedPanicTargetRecordsReadableLastError() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                javan_panic_at(
                    "JAVAN-RUNTIME-PANIC",
                    "uncaught Java exception",
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    9,
                    12,
                    "",
                    "why",
                    "fix",
                    "boom"
                );
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).contains(
            "[JAVAN-RUNTIME-PANIC] uncaught Java exception",
            "com.acme.Main.main()V(Main.java:9)",
            "bytecode:12",
            "detail:boom"
        );
    }

    @Test
    void runtimeSourceMappedPanicTargetRecordsStructuredLastErrorFields() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error_code());
                    printf("%s\\n", javan_last_error_summary());
                    printf("%s\\n", javan_last_error_class());
                    printf("%s\\n", javan_last_error_method());
                    printf("%s\\n", javan_last_error_file());
                    printf("%d:%d\\n", javan_last_error_line(), javan_last_error_bytecode_offset());
                    printf("%s\\n", javan_last_error_source_line());
                    printf("%s\\n", javan_last_error_why());
                    printf("%s\\n", javan_last_error_fix());
                    printf("%s\\n", javan_last_error_detail());
                    return 0;
                }
                javan_panic_at(
                    "JAVAN-RUNTIME-PANIC",
                    "uncaught Java exception",
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    9,
                    12,
                    "throw new IllegalStateException(\\"boom\\");",
                    "The exception reached the native boundary.",
                    "Catch it before export.",
                    "boom"
                );
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("""
            JAVAN-RUNTIME-PANIC
            uncaught Java exception
            com.acme.Main
            main()V
            Main.java
            9:12
            throw new IllegalStateException("boom");
            The exception reached the native boundary.
            Catch it before export.
            boom
            """);
    }

    @Test
    void runtimeSourceContextMapsPlainPanicToReadableLastError() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                JavanSourceContext context;
                javan_source_enter(
                    &context,
                    "JAVAN-RUNTIME-PANIC",
                    "runtime helper failure",
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    11,
                    4,
                    "",
                    "why",
                    "fix"
                );
                javan_panic("array index out of bounds");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).contains(
            "[JAVAN-RUNTIME-PANIC] runtime helper failure",
            "com.acme.Main.main()V(Main.java:11)",
            "bytecode:4",
            "detail:array index out of bounds"
        );
    }

    @Test
    void runtimeSourceContextRestoresOuterContextAfterNestedClear() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                JavanSourceContext outer;
                JavanSourceContext inner;
                javan_source_enter(&outer, "JAVAN-RUNTIME-PANIC", "runtime helper failure", "com.acme.Main", "main()V", "Main.java", 11, 4, "", "why", "fix");
                javan_source_enter(&inner, "JAVAN-RUNTIME-PANIC", "runtime helper failure", "com.acme.Helper", "run()V", "Helper.java", 21, 8, "", "why", "fix");
                javan_source_clear(&inner);
                javan_panic("after nested call");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).contains(
            "com.acme.Main.main()V(Main.java:11)",
            "bytecode:4",
            "detail:after nested call"
        );
        assertThat(stdout).doesNotContain("Helper.java");
    }

    @Test
    void runtimePanicTargetClearsSourceContextAfterRecoveredPanic() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    JavanSourceContext context;
                    javan_source_enter(&context, "JAVAN-RUNTIME-PANIC", "runtime helper failure", "com.acme.Main", "main()V", "Main.java", 11, 4, "", "why", "fix");
                    javan_panic("first failure");
                    return 2;
                }
                printf("%s\\n", javan_last_error());
                jmp_buf second;
                javan_panic_set_target(&second);
                if (setjmp(second) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                javan_panic("second failure");
                return 3;
            }
            """,
            "128"
        );

        assertThat(stdout).contains(
            "[JAVAN-RUNTIME-PANIC] runtime helper failure",
            "detail:first failure"
        );
        assertThat(stdout).endsWith("second failure\n");
    }

    @Test
    void runtimeRecoverablePanicScopeRestoresOuterRootFrameSnapshot() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            void* javan_generated_object_get_class(void* value) {
                (void) value;
                return 0;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* outer_root = NULL;
                void** roots[] = { &outer_root };
                javan_root_frame_push(roots, 1);
                JavanPanicScope scope;
                jmp_buf target;
                javan_panic_scope_push(&scope, &target);
                if (setjmp(target) != 0) {
                    printf("%d,%d\\n", javan_heap_root_frame_depth(), javan_heap_frame_root_count());
                    return 0;
                }
                void* inner_root = NULL;
                void** inner_roots[] = { &inner_root, &outer_root };
                javan_root_frame_push(inner_roots, 2);
                javan_panic("recover");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("1,1\n");
    }

    @Test
    void runtimeRecoverablePanicScopeRestoresOuterSourceContextAndTarget() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            void* javan_generated_object_get_class(void* value) {
                (void) value;
                return 0;
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf outer;
                javan_panic_set_target(&outer);
                if (setjmp(outer) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                JavanSourceContext outer_context;
                javan_source_enter(
                    &outer_context,
                    "JAVAN-RUNTIME-PANIC",
                    "outer failure",
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    11,
                    4,
                    "",
                    "why",
                    "fix"
                );
                JavanPanicScope scope;
                jmp_buf inner;
                javan_panic_scope_push(&scope, &inner);
                if (setjmp(inner) != 0) {
                    javan_panic("after recover");
                    return 3;
                }
                JavanSourceContext inner_context;
                javan_source_enter(
                    &inner_context,
                    "JAVAN-RUNTIME-PANIC",
                    "inner failure",
                    "com.acme.Helper",
                    "run()V",
                    "Helper.java",
                    21,
                    8,
                    "",
                    "why",
                    "fix"
                );
                javan_panic("inner panic");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).contains("outer failure", "com.acme.Main.main()V(Main.java:11)", "detail:after recover");
        assertThat(stdout).doesNotContain("Helper.java");
    }

    @Test
    void runtimePanicAndSourceContextStateStayThreadLocalAcrossHostThreads() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #endif

            typedef struct {
                const char* label;
                const char* detail;
                int line;
            } worker_arg;

            #if defined(_WIN32)
            static unsigned __stdcall worker_main(void* raw) {
            #else
            static void* worker_main(void* raw) {
            #endif
                worker_arg* arg = (worker_arg*) raw;
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s:%d:%s\\n", arg->label, javan_last_error_line(), javan_last_error_detail());
                    javan_clear_error();
                    #if defined(_WIN32)
                    return 0U;
                    #else
                    return NULL;
                    #endif
                }
                JavanSourceContext context;
                javan_source_enter(
                    &context,
                    "JAVAN-RUNTIME-PANIC",
                    "runtime helper failure",
                    "com.acme.Main",
                    "main()V",
                    "Main.java",
                    arg->line,
                    4,
                    "",
                    "why",
                    "fix"
                );
                javan_panic(arg->detail);
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            static void run_worker(worker_arg* arg) {
                #if defined(_WIN32)
                HANDLE thread = (HANDLE) _beginthreadex(NULL, 0, worker_main, arg, 0, NULL);
                if (thread == NULL) {
                    javan_panic("worker create failed");
                }
                WaitForSingleObject(thread, INFINITE);
                CloseHandle(thread);
                #else
                pthread_t thread;
                if (pthread_create(&thread, NULL, worker_main, arg) != 0) {
                    javan_panic("worker create failed");
                }
                pthread_join(thread, NULL);
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                worker_arg left = { "left", "left failure", 11 };
                worker_arg right = { "right", "right failure", 22 };
                run_worker(&left);
                printf("%s\\n", javan_last_error() == NULL ? "main-clear" : "main-dirty");
                run_worker(&right);
                printf("%s\\n", javan_last_error() == NULL ? "main-clear" : "main-dirty");
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("""
            left:11:left failure
            main-clear
            right:22:right failure
            main-clear
            """);
    }

    @Test
    void runtimeClearedSourceContextKeepsPlainPanicRaw() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                JavanSourceContext context;
                javan_source_enter(&context, "JAVAN-RUNTIME-PANIC", "runtime helper failure", "com.acme.Main", "main()V", "Main.java", 11, 4, "", "why", "fix");
                javan_source_clear(&context);
                javan_panic("recoverable failure");
                return 2;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("recoverable failure\n");
    }

    @Test
    void runtimeArrayCopyRootsSourceAcrossTargetAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 6; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* source = javan_int_array_new(4);
                javan_int_array_set(source, 0, 7);
                javan_int_array_set(source, 3, 11);
                seed_dead_strings();
                void* copy = javan_arrays_copy_of_int(source, 32);
                printf("%d:%d:%d\\n", javan_array_length(copy), javan_int_array_get(copy, 0), javan_int_array_get(copy, 3));
                return 0;
            }
            """,
            "220"
        );

        assertThat(stdout).isEqualTo("32:7:11\n");
    }

    @Test
    void runtimeClassLiteralCanonicalizesIdentityWithoutManagedAllocationGrowth() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                const unsigned long live_before = javan_heap_live_allocations();
                const unsigned long total_before = javan_heap_total_allocations();
                void* first = javan_runtime_class_literal("com.acme.Canonical", 71, 0, 0, 1, 71);
                void* primitive = javan_runtime_class_literal("int", -2011, 0, 0, 0);
                for (int index = 0; index < 4096; index++) {
                    if (javan_runtime_class_literal("com.acme.Canonical", 71, 0, 0, 1, 71) != first) {
                        puts("identity-mismatch");
                        return 0;
                    }
                }
                printf("%d:%d:%lu:%lu\\n",
                    primitive == javan_runtime_class_literal("int", -2011, 0, 0, 0),
                    javan_class_exact_type_id(primitive),
                    javan_heap_live_allocations() - live_before,
                    javan_heap_total_allocations() - total_before);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:-2011:0:0\n");
    }

    @Test
    void runtimeClassLiteralCompatibleRepeatsAvoidRawAllocations() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(new RuntimeReplacement(
                "static void* javan_raw_calloc_retry(unsigned long size) {",
                """
                static unsigned long javan_test_raw_calloc_calls = 0;

                static void* javan_raw_calloc_retry(unsigned long size) {
                    javan_test_raw_calloc_calls++;
                """.stripTrailing()
            )),
            """
            int javan_test_class_literal_repeats_avoid_raw_allocations(void) {
                void* canonical = javan_runtime_class_literal("com.acme.Repeat", 17, 0, 0, 3, 3, 1, 2);
                unsigned long before = javan_test_raw_calloc_calls;
                for (int index = 0; index < 4096; index++) {
                    if (javan_runtime_class_literal("com.acme.Repeat", 17, 0, 0, 3, 2, 1, 2) != canonical) {
                        return 0;
                    }
                }
                return javan_test_raw_calloc_calls == before;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            extern int javan_test_class_literal_repeats_avoid_raw_allocations(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_literal_repeats_avoid_raw_allocations());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeCanonicalClassStatePreservesClassIdentityPaths() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* first = javan_runtime_class_literal("com.acme.Identity", 17, 0, 0, 0);
                void* second = javan_runtime_class_literal("com.acme.Other", 18, 0, 0, 0);
                void* class_class = javan_runtime_class_literal(
                    "java.lang.Class", -2003, 0, 0, 0
                );
                printf("%d:%d:%d:%s\\n",
                    javan_object_equals(first, second) == 0,
                    javan_object_get_class(first) == class_class,
                    javan_class_is_instance(class_class, first),
                    (char*) javan_printable_object_string(first));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:1:1:com.acme.Identity\n");
    }

    @Test
    void runtimeClassLiteralPromotesExactTypeId() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* target = javan_runtime_class_literal("com.acme.Target", 0, 0, 0, 0);
                void* promoted = javan_runtime_class_literal("com.acme.Target", 17, 0, 0, 0);
                printf("%d:%d\\n",
                    target == promoted,
                    javan_class_exact_type_id(target));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:17\n");
    }

    @Test
    void runtimeClassLiteralUnifiesSortedUniqueAssignability() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* target = javan_runtime_class_literal("com.acme.Target", 17, 0, 0, 3, 3, 1, 3);
                (void) javan_runtime_class_literal("com.acme.Target", 17, 0, 0, 3, 2, 3, 2);
                void* one = javan_runtime_class_literal("com.acme.One", 1, 0, 0, 0);
                void* two = javan_runtime_class_literal("com.acme.Two", 2, 0, 0, 0);
                void* three = javan_runtime_class_literal("com.acme.Three", 3, 0, 0, 0);
                printf("%d:%d:%d\\n",
                    javan_class_is_assignable_from(target, one),
                    javan_class_is_assignable_from(target, two),
                    javan_class_is_assignable_from(target, three));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:1:1\n");
    }

    @Test
    void runtimeClassLiteralRejectsConflictingExactTypeIds() throws Exception {
        final String panic = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0); (void) javan_runtime_class_literal(\"com.acme.Conflict\", 17, 0, 0, 0);",
            "(void) javan_runtime_class_literal(\"com.acme.Conflict\", 18, 0, 0, 0);"
        );

        assertThat(panic).isEqualTo("conflicting runtime class metadata\n");
    }

    @Test
    void runtimeClassLiteralRejectsDescriptorNameConflict() throws Exception {
        final String panic = runRuntimePanicProbe(
            """
            JavanTypeDescriptor descriptors[] = {{17, \"com.acme.A\", 0, 0, NULL}};
            javan_register_static_roots(0, 0);
            javan_register_type_descriptors(descriptors, 1);
            """,
            "(void) javan_runtime_class_literal(\"com.acme.B\", 17, 0, 0, 0);"
        );

        assertThat(panic).isEqualTo("conflicting runtime class metadata\n");
    }

    @Test
    void runtimeClassLiteralRejectsArrayMetadataConflict() throws Exception {
        final String panic = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            "(void) javan_runtime_class_literal(\"[Lcom.acme.Item;\", 0, 0, 0, 0);"
        );

        assertThat(panic).isEqualTo("conflicting runtime class metadata\n");
    }

    @Test
    void runtimeClassLiteralKeepsEnumMetadataMonotonic() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* first = javan_runtime_class_literal("com.acme.Mode", 17, 1, 0, 0);
                void* repeated = javan_runtime_class_literal("com.acme.Mode", 17, 0, 0, 0);
                printf("%d:%d\\n", first == repeated, javan_class_is_enum(first));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:1\n");
    }

    @Test
    void runtimeClassLiteralPromotesUnknownEnumWhenDescriptorArrives() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                JavanTypeDescriptor descriptors[] = {{17, "com.acme.Mode", 1, 0, NULL}};
                javan_register_static_roots(0, 0);
                void* unknown = javan_runtime_class_literal("com.acme.Mode", 0, 0, 0, 0);
                javan_register_type_descriptors(descriptors, 1);
                void* promoted = javan_runtime_class_literal("com.acme.Mode", 17, 1, 0, 1, 17);
                printf("%d:%d:%d\\n", unknown == promoted, javan_class_exact_type_id(promoted), javan_class_is_enum(promoted));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:17:1\n");
    }

    @Test
    void runtimeClassLiteralRejectsDescriptorEnumDemotion() throws Exception {
        final String panic = runRuntimePanicProbe(
            """
            JavanTypeDescriptor enum_descriptor[] = {{17, "com.acme.Mode", 1, 0, NULL}};
            JavanTypeDescriptor class_descriptor[] = {{17, "com.acme.Mode", 0, 0, NULL}};
            javan_register_static_roots(0, 0);
            javan_register_type_descriptors(enum_descriptor, 1);
            (void) javan_runtime_class_literal("com.acme.Mode", 17, 1, 0, 1, 17);
            javan_register_type_descriptors(class_descriptor, 1);
            """,
            "(void) javan_runtime_class_literal(\"com.acme.Mode\", 17, 0, 0, 1, 17);"
        );

        assertThat(panic).isEqualTo("conflicting runtime class metadata\n");
    }

    @Test
    void runtimeClassLiteralRejectsUnknownRawStatePointer() throws Exception {
        final String panic = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            "(void) javan_class_is_enum((void*) (uintptr_t) 1);"
        );

        assertThat(panic).isEqualTo("unsupported runtime class\n");
    }

    @Test
    void runtimeClassRegistryRejectsCorruptSingleStorageIndex() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_class_registry_rejects_corrupt_storage(void) {
                (void) javan_runtime_class_literal("com.acme.Registry", 17, 0, 0, 0);
                javan_runtime_lock_enter();
                javan_runtime_class_state** names = javan_runtime_classes.names;
                javan_runtime_classes.names = names + 1;
                int rejected = javan_runtime_class_registry_ensure_capacity(1) == 0;
                javan_runtime_classes.names = names;
                javan_runtime_lock_leave();
                return rejected;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            extern int javan_test_class_registry_rejects_corrupt_storage(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_registry_rejects_corrupt_storage());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassCheckedUnlockedRequiresRuntimeLock() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_class_checked_requires_lock(void) {
                void* klass = javan_runtime_class_literal("com.acme.Lock", 17, 0, 0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    (void) javan_runtime_class_checked_unlocked(klass);
                    return 0;
                }
                return strcmp(javan_last_error(), "runtime class registry lookup requires runtime lock") == 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            extern int javan_test_class_checked_requires_lock(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_checked_requires_lock());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassStateValidationRequiresRuntimeLock() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_class_state_validation_requires_lock(void) {
                void* klass = javan_runtime_class_literal("com.acme.Lock", 17, 0, 0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    (void) javan_runtime_class_state_valid_unlocked((javan_runtime_class_state*) klass);
                    return 0;
                }
                return strcmp(javan_last_error(), "runtime class registry validation requires runtime lock") == 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            extern int javan_test_class_state_validation_requires_lock(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_state_validation_requires_lock());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassLiteralRollsBackPromotionOnRawAllocationFailure() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(new RuntimeReplacement(
                "static void* javan_raw_calloc_retry(unsigned long size) {",
                """
                static int javan_test_raw_allocations_before_failure = -1;

                static void* javan_raw_calloc_retry(unsigned long size) {
                    if (javan_test_raw_allocations_before_failure == 0) {
                        return NULL;
                    }
                    if (javan_test_raw_allocations_before_failure > 0) {
                        javan_test_raw_allocations_before_failure--;
                    }
                """.stripTrailing()
            )),
            """
            int javan_test_class_promotion_rollback(void) {
                void* klass = javan_runtime_class_literal("com.acme.Rollback", 0, 0, 0, 1, 1);
                jmp_buf target;
                javan_panic_set_target(&target);
                javan_test_raw_allocations_before_failure = 1;
                if (setjmp(target) == 0) {
                    (void) javan_runtime_class_literal("com.acme.Rollback", 17, 0, 0, 1, 2);
                    return 0;
                }
                javan_test_raw_allocations_before_failure = -1;
                javan_panic_clear_target(&target);
                javan_clear_error();
                javan_runtime_lock_enter();
                javan_runtime_class_state* state = javan_runtime_class_checked_unlocked(klass);
                int rolled_back = state->exact_type_id == 0
                    && state->assignable_count == 1
                    && state->assignable_type_ids[0] == 1;
                javan_runtime_lock_leave();
                void* retried = javan_runtime_class_literal("com.acme.Rollback", 17, 0, 0, 1, 2);
                javan_runtime_lock_enter();
                state = javan_runtime_class_checked_unlocked(klass);
                int recovered = retried == klass
                    && state->exact_type_id == 17
                    && state->assignable_count == 2
                    && state->assignable_type_ids[0] == 1
                    && state->assignable_type_ids[1] == 2;
                javan_runtime_lock_leave();
                return rolled_back != 0 && recovered != 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            extern int javan_test_class_promotion_rollback(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_promotion_rollback());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassLiteralRollsBackFirstPublicationOnIndexStorageFailure() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(new RuntimeReplacement(
                "static void* javan_raw_calloc_retry(unsigned long size) {",
                """
                static int javan_test_raw_allocations_before_failure = -1;

                static void* javan_raw_calloc_retry(unsigned long size) {
                    if (javan_test_raw_allocations_before_failure == 0) {
                        return NULL;
                    }
                    if (javan_test_raw_allocations_before_failure > 0) {
                        javan_test_raw_allocations_before_failure--;
                    }
                """.stripTrailing()
            )),
            """
            int javan_test_class_first_publication_rollback(void) {
                jmp_buf target;
                javan_panic_set_target(&target);
                javan_test_raw_allocations_before_failure = 2;
                if (setjmp(target) == 0) {
                    (void) javan_runtime_class_literal("com.acme.First", 17, 0, 0, 0);
                    return 0;
                }
                javan_test_raw_allocations_before_failure = -1;
                javan_panic_clear_target(&target);
                javan_clear_error();
                int rolled_back = javan_runtime_classes.names == NULL
                    && javan_runtime_classes.pointers == NULL
                    && javan_runtime_classes.storage == NULL
                    && javan_runtime_classes.length == 0
                    && javan_runtime_classes.capacity == 0;
                void* retried = javan_runtime_class_literal("com.acme.First", 17, 0, 0, 0);
                return rolled_back != 0 && retried != NULL && javan_runtime_classes.length == 1;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            extern int javan_test_class_first_publication_rollback(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_first_publication_rollback());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassRegistryCleanupResetsRawOwnershipAndManagedCounters() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_class_registry_cleanup(void) {
                (void) javan_runtime_class_literal("com.acme.Cleanup", 17, 0, 0, 0);
                javan_allocator_cleanup();
                int reset = javan_runtime_classes.names == NULL
                    && javan_runtime_classes.pointers == NULL
                    && javan_runtime_classes.storage == NULL
                    && javan_runtime_classes.length == 0
                    && javan_runtime_classes.capacity == 0
                    && javan_heap_live_allocations() == 0
                    && javan_heap_live_bytes() == 0
                    && javan_heap_total_allocations() == 0;
                void* reused = javan_runtime_class_literal("com.acme.Cleanup", 17, 0, 0, 0);
                return reset != 0
                    && reused != NULL
                    && javan_runtime_classes.length == 1
                    && javan_heap_live_allocations() == 0
                    && javan_heap_total_allocations() == 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            extern int javan_test_class_registry_cleanup(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_registry_cleanup());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassLiteralConcurrentLookupAndPromotionUsesOneCanonicalState() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            canonicalClassConcurrentPromotionSource(),
            "4096"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeClassLiteralConcurrentPromotionWin32BranchCrossCompilesWhenMinGwIsAvailable() throws Exception {
        final Path compiler = findFirstExecutableOnPath("x86_64-w64-mingw32-gcc");
        assumeTrue(compiler != null, "MinGW cross compiler is not installed");
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path source = tempDir.resolve("canonical-class-concurrent-probe.c");
        Files.writeString(source, canonicalClassConcurrentPromotionSource());
        final TestProcesses.Result mainCompile = TestProcesses.run(
            tempDir,
            mingwCompileCommand(compiler, source, tempDir.resolve("canonical-class-concurrent-probe.o")),
            java.time.Duration.ofSeconds(60)
        );
        final TestProcesses.Result runtimeCompile = TestProcesses.run(
            tempDir,
            mingwCompileCommand(compiler, runtime, tempDir.resolve("canonical-class-concurrent-runtime.o")),
            java.time.Duration.ofSeconds(60)
        );

        assertThat(List.of(mainCompile.exitCode(), runtimeCompile.exitCode()))
            .describedAs(mainCompile.stderr() + runtimeCompile.stderr())
            .containsExactly(0, 0);
    }

    private String canonicalClassConcurrentPromotionSource() {
        return """
            #include "javan_runtime.h"
            #include <stdatomic.h>
            #include <stdio.h>
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #endif

            enum { worker_count = 8 };

            typedef struct {
                int type_id;
                atomic_int* arrived;
                atomic_int* released;
                void** result;
            } worker_arg;

            #if defined(_WIN32)
            static unsigned __stdcall class_worker(void* raw) {
            #else
            static void* class_worker(void* raw) {
            #endif
                worker_arg* arg = (worker_arg*) raw;
                atomic_fetch_add(arg->arrived, 1);
                while (atomic_load(arg->released) == 0) {
                }
                *arg->result = javan_runtime_class_literal(
                    "com.acme.Concurrent", 17, 0, 0, 1, arg->type_id
                );
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                atomic_int arrived = 0;
                atomic_int released = 0;
                void* values[worker_count] = { NULL };
                worker_arg arguments[worker_count];
                javan_register_static_roots(0, 0);
                void* canonical_state = javan_runtime_class_literal("com.acme.Concurrent", 0, 0, 0, 0);
                #if defined(_WIN32)
                HANDLE threads[worker_count];
                #else
                pthread_t threads[worker_count];
                #endif
                for (int index = 0; index < worker_count; index++) {
                    arguments[index] = (worker_arg) { index + 1, &arrived, &released, &values[index] };
                    #if defined(_WIN32)
                    threads[index] = (HANDLE) _beginthreadex(NULL, 0, class_worker, &arguments[index], 0, NULL);
                    if (threads[index] == NULL) {
                        return 2;
                    }
                    #else
                    if (pthread_create(&threads[index], NULL, class_worker, &arguments[index]) != 0) {
                        return 2;
                    }
                    #endif
                }
                while (atomic_load(&arrived) != worker_count) {
                }
                atomic_store(&released, 1);
                for (int index = 0; index < worker_count; index++) {
                    #if defined(_WIN32)
                    if (WaitForSingleObject(threads[index], INFINITE) != WAIT_OBJECT_0) {
                        return 3;
                    }
                    CloseHandle(threads[index]);
                    #else
                    if (pthread_join(threads[index], NULL) != 0) {
                        return 3;
                    }
                    #endif
                }
                int canonical = javan_class_exact_type_id(canonical_state) == 17 && values[0] == canonical_state;
                for (int index = 1; index < worker_count; index++) {
                    canonical = canonical != 0 && values[index] == canonical_state;
                }
                for (int index = 0; index < worker_count; index++) {
                    char name[32];
                    snprintf(name, sizeof(name), "com.acme.Source%d", index);
                    void* source = javan_runtime_class_literal(name, index + 1, 0, 0, 0);
                    canonical = canonical != 0 && javan_class_is_assignable_from(values[0], source);
                }
                printf("%d\\n", canonical);
                return 0;
            }
            """;
    }

    @Test
    void runtimeClassLiteralRejectsFree() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_class_rejects_free(void) {
                void* klass = javan_runtime_class_literal("com.acme.Immutable", 17, 0, 0, 0);
                jmp_buf free_target;
                javan_panic_set_target(&free_target);
                if (setjmp(free_target) == 0) {
                    javan_free(klass);
                    return 0;
                }
                return strcmp(javan_last_error(), "cannot free runtime class metadata") == 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            extern int javan_test_class_rejects_free(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_rejects_free());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeClassLiteralRejectsReallocation() throws Exception {
        assertThat(runInstrumentedRuntimeProbe(
            List.of(),
            """
            int javan_test_class_rejects_realloc(void) {
                void* klass = javan_runtime_class_literal("com.acme.Immutable", 17, 0, 0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    (void) javan_realloc(klass, 64);
                    return 0;
                }
                return strcmp(javan_last_error(), "cannot reallocate runtime class metadata") == 0;
            }
            """,
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>
            #include <string.h>

            extern int javan_test_class_rejects_realloc(void);

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_test_class_rejects_realloc());
                return 0;
            }
            """,
            "4096",
            Map.of(),
            java.time.Duration.ofSeconds(30)
        )).isEqualTo("0\n1\n");
    }

    @Test
    void runtimeObjectArrayGetClassPreservesExactBinaryName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_object_array_new(1, "[Ljava.lang.String;");
                void* klass = javan_object_get_class(array);
                printf("%s\\n", (char*) javan_runtime_class_get_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("[Ljava.lang.String;\n");
    }

    @Test
    void runtimeObjectArrayCopyPreservesExactBinaryName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* source = javan_object_array_new(2, "[Ljava.lang.String;");
                void* copy = javan_arrays_copy_of_object(source, 4);
                void* klass = javan_object_get_class(copy);
                printf("%s\\n", (char*) javan_runtime_class_get_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("[Ljava.lang.String;\n");
    }

    @Test
    void runtimeAsciiStringHashCodeMatchesJavaValue() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_string_hash_code("javan"));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("100899468\n");
    }

    @Test
    void runtimeSupplementaryUtf8StringHashCodeMatchesJavaUtf16Semantics() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                const char* value = "x" "\\xF0\\x9F\\x99\\x82" "y";
                printf("%d\\n", javan_string_hash_code(value));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("58536956\n");
    }

    @Test
    void runtimeClassDescriptorStringConvertsObjectArrayNameToDescriptor() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[Ljava.lang.String;", 0, 0, 1, 0);
                printf("%s\\n", (char*) javan_class_descriptor_string(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("[Ljava/lang/String;\n");
    }

    @Test
    void runtimePrimitiveArrayClassComponentTypeReturnsPrimitiveName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_int_array_new(1);
                void* klass = javan_object_get_class(array);
                void* component = javan_class_component_type(klass);
                printf("%s\\n", (char*) javan_runtime_class_get_name(component));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("int\n");
    }

    @Test
    void runtimeClassGetComponentTypeMatchesComponentTypeSemantics() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[Ljava.lang.String;", 0, 0, 1, 0);
                void* component = javan_class_component_type(klass);
                printf("%s\\n", (char*) javan_runtime_class_get_name(component));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("java.lang.String\n");
    }

    @Test
    void runtimeReferenceClassArrayTypeReturnsReferenceArrayName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("java.lang.String", -2001, 0, 0, 0);
                void* array_type = javan_class_array_type(klass);
                printf("%s\\n", (char*) javan_runtime_class_get_name(array_type));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("[Ljava.lang.String;\n");
    }

    @Test
    void runtimePrimitiveClassArrayTypeReturnsPrimitiveArrayName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("int", -2011, 0, 0, 0);
                void* array_type = javan_class_array_type(klass);
                printf("%s\\n", (char*) javan_runtime_class_get_name(array_type));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("[I\n");
    }

    @Test
    void runtimePrimitiveClassIsPrimitiveReturnsTrue() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("int", -2011, 0, 0, 0);
                printf("%d\\n", javan_class_is_primitive(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeReferenceClassIsPrimitiveReturnsFalse() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("java.lang.String", -2001, 0, 0, 0);
                printf("%d\\n", javan_class_is_primitive(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeVoidPrimitiveClassGetNameReturnsVoid() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("void", -2015, 0, 0, 0);
                printf("%s\\n", (char*) javan_runtime_class_get_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("void\n");
    }

    @Test
    void runtimeReferenceArrayTypeNameReturnsDisplayName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[Ljava.lang.String;", 0, 0, 1, 0);
                printf("%s\\n", (char*) javan_class_type_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("java.lang.String[]\n");
    }

    @Test
    void runtimeReferenceClassSimpleNameReturnsLeafTypeName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("java.lang.String", -2001, 0, 0, 0);
                printf("%s\\n", (char*) javan_class_simple_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("String\n");
    }

    @Test
    void runtimeReferenceArraySimpleNameReturnsDisplayName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[Ljava.util.Map$Entry;", 0, 0, 1, 0);
                printf("%s\\n", (char*) javan_class_simple_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("Entry[]\n");
    }

    @Test
    void runtimePrimitiveNestedArraySimpleNameReturnsDisplayName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[[I", 0, 0, 1, 0);
                printf("%s\\n", (char*) javan_class_simple_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("int[][]\n");
    }

    @Test
    void runtimeVoidSimpleNameReturnsVoid() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("void", -2015, 0, 0, 0);
                printf("%s\\n", (char*) javan_class_simple_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("void\n");
    }

    @Test
    void runtimePrimitiveNestedArrayTypeNameReturnsDisplayName() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[[I", 0, 0, 1, 0);
                printf("%s\\n", (char*) javan_class_type_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("int[][]\n");
    }

    @Test
    void runtimeReferenceArrayPackageNameUsesElementPackage() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("[Ljava.util.Map$Entry;", 0, 0, 1, 0);
                printf("%s\\n", (char*) javan_class_package_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("java.util\n");
    }

    @Test
    void runtimePrimitivePackageNameIsJavaLang() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("int", -2011, 0, 0, 0);
                printf("%s\\n", (char*) javan_class_package_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("java.lang\n");
    }

    @Test
    void runtimeVoidPackageNameIsJavaLang() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* klass = javan_runtime_class_literal("void", -2015, 0, 0, 0);
                printf("%s\\n", (char*) javan_class_package_name(klass));
                return 0;
            }
            """,
            "128"
        );

        assertThat(stdout).isEqualTo("java.lang\n");
    }

    @Test
    void runtimeArrayAssignableFromUsesComponentTypes() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* object_array = NULL;
                void* string_array = NULL;
                void* int_array = NULL;
                void** roots[] = {
                    (void**) &object_array,
                    (void**) &string_array,
                    (void**) &int_array
                };
                javan_root_frame_push(roots, 3);
                object_array = javan_runtime_class_literal("[Ljava.lang.Object;", 0, 0, 1, 0);
                string_array = javan_runtime_class_literal("[Ljava.lang.String;", 0, 0, 1, 0);
                int_array = javan_runtime_class_literal("[I", 0, 0, 1, 0);
                printf("%d:%d:%d\\n",
                    javan_class_is_assignable_from(object_array, string_array),
                    javan_class_is_assignable_from(string_array, object_array),
                    javan_class_is_assignable_from(object_array, int_array));
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1:0:0\n");
    }

    @Test
    void runtimeObjectClassIsNotAssignableFromPrimitiveClass() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* object_class = NULL;
                void* int_class = NULL;
                void** roots[] = {
                    (void**) &object_class,
                    (void**) &int_class
                };
                javan_root_frame_push(roots, 2);
                object_class = javan_runtime_class_literal("java.lang.Object", -2002, 0, 0, 0);
                int_class = javan_runtime_class_literal("int", -2011, 0, 0, 0);
                printf("%d\\n", javan_class_is_assignable_from(object_class, int_class));
                javan_root_frame_pop(roots);
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("0\n");
    }

    @Test
    void runtimeArrayRangeCopyRootsSourceAcrossTargetAllocation() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 6; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                signed char data[8] = {1, 2, 3, 4, 5, 6, 7, 8};
                void* source = javan_byte_array_from(data, 8);
                seed_dead_strings();
                void* copy = javan_arrays_copy_of_range_byte(source, 2, 66);
                printf("%d:%d:%d\\n", javan_array_length(copy), javan_byte_array_get(copy, 0), javan_byte_array_get(copy, 5));
                return 0;
            }
            """,
            "180"
        );

        assertThat(stdout).isEqualTo("64:3:8\n");
    }

    @Test
    void runtimeByteArrayEqualsAcceptsTwoNullReferences() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                printf("%d\\n", javan_arrays_equals_byte(NULL, NULL));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void runtimeByteArrayEqualsAcceptsDistinctEqualSignedValues() throws Exception {
        assertThat(byteArraysEqualProbe("-128, -1, 0, 127", 4, "-128, -1, 0, 127", 4)).isEqualTo("1\n");
    }

    @Test
    void runtimeByteArrayEqualsRejectsDifferentValues() throws Exception {
        assertThat(byteArraysEqualProbe("1, 2", 2, "1, 3", 2)).isEqualTo("0\n");
    }

    @Test
    void runtimeByteArrayEqualsRejectsDifferentLengths() throws Exception {
        assertThat(byteArraysEqualProbe("1", 1, "1, 2", 2)).isEqualTo("0\n");
    }

    @Test
    void runtimeByteArrayEqualsRejectsIdenticalWrongKindReferences() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                void* values = javan_int_array_new(1);
                (void) javan_arrays_equals_byte(values, values);
                return 2;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("array copy type mismatch\n");
    }

    private String byteArraysEqualProbe(
        final String leftData,
        final int leftLength,
        final String rightData,
        final int rightLength
    ) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                signed char left_data[] = {%s};
                signed char right_data[] = {%s};
                void* left = NULL;
                void* right = NULL;
                void** roots[] = {
                    (void**) &left,
                    (void**) &right
                };
                javan_root_frame_push(roots, 2);
                left = javan_byte_array_from(left_data, %d);
                right = javan_byte_array_from(right_data, %d);
                printf("%%d\\n", javan_arrays_equals_byte(left, right));
                javan_root_frame_pop(roots);
                return 0;
            }
            """.formatted(leftData, rightData, leftLength, rightLength),
            "4096"
        );
    }

    @Test
    void runtimeDirectoryStreamRootsSourcePathAcrossResultAndChildAllocations() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>
            #include <sys/stat.h>

            static void touch_file(const char* name) {
                FILE* file = fopen(name, "w");
                if (file == 0) {
                    javan_panic("touch failed");
                }
                fputs("x", file);
                fclose(file);
            }

            static void seed_dead_strings(void) {
                const char* values[] = {"0123456789"};
                for (int index = 0; index < 6; index++) {
                    (void) javan_string_concat("dead-dead-\\001", 1, values);
                }
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                mkdir("data", 0700);
                touch_file("data/b.txt");
                touch_file("data/a.txt");
                seed_dead_strings();
                const char* values[] = {"data"};
                void* path = javan_string_concat("\\001", 1, values);
                void* stream = javan_files_new_directory_stream(path);
                void* iterator = javan_list_iterator(stream);
                while (javan_iterator_has_next(iterator) != 0) {
                    void* child = javan_iterator_next(iterator);
                    printf("%s\\n", (char*) javan_path_get_file_name(child));
                }
                return 0;
            }
            """,
            "512"
        );

        assertThat(stdout).isEqualTo("a.txt\nb.txt\n");
    }

    @Test
    void runtimeProcessResultFreeReleasesOwnedOutputStrings() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* command = javan_arraylist_new();
                void* result = javan_process_run(NULL, command, 10);
                printf(
                    "%d:%s:%lu\\n",
                    javan_process_result_exit_code(result),
                    (char*) javan_process_result_stderr(result),
                    javan_heap_live_allocations()
                );
                javan_free(result);
                printf("after-result=%lu\\n", javan_heap_live_allocations());
                javan_free(command);
                printf("after-all=%lu\\n", javan_heap_live_allocations());
                return 0;
            }
            """,
            "512"
        );

        assertThat(stdout).isEqualTo("127:empty command:4\nafter-result=1\nafter-all=0\n");
    }

    @Test
    void runtimeCollectsLongLivedAllocationSoakToZeroLiveHeap() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                int checksum = 0;
                for (int round = 0; round < 250; round++) {
                    void* values = javan_int_array_new(16);
                    javan_int_array_set(values, 0, round);
                    javan_int_array_set(values, 15, round + 15);

                    void* bytes = javan_byte_array_new(16);
                    javan_byte_array_set(bytes, 3, round & 127);

                    const char* parts[] = {"payload"};
                    void* key = javan_string_concat("key-\\001", 1, parts);
                    void* list = javan_arraylist_new();
                    (void) javan_arraylist_add(list, key);

                    void* map = javan_hashmap_new();
                    (void) javan_map_put(map, key, list);

                    void* optional = javan_optional_of(map);
                    void* builder = javan_stringbuilder_new();
                    (void) javan_stringbuilder_append_string(builder, key);
                    (void) javan_stringbuilder_append_int(builder, round);
                    void* built = javan_stringbuilder_to_string(builder);

                    checksum += javan_int_array_get(values, 0);
                    checksum += javan_int_array_get(values, 15);
                    checksum += javan_byte_array_get(bytes, 3);
                    checksum += javan_list_size(list);
                    checksum += javan_map_size(map);
                    checksum += javan_optional_is_present(optional);
                    checksum += javan_string_length((const char*) built);
                }
                javan_gc_collect();
                javan_validate_heap_metadata();
                printf("checksum=%d\\n", checksum);
                printf("live=%lu\\n", javan_heap_live_allocations());
                printf("bytes=%lu\\n", javan_heap_live_bytes());
                printf("allocated=%d\\n", javan_heap_total_allocations() >= 2500);
                printf("collections=%d\\n", javan_heap_gc_collections() >= 1);
                printf("collected=%d\\n", javan_heap_gc_collected_allocations() >= 2500);
                return 0;
            }
            """,
            "1048576",
            Map.of(),
            java.time.Duration.ofSeconds(180)
        );

        assertThat(stdout).isEqualTo(
            """
            checksum=85649
            live=0
            bytes=0
            allocated=1
            collections=1
            collected=1
            """
        );
    }

    private record RuntimeProbeOutput(String stdout, String stderr) {
    }

    @Test
    void stringIsBlankRejectsNullReceiver() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            "(void) javan_string_is_blank(NULL);"
        );

        assertThat(stdout).isEqualTo("null string\n");
    }

    @Test
    void stringIsBlankRejectsTruncatedUtf8() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xe2, (char) 0x80, 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsOverlongUtf8() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xc0, (char) 0x80, 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsInvalidUtf8Continuation() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xe2, ' ', (char) 0x80, 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsOutOfRangeUtf8() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xf4, (char) 0x90, (char) 0x80, (char) 0x80, 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsOverlongThreeByteUtf8() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xe0, (char) 0x80, (char) 0x80, 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsOverlongFourByteUtf8() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xf0, (char) 0x80, (char) 0x80, (char) 0x80, 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsInvalidThirdUtf8Continuation() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xe2, (char) 0x80, ' ', 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void stringIsBlankRejectsInvalidFourthUtf8Continuation() throws Exception {
        final String stdout = runRuntimePanicProbe(
            "javan_register_static_roots(0, 0);",
            """
            const char value[] = {(char) 0xf0, (char) 0x90, (char) 0x80, ' ', 0};
            (void) javan_string_is_blank(value);
            """
        );

        assertThat(stdout).isEqualTo("invalid UTF-8 string\n");
    }

    @Test
    void floatToRawIntBitsPreservesQuietNanPayload() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                float value = javan_float_int_bits_to_float(0x7fc01234);
                printf("%08x\\n", (unsigned int) javan_float_to_raw_int_bits(value));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("7fc01234\n");
    }

    @Test
    void floatIsFiniteAcceptsMaximumValue() throws Exception {
        assertThat(floatIsFiniteBits("7f7fffff")).isEqualTo("1\n");
    }

    @Test
    void floatIsFiniteRejectsInfinity() throws Exception {
        assertThat(floatIsFiniteBits("7f800000")).isEqualTo("0\n");
    }

    @Test
    void floatIsFiniteRejectsNan() throws Exception {
        assertThat(floatIsFiniteBits("7fc01234")).isEqualTo("0\n");
    }

    private String floatIsFiniteBits(final String bits) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_float_is_finite(javan_float_int_bits_to_float(0x%s))
                );
                return 0;
            }
            """.formatted(bits),
            "4096"
        );
    }

    private String doubleToFloatBits(final String bits) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <inttypes.h>
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            static double from_bits(uint64_t value) {
                double result = 0.0;
                memcpy(&result, &value, sizeof(result));
                return result;
            }

            int main(void) {
                uint32_t result = UINT32_C(0);
                const float narrowed = javan_d2f(from_bits(%s));
                memcpy(&result, &narrowed, sizeof(result));
                printf("%%08" PRIx32 "\\n", result);
                return 0;
            }
            """.formatted(bits),
            "512"
        );
    }

    @Test
    void doubleIsFiniteAcceptsMaximumValue() throws Exception {
        assertThat(doubleIsFiniteBits("7fefffffffffffff")).isEqualTo("1\n");
    }

    @Test
    void doubleIsFiniteRejectsInfinity() throws Exception {
        assertThat(doubleIsFiniteBits("7ff0000000000000")).isEqualTo("0\n");
    }

    @Test
    void doubleIsFiniteRejectsNan() throws Exception {
        assertThat(doubleIsFiniteBits("7ff8000000001234")).isEqualTo("0\n");
    }

    private String doubleIsFiniteBits(final String bits) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_double_is_finite(javan_double_long_bits_to_double(0x%sLL))
                );
                return 0;
            }
            """.formatted(bits),
            "4096"
        );
    }

    @Test
    void mathFloorPreservesNegativeZeroBits() throws Exception {
        assertThat(mathFloorBits("8000000000000000")).isEqualTo("8000000000000000\n");
    }

    @Test
    void mathFloorPreservesQuietNanBits() throws Exception {
        assertThat(mathFloorBits("7ff8000000001234")).isEqualTo("7ff8000000001234\n");
    }

    @Test
    void mathFloorPreservesNegativeQuietNanBits() throws Exception {
        assertThat(mathFloorBits("fff8000000005678")).isEqualTo("fff8000000005678\n");
    }

    @Test
    void mathFloorPreservesPositiveSignalingNanBits() throws Exception {
        assertThat(mathFloorBits("7ff0000000001234")).isEqualTo("7ff0000000001234\n");
    }

    @Test
    void mathFloorPreservesNegativeSignalingNanBits() throws Exception {
        assertThat(mathFloorBits("fff0000000005678")).isEqualTo("fff0000000005678\n");
    }

    @Test
    void mathFloorPreservesPositiveTwoToTheFiftyTwoBits() throws Exception {
        assertThat(mathFloorBits("4330000000000000")).isEqualTo("4330000000000000\n");
    }

    @Test
    void mathFloorPreservesNegativeTwoToTheFiftyTwoBits() throws Exception {
        assertThat(mathFloorBits("c330000000000000")).isEqualTo("c330000000000000\n");
    }

    @Test
    void mathFloorPreservesPositiveMaximumDoubleBits() throws Exception {
        assertThat(mathFloorBits("7fefffffffffffff")).isEqualTo("7fefffffffffffff\n");
    }

    @Test
    void mathFloorPreservesNegativeMaximumDoubleBits() throws Exception {
        assertThat(mathFloorBits("ffefffffffffffff")).isEqualTo("ffefffffffffffff\n");
    }

    @Test
    void generatedRuntimeFloorRejectsNonBinary64Targets() throws Exception {
        assertThat(Files.readString(new RuntimeFiles().write(tempDir))).contains(
            "#if CHAR_BIT != 8 || FLT_RADIX != 2 || DBL_MANT_DIG != 53 || DBL_MIN_EXP != -1021 || DBL_MAX_EXP != 1024"
        );
    }

    @Test
    void generatedRuntimeFloorRequiresEightByteDouble() throws Exception {
        assertThat(Files.readString(new RuntimeFiles().write(tempDir))).contains(
            "_Static_assert(sizeof(double) == 8, \"Javan requires 64-bit double\");"
        );
    }

    @Test
    void generatedRuntimeFloorHasNoExternalFloorCall() throws Exception {
        assertThat(runtimeFunction(
            Files.readString(new RuntimeFiles().write(tempDir)),
            "double javan_math_floor_double(double value)"
        )).doesNotContain("floor(value)");
    }

    @Test
    void mathCeilPreservesPositiveSignalingNanBits() throws Exception {
        assertThat(mathCeilBits("7ff0000000001234")).isEqualTo("7ff0000000001234\n");
    }

    @Test
    void mathCeilPreservesNegativeSignalingNanBits() throws Exception {
        assertThat(mathCeilBits("fff0000000005678")).isEqualTo("fff0000000005678\n");
    }

    @Test
    void mathCeilPreservesNegativeInfinityBits() throws Exception {
        assertThat(mathCeilBits("fff0000000000000")).isEqualTo("fff0000000000000\n");
    }

    @Test
    void mathCeilPreservesNegativeZeroBits() throws Exception {
        assertThat(mathCeilBits("8000000000000000")).isEqualTo("8000000000000000\n");
    }

    @Test
    void mathCeilRoundsPositiveFractionUp() throws Exception {
        assertThat(mathCeilBits("401f000000000000")).isEqualTo("4020000000000000\n");
    }

    @Test
    void mathCeilRoundsNegativeFractionTowardZero() throws Exception {
        assertThat(mathCeilBits("c01d000000000000")).isEqualTo("c01c000000000000\n");
    }

    @Test
    void mathCeilRoundsPositiveMinimumSubnormalToOne() throws Exception {
        assertThat(mathCeilBits("0000000000000001")).isEqualTo("3ff0000000000000\n");
    }

    @Test
    void mathCeilRoundsNegativeMinimumSubnormalToNegativeZero() throws Exception {
        assertThat(mathCeilBits("8000000000000001")).isEqualTo("8000000000000000\n");
    }

    @Test
    void mathCeilRoundsPositiveTwoToTheFiftyTwoBoundaryUp() throws Exception {
        assertThat(mathCeilBits("432fffffffffffff")).isEqualTo("4330000000000000\n");
    }

    @Test
    void mathCeilRoundsNegativeTwoToTheFiftyTwoBoundaryTowardZero() throws Exception {
        assertThat(mathCeilBits("c32fffffffffffff")).isEqualTo("c32ffffffffffffe\n");
    }

    @Test
    void mathCeilPreservesIntegralTwoToTheFiftyTwoBits() throws Exception {
        assertThat(mathCeilBits("4330000000000000")).isEqualTo("4330000000000000\n");
    }

    @Test
    void generatedRuntimeCeilHasNoExternalCeilCall() throws Exception {
        assertThat(runtimeFunction(
            Files.readString(new RuntimeFiles().write(tempDir)),
            "double javan_math_ceil_double(double value)"
        )).doesNotContain("ceil(value)");
    }

    private String mathFloorBits(final String bits) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const double result = javan_math_floor_double(
                    javan_double_long_bits_to_double(0x%sLL)
                );
                uint64_t result_bits = UINT64_C(0);
                memcpy(&result_bits, &result, sizeof(result_bits));
                printf("%%016llx\\n", (unsigned long long) result_bits);
                return 0;
            }
            """.formatted(bits),
            "4096"
        );
    }

    private String mathCeilBits(final String bits) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const double result = javan_math_ceil_double(
                    javan_double_long_bits_to_double(0x%sLL)
                );
                uint64_t result_bits = UINT64_C(0);
                memcpy(&result_bits, &result, sizeof(result_bits));
                printf("%%016llx\\n", (unsigned long long) result_bits);
                return 0;
            }
            """.formatted(bits),
            "4096"
        );
    }

    @Test
    void addExactLongAcceptsMaximumBoundary() throws Exception {
        assertThat(addExactLongOverflow("LLONG_MAX", "0LL")).isEqualTo("0\n");
    }

    @Test
    void addExactLongRejectsPositiveOverflow() throws Exception {
        assertThat(addExactLongOverflow("LLONG_MAX", "1LL")).isEqualTo("1\n");
    }

    @Test
    void addExactLongAcceptsMinimumBoundary() throws Exception {
        assertThat(addExactLongOverflow("LLONG_MIN", "0LL")).isEqualTo("0\n");
    }

    @Test
    void addExactLongRejectsNegativeOverflow() throws Exception {
        assertThat(addExactLongOverflow("LLONG_MIN", "-1LL")).isEqualTo("1\n");
    }

    private String addExactLongOverflow(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_math_add_exact_long_overflows(%s, %s)
                );
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    @Test
    void subtractExactLongAcceptsZero() throws Exception {
        assertThat(subtractExactLongOverflow("0LL", "0LL")).isEqualTo("0\n");
    }

    @Test
    void subtractExactLongAcceptsSameSignOperands() throws Exception {
        assertThat(subtractExactLongOverflow("-7LL", "-2LL")).isEqualTo("0\n");
    }

    @Test
    void subtractExactLongAcceptsMixedSignOperands() throws Exception {
        assertThat(subtractExactLongOverflow("7LL", "-2LL")).isEqualTo("0\n");
    }

    @Test
    void subtractExactLongRejectsMaximumMinusNegativeOne() throws Exception {
        assertThat(subtractExactLongOverflow("LLONG_MAX", "-1LL")).isEqualTo("1\n");
    }

    @Test
    void subtractExactLongAcceptsMaximumMinusNegativeOneThreshold() throws Exception {
        assertThat(subtractExactLongOverflow("LLONG_MAX - 1LL", "-1LL")).isEqualTo("0\n");
    }

    @Test
    void subtractExactLongRejectsMinimumMinusOne() throws Exception {
        assertThat(subtractExactLongOverflow("LLONG_MIN", "1LL")).isEqualTo("1\n");
    }

    @Test
    void subtractExactLongAcceptsMinimumMinusOneThreshold() throws Exception {
        assertThat(subtractExactLongOverflow("LLONG_MIN + 1LL", "1LL")).isEqualTo("0\n");
    }

    @Test
    void subtractExactLongReturnsSafeDifference() throws Exception {
        assertThat(subtractExactLongResult("7LL", "5LL")).isEqualTo("2\n");
    }

    private String subtractExactLongOverflow(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_math_subtract_exact_long_overflows(%s, %s)
                );
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    private String subtractExactLongResult(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                printf("%%lld\\n", javan_math_subtract_exact_long(%s, %s));
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    private String exactLongUnaryOverflow(final String symbol, final String value) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf("%%d\\n", %s(%s));
                return 0;
            }
            """.formatted(symbol, value),
            "4096"
        );
    }

    private String subtractExactIntOverflow(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_math_subtract_exact_int_overflows(%s, %s)
                );
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    @Test
    void subtractExactIntRejectsMaximumMinusNegativeOne() throws Exception {
        assertThat(subtractExactIntOverflow("INT_MAX", "-1")).isEqualTo("1\n");
    }

    @Test
    void incrementExactLongRejectsMaximumValue() throws Exception {
        assertThat(exactLongUnaryOverflow("javan_math_increment_exact_long_overflows", "LLONG_MAX")).isEqualTo("1\n");
    }

    @Test
    void decrementExactLongRejectsMinimumValue() throws Exception {
        assertThat(exactLongUnaryOverflow("javan_math_decrement_exact_long_overflows", "LLONG_MIN")).isEqualTo("1\n");
    }

    @Test
    void negateExactLongRejectsMinimumValue() throws Exception {
        assertThat(exactLongUnaryOverflow("javan_math_negate_exact_long_overflows", "LLONG_MIN")).isEqualTo("1\n");
    }

    @Test
    void longNegationReturnsZeroForZero() throws Exception {
        assertThat(longNegation("0LL")).isEqualTo("0\n");
    }

    @Test
    void longNegationReturnsNegativeForPositiveValue() throws Exception {
        assertThat(longNegation("73LL")).isEqualTo("-73\n");
    }

    @Test
    void longNegationReturnsPositiveForNegativeValue() throws Exception {
        assertThat(longNegation("-73LL")).isEqualTo("73\n");
    }

    @Test
    void longNegationReturnsNegativeMaximumForLongMaximum() throws Exception {
        assertThat(longNegation("LLONG_MAX")).isEqualTo("-9223372036854775807\n");
    }

    @Test
    void longNegationPreservesLongMinimum() throws Exception {
        assertThat(longNegation("LLONG_MIN")).isEqualTo("-9223372036854775808\n");
    }

    private String longNegation(final String value) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf("%%lld\\n", javan_long_neg(%s));
                return 0;
            }
            """.formatted(value),
            "4096"
        );
    }

    @Test
    void multiplyExactIntAcceptsPositiveOperands() throws Exception {
        assertThat(multiplyExactIntOverflow("2", "3")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsMixedSignOperands() throws Exception {
        assertThat(multiplyExactIntOverflow("2", "-3")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsNegativeOperands() throws Exception {
        assertThat(multiplyExactIntOverflow("-2", "-3")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsZeroWithMinimumValue() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MIN", "0")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsMaximumBoundary() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MAX", "1")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsMinimumBoundary() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MIN", "1")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsPositiveThreshold() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MAX / 3", "3")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntAcceptsNegativeThreshold() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MIN / 3", "3")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactIntRejectsPositiveTimesPositiveOverflow() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MAX", "2")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactIntRejectsNegativeTimesPositiveOverflow() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MIN", "2")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactIntRejectsPositiveTimesNegativeOverflow() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MAX", "-2")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactIntRejectsMinimumTimesNegativeOne() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MIN", "-1")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactIntRejectsPositiveThresholdOverflow() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MAX / 3 + 1", "3")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactIntRejectsNegativeThresholdOverflow() throws Exception {
        assertThat(multiplyExactIntOverflow("INT_MIN / 3 - 1", "3")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactIntReturnsSafeProduct() throws Exception {
        assertThat(multiplyExactIntResult("-7", "6")).isEqualTo("-42\n");
    }

    private String multiplyExactIntOverflow(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_math_multiply_exact_int_overflows(%s, %s)
                );
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    private String multiplyExactIntResult(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                printf("%%d\\n", javan_math_multiply_exact_int(%s, %s));
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    @Test
    void multiplyExactLongIntDetectsMinimumTimesNegativeOne() throws Exception {
        final String stdout = runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf("%d\\n", javan_math_multiply_exact_long_int_overflows(LLONG_MIN, -1));
                return 0;
            }
            """,
            "4096"
        );

        assertThat(stdout).isEqualTo("1\n");
    }

    @Test
    void multiplyExactLongLongDetectsMinimumTimesNegativeOne() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MIN", "-1LL")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactLongLongAcceptsPositiveThreshold() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MAX / 3LL", "3LL")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactLongLongRejectsPositiveThresholdOverflow() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MAX / 3LL + 1LL", "3LL")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactLongLongAcceptsNegativeThreshold() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MIN / 3LL", "3LL")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactLongLongRejectsNegativeThresholdOverflow() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MIN / 3LL - 1LL", "3LL")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactLongLongRejectsPositiveTimesNegativeOverflow() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MAX", "-2LL")).isEqualTo("1\n");
    }

    @Test
    void multiplyExactLongLongAcceptsPositiveTimesNegative() throws Exception {
        assertThat(multiplyExactLongLongOverflow("2LL", "-3LL")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactLongLongAcceptsNegativeTimesNegative() throws Exception {
        assertThat(multiplyExactLongLongOverflow("-2LL", "-3LL")).isEqualTo("0\n");
    }

    @Test
    void multiplyExactLongLongAcceptsZeroMultiplier() throws Exception {
        assertThat(multiplyExactLongLongOverflow("LLONG_MIN", "0LL")).isEqualTo("0\n");
    }

    private String multiplyExactLongLongOverflow(final String left, final String right) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <stdio.h>

            int main(void) {
                printf(
                    "%%d\\n",
                    javan_math_multiply_exact_long_long_overflows(%s, %s)
                );
                return 0;
            }
            """.formatted(left, right),
            "4096"
        );
    }

    private String runRuntimePanicProbe(final String setup, final String statement) throws Exception {
        return runRuntimeBoundaryProbe(
            """
            #include "javan_runtime.h"
            #include <limits.h>
            #include <setjmp.h>
            #include <stdio.h>

            typedef struct {
                int magic;
                int target_id;
                int capture_count;
                void** captures;
            } materialized_lambda_state_probe;

            int main(void) {
                %s
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) == 0) {
                    %s
                    javan_panic_clear_target(&target);
                    puts("no panic");
                    return 0;
                }
                const char* error = javan_last_error();
                printf("%%s\\n", error == NULL ? "missing panic" : error);
                return 0;
            }
            """.formatted(setup, statement),
            "4096"
        );
    }

    private String runRuntimeBoundaryProbe(final String source, final String heapLimitBytes) throws Exception {
        return runRuntimeBoundaryProbe(source, heapLimitBytes, Map.of());
    }

    private String runRuntimeBoundaryProbe(
        final String source,
        final String heapLimitBytes,
        final NativeLinkInputs linkInputs
    ) throws Exception {
        final RuntimeProbeOutput output = runRuntimeBoundaryProbeOutput(
            source,
            heapLimitBytes,
            Map.of(),
            java.time.Duration.ofSeconds(30),
            linkInputs
        );

        assertThat(output.stderr()).isEmpty();
        return output.stdout();
    }

    private String runRuntimeBoundaryProbe(
        final String source,
        final String heapLimitBytes,
        final Map<String, String> environmentOverrides
    ) throws Exception {
        return runRuntimeBoundaryProbe(source, heapLimitBytes, environmentOverrides, java.time.Duration.ofSeconds(30));
    }

    private String runRuntimeBoundaryProbe(
        final String source,
        final String heapLimitBytes,
        final Map<String, String> environmentOverrides,
        final java.time.Duration timeout
    ) throws Exception {
        final RuntimeProbeOutput output = runRuntimeBoundaryProbeOutput(source, heapLimitBytes, environmentOverrides, timeout);

        assertThat(output.stderr()).isEmpty();
        return output.stdout();
    }

    private String runRuntimeBoundaryProbeStderr(final String source, final String heapLimitBytes) throws Exception {
        final RuntimeProbeOutput output = runRuntimeBoundaryProbeOutput(
            source,
            heapLimitBytes,
            Map.of(),
            java.time.Duration.ofSeconds(30)
        );

        assertThat(output.stdout()).isEmpty();
        return output.stderr();
    }

    private RuntimeProbeOutput runRuntimeBoundaryProbeOutput(
        final String source,
        final String heapLimitBytes,
        final Map<String, String> environmentOverrides,
        final java.time.Duration timeout
    ) throws Exception {
        return runRuntimeBoundaryProbeOutput(
            source,
            heapLimitBytes,
            environmentOverrides,
            timeout,
            NativeLinkInputs.empty()
        );
    }

    private RuntimeProbeOutput runRuntimeBoundaryProbeOutput(
        final String source,
        final String heapLimitBytes,
        final Map<String, String> environmentOverrides,
        final java.time.Duration timeout,
        final NativeLinkInputs linkInputs
    ) throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("probe.c");
        Files.writeString(main, source);
        final Path binary = new NativeLinker().link(
            tempDir,
            main,
            runtime,
            tempDir.resolve("probe"),
            linkInputs,
            List.of()
        );
        final Map<String, String> environment = new java.util.LinkedHashMap<>();
        environment.put("JAVAN_HEAP_LIMIT_BYTES", heapLimitBytes);
        environment.put("JAVAN_GC_STRESS", "1");
        environment.putAll(environmentOverrides);

        final TestProcesses.Result result = TestProcesses.run(
            tempDir,
            java.util.List.of(binary.toString()),
            timeout,
            environment
        );
        if (result.exitCode() == 124) {
            throw new AssertionError(
                "Runtime boundary probe timed out after "
                    + timeout.toSeconds()
                    + " seconds.\nstdout:\n"
                    + result.stdout()
                    + "\nstderr:\n"
                    + result.stderr()
            );
        }

        assertThat(result.exitCode())
            .describedAs(result.stderr())
            .isEqualTo(0);
        return new RuntimeProbeOutput(result.stdout(), result.stderr());
    }

    private static NativeLinkInputs fenvLinkInputs() {
        final String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!osName.contains("linux")) {
            return NativeLinkInputs.empty();
        }
        return new NativeLinkInputs(List.of(), List.of(), List.of(), List.of("m"), List.of());
    }

    private static boolean isWindowsHost() {
        final String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static String runtimeFunction(final String source, final String signature) {
        final int start = source.lastIndexOf(signature);
        assertThat(start).as(signature).isGreaterThanOrEqualTo(0);
        final int end = source.indexOf("\n}\n", start);
        assertThat(end).as(signature).isGreaterThan(start);
        return source.substring(start, end + 2);
    }

    private static Path findFirstExecutableOnPath(final String... executables) {
        final String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (final String executable : executables) {
            for (final String entry : path.split(File.pathSeparator)) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                final Path candidate = Path.of(entry).resolve(executable);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}

package javan.codegen;

import javan.TestProcesses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
@ResourceLock("native-cli-heavy")
final class RuntimeNativeImportByteArrayTest {
    @TempDir
    private Path tempDir;

    @Test
    void borrowsExactMutableByteArrayStorageWithoutAllocation() throws Exception {
        final TestProcesses.Result result = runProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_byte_array_new(3);
                javan_byte_array_set(array, 0, 7);
                javan_byte_array_set(array, 1, -2);
                javan_byte_array_set(array, 2, 11);
                unsigned long allocations = javan_heap_total_allocations();
                JavanNativeImportedByteArray first = javan_native_import_byte_array(array);
                JavanNativeImportedByteArray second = javan_native_import_byte_array(array);
                first.data[1] = 42;
                printf(
                    "%d:%d:%d:%d\\n",
                    first.data == second.data,
                    first.length,
                    javan_byte_array_get(array, 1),
                    allocations == javan_heap_total_allocations()
                );
                return 0;
            }
            """);

        assertThat(result).isEqualTo(new TestProcesses.Result(0, "1:3:42:1\n", ""));
    }

    @Test
    void borrowsEmptyByteArrayWithoutAllocation() throws Exception {
        final TestProcesses.Result result = runProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_byte_array_new(0);
                unsigned long allocations = javan_heap_total_allocations();
                JavanNativeImportedByteArray view = javan_native_import_byte_array(array);
                printf(
                    "%d:%d:%d\\n",
                    view.data != NULL,
                    view.length,
                    allocations == javan_heap_total_allocations()
                );
                return 0;
            }
            """);

        assertThat(result).isEqualTo(new TestProcesses.Result(0, "1:0:1\n", ""));
    }

    @Test
    void rejectsNullNativeImportByteArrayBeforeReturningAView() throws Exception {
        final TestProcesses.Result result = runProbe("""
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
                (void) javan_native_import_byte_array(NULL);
                return 2;
            }
            """);

        assertThat(result).isEqualTo(new TestProcesses.Result(
            0,
            "native import byte[] argument is null\n",
            ""
        ));
    }

    @Test
    void rejectsNonByteArrayBeforeReturningAView() throws Exception {
        final TestProcesses.Result result = runProbe("""
            #include "javan_runtime.h"
            #include <setjmp.h>
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_int_array_new(1);
                jmp_buf target;
                javan_panic_set_target(&target);
                if (setjmp(target) != 0) {
                    printf("%s\\n", javan_last_error());
                    return 0;
                }
                (void) javan_native_import_byte_array(array);
                return 2;
            }
            """);

        assertThat(result).isEqualTo(new TestProcesses.Result(
            0,
            "native import argument is not byte[]\n",
            ""
        ));
    }

    @Test
    void callerRootKeepsBorrowedStorageStableAcrossForcedCollection() throws Exception {
        final TestProcesses.Result result = runProbe("""
            #include "javan_runtime.h"
            #include <stdio.h>

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_byte_array_new(2);
                javan_byte_array_set(array, 0, 5);
                javan_byte_array_set(array, 1, 9);
                void** roots[] = {(void**) &array};
                javan_root_frame_push(roots, 1);
                JavanNativeImportedByteArray view = javan_native_import_byte_array(array);
                signed char* data = view.data;
                javan_gc_collect();
                view.data[0] = 31;
                printf(
                    "%d:%d:%d\\n",
                    data == view.data,
                    javan_byte_array_get(array, 0),
                    javan_heap_gc_collections() > 0
                );
                javan_root_frame_pop(roots);
                return 0;
            }
            """);

        assertThat(result).isEqualTo(new TestProcesses.Result(0, "1:31:1\n", ""));
    }

    @Test
    void conversionWaitsForRuntimeLockBeforeReadingAllocationMetadata() throws Exception {
        final TestProcesses.Result result = runProbe("""
            #include "javan_runtime.h"
            #include <stdatomic.h>
            #include <stdio.h>
            #if defined(_WIN32)
            #include <process.h>
            #include <windows.h>
            #else
            #include <pthread.h>
            #include <unistd.h>
            #endif

            static atomic_int worker_ready;
            static atomic_int worker_proceed;
            static atomic_int worker_complete;
            static atomic_int worker_length;

            static void wait_tick(void) {
                #if defined(_WIN32)
                Sleep(1);
                #else
                usleep(1000);
                #endif
            }

            #if defined(_WIN32)
            static unsigned __stdcall borrow_array(void* argument) {
            #else
            static void* borrow_array(void* argument) {
            #endif
                atomic_store_explicit(&worker_ready, 1, memory_order_release);
                while (atomic_load_explicit(&worker_proceed, memory_order_acquire) == 0) {
                    wait_tick();
                }
                JavanNativeImportedByteArray view = javan_native_import_byte_array(argument);
                atomic_store_explicit(&worker_length, view.length, memory_order_release);
                atomic_store_explicit(&worker_complete, 1, memory_order_release);
                #if defined(_WIN32)
                return 0U;
                #else
                return NULL;
                #endif
            }

            int main(void) {
                javan_register_static_roots(0, 0);
                void* array = javan_byte_array_new(3);
                void** roots[] = {(void**) &array};
                javan_root_frame_push(roots, 1);
                javan_runtime_lock_enter();
                #if defined(_WIN32)
                HANDLE worker = (HANDLE) _beginthreadex(NULL, 0, borrow_array, array, 0, NULL);
                if (worker == NULL) {
                    javan_runtime_lock_leave();
                    return 2;
                }
                #else
                pthread_t worker;
                if (pthread_create(&worker, NULL, borrow_array, array) != 0) {
                    javan_runtime_lock_leave();
                    return 2;
                }
                #endif
                while (atomic_load_explicit(&worker_ready, memory_order_acquire) == 0) {
                    wait_tick();
                }
                atomic_store_explicit(&worker_proceed, 1, memory_order_release);
                for (int index = 0; index < 100
                    && atomic_load_explicit(&worker_complete, memory_order_acquire) == 0; index++) {
                    wait_tick();
                }
                int completed_while_locked = atomic_load_explicit(&worker_complete, memory_order_acquire);
                javan_runtime_lock_leave();
                #if defined(_WIN32)
                if (WaitForSingleObject(worker, INFINITE) != WAIT_OBJECT_0) {
                    CloseHandle(worker);
                    return 3;
                }
                CloseHandle(worker);
                #else
                if (pthread_join(worker, NULL) != 0) {
                    return 3;
                }
                #endif
                printf(
                    "%d:%d\\n",
                    completed_while_locked,
                    atomic_load_explicit(&worker_length, memory_order_acquire)
                );
                javan_root_frame_pop(roots);
                return 0;
            }
            """);

        assertThat(result).isEqualTo(new TestProcesses.Result(0, "0:3\n", ""));
    }

    private TestProcesses.Result runProbe(final String source) throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path probe = tempDir.resolve("probe.c");
        Files.writeString(probe, source);
        final Path binary = new NativeLinker().link(tempDir, probe, runtime, tempDir.resolve("probe"));
        return TestProcesses.run(
            tempDir,
            List.of(binary.toString()),
            Duration.ofSeconds(30),
            Map.of("JAVAN_HEAP_LIMIT_BYTES", "4096", "JAVAN_GC_STRESS", "1")
        );
    }
}

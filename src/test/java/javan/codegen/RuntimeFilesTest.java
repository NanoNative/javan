package javan.codegen;

import javan.TestProcesses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
@ResourceLock("native-cli-heavy")
final class RuntimeFilesTest {
    @TempDir
    private Path tempDir;

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
            "128"
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
            "128"
        );

        assertThat(stdout).isEqualTo("null");
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
            "javan_find_allocation(value, &previous)"
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
            "JavanTypeDescriptor* javan_type_descriptors_value = NULL;",
            "void javan_register_type_descriptors(JavanTypeDescriptor* descriptors, int count)",
            "void javan_root_frame_push(void*** roots, int count)",
            "void javan_root_frame_pop(void*** roots)",
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
            "static void javan_runtime_lock_enter(void) {",
            "pthread_once(&javan_runtime_lock_once, javan_runtime_lock_initialize)",
            "pthread_mutex_lock(&javan_runtime_lock_value)",
            "static void javan_runtime_lock_leave(void) {",
            "pthread_mutex_unlock(&javan_runtime_lock_value)",
            "static void javan_runtime_lock_reset_for_panic(void) {",
            "while (javan_runtime_lock_depth_value > 0) {",
            "javan_runtime_lock_enter();",
            "javan_runtime_lock_leave();",
            "javan_runtime_lock_reset_for_panic();"
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
            "thread->target = NULL;",
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
            live-live=16
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
            live-live=16
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
            live-live=8
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
            "javan_root_frame_push(javan_map_owner_roots, 3);",
            "javan_root_frame_push(javan_map_growth_roots, 3);",
            "map->keys = next_keys;",
            "map->values = next_values;",
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
            "javan_root_frame_push(javan_array_copy_roots, 1);",
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

    private String runRuntimeBoundaryProbe(final String source, final String heapLimitBytes) throws Exception {
        return runRuntimeBoundaryProbe(source, heapLimitBytes, Map.of());
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
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("probe.c");
        Files.writeString(main, source);
        final Path binary = new NativeLinker().link(tempDir, main, runtime, tempDir.resolve("probe"));
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

    private static boolean isWindowsHost() {
        final String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(java.util.Locale.ROOT).contains("win");
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

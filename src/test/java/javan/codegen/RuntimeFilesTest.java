package javan.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
            "node->collectible = ((kind == JAVAN_HEAP_KIND_OBJECT && type_id > 0)",
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
            "javan_root_frame_cleanup();",
            "exit(1);"
        );
    }

    @Test
    void writeEmitsSafePointMarkSweepForGeneratedObjectsAndObjectArrays() throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);

        assertThat(Files.readString(runtime)).contains(
            "void javan_gc_safe_point(void)",
            "void javan_gc_collect(void)",
            "JavanTypeDescriptor* descriptor = javan_type_descriptor_for(type_id);",
            "javan_gc_mark_object_array((javan_object_array*) value);",
            "javan_gc_mark_static_roots();",
            "javan_gc_mark_frame_roots();",
            "javan_gc_mark_runtime_object_references();",
            "javan_gc_sweep_unmarked();",
            "javan_gc_collected_allocations_value++;",
            "const char* value = getenv(\"JAVAN_GC_SAFEPOINT_INTERVAL\");"
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
            "javan_update_runtime_allocation_kind((void*) list->values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "javan_update_runtime_allocation_kind((void*) next_keys, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "javan_update_runtime_allocation_kind((void*) next_values, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
            "javan_update_runtime_allocation_kind((void*) next, JAVAN_RUNTIME_KIND_OWNED_BUFFER);",
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
            "void** javan_map_copy_roots[] = {",
            "javan_root_frame_push(javan_map_copy_roots, 1);",
            "javan_root_frame_pop(javan_map_copy_roots);",
            "void** javan_map_values_roots[] = {",
            "javan_root_frame_push(javan_map_values_roots, 1);",
            "javan_root_frame_pop(javan_map_values_roots);"
        );
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
                char* input = (char*) javan_string_concat("/tmp//\\001//app", 1, values);
                char* normalized = (char*) javan_path_normalize(input);
                printf("%s\\n", normalized);
                return 0;
            }
            """,
            "112"
        );

        assertThat(stdout).isEqualTo("/tmp/service/app\n");
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

    private String runRuntimeBoundaryProbe(final String source, final String heapLimitBytes) throws Exception {
        final Path runtime = new RuntimeFiles().write(tempDir);
        final Path main = tempDir.resolve("probe.c");
        Files.writeString(main, source);
        final Path binary = new NativeLinker().link(tempDir, main, runtime, tempDir.resolve("probe"));
        final ProcessBuilder processBuilder = new ProcessBuilder(binary.toString());
        processBuilder.directory(tempDir.toFile());
        processBuilder.environment().put("JAVAN_HEAP_LIMIT_BYTES", heapLimitBytes);
        processBuilder.environment().put("JAVAN_GC_STRESS", "1");

        final Process process = processBuilder.start();
        final String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        final int exitCode = process.waitFor();

        assertThat(exitCode)
            .describedAs(stderr)
            .isEqualTo(0);
        assertThat(stderr).isEmpty();
        return stdout;
    }
}

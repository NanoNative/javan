package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.codegen.MethodDescriptor;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes deterministic reachable file-system API evidence without accessing the file system.
 */
public final class FileReports {
    private static final String FILES = "java/nio/file/Files";
    private static final String PATH = "java/nio/file/Path";
    private static final MethodRef PATH_OF = new MethodRef(
        PATH, "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"
    );
    private static final MethodRef PATHS_GET = new MethodRef(
        "java/nio/file/Paths", "get", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"
    );

    /**
     * Analyzes reachable bytecode and writes JSON and Markdown file-system reports.
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
        final Path json = outputDirectory.resolve("reports/files.json");
        final Path markdown = outputDirectory.resolve("reports/files.md");
        Files2.writeString(json, json(report));
        Files2.writeString(markdown, markdown(report));
        return List.of(json, markdown);
    }

    /**
     * Counts reachable file-system APIs and literal paths proven by a conservative local flow.
     *
     * @param classes parsed application and dependency classes
     * @param reachable reachable application methods
     * @return immutable file-system evidence
     */
    Report analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final Counts counts = new Counts();
        for (final EntryPoint entry : reachable) {
            final Optional<MethodInfo> method = method(classes, entry);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            inspect(method.orElseThrow().code().orElseThrow().instructions(), counts);
        }
        return counts.report();
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile classFile = classes.get(entry.className());
        return classFile == null ? Optional.empty() : classFile.method(entry.methodName(), entry.descriptor());
    }

    private static void inspect(final List<Instruction> instructions, final Counts counts) {
        final Flow flow = new Flow();
        for (final Instruction instruction : instructions) {
            if (instruction.stringValue().isPresent()) {
                flow.push(Value.string(instruction.stringValue().orElseThrow()));
                continue;
            }
            if (zero(instruction)) {
                flow.push(Value.ZERO);
                continue;
            }
            final Optional<Integer> load = loadIndex(instruction);
            if (load.isPresent()) {
                flow.push(flow.local(load.orElseThrow()));
                continue;
            }
            final Optional<Integer> store = storeIndex(instruction);
            if (store.isPresent()) {
                flow.store(store.orElseThrow());
                continue;
            }
            if (instruction.methodRef().isPresent()) {
                final Invocation invocation = flow.invoke(instruction, instruction.methodRef().orElseThrow());
                final Optional<FileAccess> access = fileAccess(invocation.reference());
                if (access.isPresent()) {
                    counts.accept(invocation, access.orElseThrow());
                }
                continue;
            }
            switch (instruction.opcode()) {
                case 0, 192 -> {
                    // NOP and checkcast preserve the tracked operand stack.
                }
                case 1, 187 -> flow.push(Value.UNKNOWN);
                case 87 -> flow.pop();
                case 89 -> flow.duplicate();
                case 189 -> flow.newReferenceArray(instruction);
                case 132 -> flow.removeLocal(incrementedLocal(instruction));
                default -> flow.clear();
            }
        }
    }

    private static boolean zero(final Instruction instruction) {
        return instruction.opcode() == 3 || (instruction.intValue().isPresent() && instruction.intValue().orElseThrow() == 0);
    }

    private static Optional<Integer> loadIndex(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 25 -> operandIndex(instruction);
            case 42, 43, 44, 45 -> Optional.of(instruction.opcode() - 42);
            default -> Optional.empty();
        };
    }

    private static Optional<Integer> storeIndex(final Instruction instruction) {
        if (instruction.opcode() >= 54 && instruction.opcode() <= 58) {
            return operandIndex(instruction);
        }
        if (instruction.opcode() >= 59 && instruction.opcode() <= 78) {
            return Optional.of((instruction.opcode() - 59) % 4);
        }
        return Optional.empty();
    }

    private static Optional<Integer> operandIndex(final Instruction instruction) {
        return instruction.operands().length == 0
            ? Optional.empty()
            : Optional.of(instruction.operands()[0] & 0xff);
    }

    private static int incrementedLocal(final Instruction instruction) {
        return instruction.operands().length == 0 ? -1 : instruction.operands()[0] & 0xff;
    }

    private static Optional<FileAccess> fileAccess(final MethodRef reference) {
        if (FILES.equals(reference.owner())) {
            return filesAccess(reference);
        }
        if ("java/io/FileInputStream".equals(reference.owner()) || "java/io/FileReader".equals(reference.owner())) {
            return stringConstructorAccess(reference, "read");
        }
        if ("java/io/FileOutputStream".equals(reference.owner()) || "java/io/FileWriter".equals(reference.owner())) {
            return stringConstructorAccess(reference, "write");
        }
        if ("java/io/RandomAccessFile".equals(reference.owner())) {
            return stringConstructorAccess(reference, "unknown");
        }
        if (("java/nio/channels/FileChannel".equals(reference.owner())
            || "java/nio/channels/AsynchronousFileChannel".equals(reference.owner()))
            && "open".equals(reference.name())) {
            return Optional.of(new FileAccess("unknown", List.of(PathSlot.path(0))));
        }
        return Optional.empty();
    }

    private static Optional<FileAccess> stringConstructorAccess(final MethodRef reference, final String operation) {
        return "<init>".equals(reference.name()) && reference.descriptor().startsWith("(Ljava/lang/String;")
            ? Optional.of(new FileAccess(operation, List.of(PathSlot.string(0))))
            : Optional.empty();
    }

    private static Optional<FileAccess> filesAccess(final MethodRef reference) {
        return switch (reference.name()) {
            case "readString", "readAllBytes", "readAllLines", "newInputStream", "newBufferedReader", "lines" ->
                Optional.of(new FileAccess("read", List.of(PathSlot.path(0))));
            case "write", "writeString", "writeLines", "newOutputStream", "newBufferedWriter", "createFile",
                "createTempFile", "createTempDirectory", "createDirectories", "createDirectory", "setAttribute",
                "setLastModifiedTime", "setOwner", "setPosixFilePermissions" ->
                Optional.of(new FileAccess("write", List.of(PathSlot.path(0))));
            case "delete", "deleteIfExists" -> Optional.of(new FileAccess("delete", List.of(PathSlot.path(0))));
            case "copy", "move" -> Optional.of(new FileAccess("copy", List.of(PathSlot.path(0), PathSlot.path(1))));
            case "exists", "notExists", "isDirectory", "isRegularFile", "isSymbolicLink", "isExecutable",
                "isHidden", "size", "getLastModifiedTime", "getOwner", "getFileStore", "probeContentType",
                "readAttributes", "getAttribute", "getPosixFilePermissions" ->
                Optional.of(new FileAccess("metadata", List.of(PathSlot.path(0))));
            case "isSameFile" -> Optional.of(new FileAccess("metadata", List.of(PathSlot.path(0), PathSlot.path(1))));
            case "newDirectoryStream", "list", "walk", "find", "walkFileTree" ->
                Optional.of(new FileAccess("directory", List.of(PathSlot.path(0))));
            default -> Optional.of(new FileAccess("unknown", List.of()));
        };
    }

    private static String json(final Report report) {
        return new StringBuilder()
            .append("{\n")
            .append("  \"schemaVersion\": \"1\",\n")
            .append("  \"reachableFileCallSiteCount\": ").append(report.reachableFileCallSiteCount()).append(",\n")
            .append("  \"readCallSiteCount\": ").append(report.readCallSiteCount()).append(",\n")
            .append("  \"writeCallSiteCount\": ").append(report.writeCallSiteCount()).append(",\n")
            .append("  \"deleteCallSiteCount\": ").append(report.deleteCallSiteCount()).append(",\n")
            .append("  \"copyCallSiteCount\": ").append(report.copyCallSiteCount()).append(",\n")
            .append("  \"metadataCallSiteCount\": ").append(report.metadataCallSiteCount()).append(",\n")
            .append("  \"directoryCallSiteCount\": ").append(report.directoryCallSiteCount()).append(",\n")
            .append("  \"unknownOperationCallSiteCount\": ").append(report.unknownOperationCallSiteCount()).append(",\n")
            .append("  \"knownFilePathCount\": ").append(report.knownFilePathCount()).append(",\n")
            .append("  \"knownPathReferenceCount\": ").append(report.knownPathReferenceCount()).append(",\n")
            .append("  \"unknownPathCallSiteCount\": ").append(report.unknownPathCallSiteCount()).append(",\n")
            .append("  \"knownPaths\": [\n").append(pathsJson(report.knownPaths())).append("  ],\n")
            .append("  \"unknownPathCalls\": [\n").append(callsJson(report.unknownPathCalls())).append("  ],\n")
            .append("  \"fileCalls\": [\n").append(callsJson(report.fileCalls())).append("  ]\n")
            .append("}\n")
            .toString();
    }

    private static String pathsJson(final List<PathCount> paths) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < paths.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final PathCount path = paths.get(index);
            result.append("    {\"path\": ").append(Json.string(path.path()))
                .append(", \"operation\": ").append(Json.string(path.operation()))
                .append(", \"count\": ").append(path.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String callsJson(final List<CallCount> calls) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < calls.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final CallCount call = calls.get(index);
            result.append("    {\"target\": ").append(Json.string(call.target()))
                .append(", \"operation\": ").append(Json.string(call.operation()))
                .append(", \"count\": ").append(call.count()).append("}");
        }
        return result.append("\n").toString();
    }

    private static String markdown(final Report report) {
        final StringBuilder result = new StringBuilder();
        result.append("# Reachable File Access\n\n");
        result.append("The compiler scans reachable file-system APIs without accessing files. It records only paths proven ")
            .append("by a limited straight-line literal flow; dynamic or branched paths remain unknown. Embedded resources ")
            .append("are reported separately in `resources.md`.\n\n");
        result.append("- reachable file API call sites: `").append(report.reachableFileCallSiteCount()).append("`\n");
        result.append("- read call sites: `").append(report.readCallSiteCount()).append("`\n");
        result.append("- write call sites: `").append(report.writeCallSiteCount()).append("`\n");
        result.append("- delete call sites: `").append(report.deleteCallSiteCount()).append("`\n");
        result.append("- copy or move call sites: `").append(report.copyCallSiteCount()).append("`\n");
        result.append("- metadata call sites: `").append(report.metadataCallSiteCount()).append("`\n");
        result.append("- directory call sites: `").append(report.directoryCallSiteCount()).append("`\n");
        result.append("- unknown operation call sites: `").append(report.unknownOperationCallSiteCount()).append("`\n");
        result.append("- known file paths: `").append(report.knownFilePathCount()).append("`\n");
        result.append("- known path references: `").append(report.knownPathReferenceCount()).append("`\n");
        result.append("- unknown-path call sites: `").append(report.unknownPathCallSiteCount()).append("`\n\n");
        result.append("## Known Paths\n\n");
        result.append("| Path | Operation | Reachable call sites |\n");
        result.append("| --- | --- | ---: |\n");
        appendPaths(result, report.knownPaths());
        result.append("\n## Unknown Path Calls\n\n");
        result.append("| File target | Operation | Reachable call sites |\n");
        result.append("| --- | --- | ---: |\n");
        appendCalls(result, report.unknownPathCalls());
        return result.toString();
    }

    private static void appendPaths(final StringBuilder result, final List<PathCount> paths) {
        if (paths.isEmpty()) {
            result.append("| none | none | 0 |\n");
            return;
        }
        for (final PathCount path : paths) {
            result.append("| `").append(markdownCell(path.path())).append("` | ")
                .append(path.operation()).append(" | ").append(path.count()).append(" |\n");
        }
    }

    private static void appendCalls(final StringBuilder result, final List<CallCount> calls) {
        if (calls.isEmpty()) {
            result.append("| none | none | 0 |\n");
            return;
        }
        for (final CallCount call : calls) {
            result.append("| `").append(markdownCell(call.target())).append("` | ")
                .append(call.operation()).append(" | ").append(call.count()).append(" |\n");
        }
    }

    private static String markdownCell(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '\\' || current == '|' || current == '`') {
                result.append('\\');
            }
            result.append(current);
        }
        return result.toString();
    }

    private record FileAccess(String operation, List<PathSlot> pathSlots) {
    }

    private record PathSlot(int index, ValueKind requiredKind) {
        static PathSlot path(final int index) {
            return new PathSlot(index, ValueKind.PATH);
        }

        static PathSlot string(final int index) {
            return new PathSlot(index, ValueKind.STRING);
        }
    }

    private record Invocation(MethodRef reference, List<Value> arguments) {
    }

    private enum ValueKind {
        UNKNOWN,
        ZERO,
        EMPTY_STRING_ARRAY,
        STRING,
        PATH
    }

    private record Value(ValueKind kind, String text) {
        private static final Value UNKNOWN = new Value(ValueKind.UNKNOWN, "");
        private static final Value ZERO = new Value(ValueKind.ZERO, "");
        private static final Value EMPTY_STRING_ARRAY = new Value(ValueKind.EMPTY_STRING_ARRAY, "");

        static Value string(final String text) {
            return new Value(ValueKind.STRING, text);
        }

        static Value path(final String text) {
            return new Value(ValueKind.PATH, text);
        }
    }

    private static final class Flow {
        private List<Value> stack = new ArrayList<>();
        private Map<Integer, Value> locals = new HashMap<>();

        void push(final Value value) {
            stack.add(value);
        }

        Value pop() {
            return stack.isEmpty() ? Value.UNKNOWN : stack.removeLast();
        }

        void duplicate() {
            push(stack.isEmpty() ? Value.UNKNOWN : stack.getLast());
        }

        void newReferenceArray(final Instruction instruction) {
            final Value length = pop();
            final Optional<String> component = instruction.className();
            final boolean emptyStrings = length.kind() == ValueKind.ZERO
                && component.isPresent()
                && "java/lang/String".equals(component.orElseThrow());
            push(emptyStrings ? Value.EMPTY_STRING_ARRAY : Value.UNKNOWN);
        }

        Value local(final int index) {
            return locals.getOrDefault(index, Value.UNKNOWN);
        }

        void store(final int index) {
            locals.put(index, pop());
        }

        void removeLocal(final int index) {
            if (index >= 0) {
                locals.remove(index);
            }
        }

        Invocation invoke(final Instruction instruction, final MethodRef reference) {
            final int parameterCount = MethodDescriptor.parse(reference.descriptor()).parameterTypes().size();
            final List<Value> arguments = new ArrayList<>();
            for (int index = 0; index < parameterCount; index++) {
                arguments.addFirst(pop());
            }
            final Value receiver = instruction.opcode() == 184 ? Value.UNKNOWN : pop();
            final Optional<Value> returned = returnedValue(reference, arguments, receiver);
            if (returned.isPresent()) {
                push(returned.orElseThrow());
            }
            return new Invocation(reference, List.copyOf(arguments));
        }

        void clear() {
            stack = new ArrayList<>();
            locals = new HashMap<>();
        }
    }

    private static Optional<Value> returnedValue(
        final MethodRef reference,
        final List<Value> arguments,
        final Value receiver
    ) {
        if (PATH_OF.equals(reference) || PATHS_GET.equals(reference)) {
            return literalPath(arguments);
        }
        if (PATH.equals(reference.owner()) && "resolve".equals(reference.name()) && receiver.kind() == ValueKind.PATH) {
            return resolvedPath(reference, arguments, receiver);
        }
        if (FILES.equals(reference.owner()) && "copy".equals(reference.name()) && arguments.size() > 1) {
            return pathResult(arguments.get(1));
        }
        if (FILES.equals(reference.owner()) && ("createDirectories".equals(reference.name()) || "createDirectory".equals(reference.name())
            || "createFile".equals(reference.name()) || "write".equals(reference.name()) || "writeString".equals(reference.name()))
            && !arguments.isEmpty()) {
            return pathResult(arguments.getFirst());
        }
        return reference.descriptor().endsWith(")V") ? Optional.empty() : Optional.of(Value.UNKNOWN);
    }

    private static Optional<Value> literalPath(final List<Value> arguments) {
        if (arguments.size() != 2
            || arguments.getFirst().kind() != ValueKind.STRING
            || arguments.get(1).kind() != ValueKind.EMPTY_STRING_ARRAY) {
            return Optional.of(Value.UNKNOWN);
        }
        return Optional.of(Value.path(arguments.getFirst().text()));
    }

    private static Optional<Value> resolvedPath(
        final MethodRef reference,
        final List<Value> arguments,
        final Value receiver
    ) {
        if (arguments.size() != 1) {
            return Optional.of(Value.UNKNOWN);
        }
        final Value child = arguments.getFirst();
        if ("(Ljava/lang/String;)Ljava/nio/file/Path;".equals(reference.descriptor()) && child.kind() == ValueKind.STRING) {
            return Optional.of(Value.path(resolve(receiver.text(), child.text())));
        }
        if ("(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(reference.descriptor()) && child.kind() == ValueKind.PATH) {
            return Optional.of(Value.path(resolve(receiver.text(), child.text())));
        }
        return Optional.of(Value.UNKNOWN);
    }

    private static Optional<Value> pathResult(final Value value) {
        return value.kind() == ValueKind.PATH ? Optional.of(value) : Optional.of(Value.UNKNOWN);
    }

    private static String resolve(final String base, final String child) {
        if (absolute(child) || base.isEmpty()) {
            return child;
        }
        return base.endsWith("/") ? base + child : base + "/" + child;
    }

    private static boolean absolute(final String path) {
        return path.startsWith("/") || path.startsWith("\\")
            || (path.length() > 2 && path.charAt(1) == ':' && (path.charAt(2) == '/' || path.charAt(2) == '\\'));
    }

    private static final class Counts {
        private final List<PathCount> knownPaths = new ArrayList<>();
        private final List<CallCount> unknownPathCalls = new ArrayList<>();
        private final List<CallCount> fileCalls = new ArrayList<>();
        private int reachableFileCallSites;
        private int readCallSites;
        private int writeCallSites;
        private int deleteCallSites;
        private int copyCallSites;
        private int metadataCallSites;
        private int directoryCallSites;
        private int unknownOperationCallSites;
        private int knownPathReferences;
        private int unknownPathCallSites;

        void accept(final Invocation invocation, final FileAccess access) {
            reachableFileCallSites++;
            incrementCall(fileCalls, invocation.reference().display(), access.operation());
            incrementOperation(access.operation());
            boolean unknownPath = false;
            for (final PathSlot slot : access.pathSlots()) {
                final Value value = slot.index() < invocation.arguments().size()
                    ? invocation.arguments().get(slot.index())
                    : Value.UNKNOWN;
                if (value.kind() == slot.requiredKind()) {
                    knownPathReferences++;
                    incrementPath(knownPaths, value.text(), access.operation());
                } else {
                    unknownPath = true;
                }
            }
            if (unknownPath) {
                unknownPathCallSites++;
                incrementCall(unknownPathCalls, invocation.reference().display(), access.operation());
            }
        }

        private void incrementOperation(final String operation) {
            switch (operation) {
                case "read" -> readCallSites++;
                case "write" -> writeCallSites++;
                case "delete" -> deleteCallSites++;
                case "copy" -> copyCallSites++;
                case "metadata" -> metadataCallSites++;
                case "directory" -> directoryCallSites++;
                default -> unknownOperationCallSites++;
            }
        }

        Report report() {
            return new Report(
                reachableFileCallSites,
                readCallSites,
                writeCallSites,
                deleteCallSites,
                copyCallSites,
                metadataCallSites,
                directoryCallSites,
                unknownOperationCallSites,
                uniquePathCount(knownPaths),
                knownPathReferences,
                unknownPathCallSites,
                List.copyOf(knownPaths),
                List.copyOf(unknownPathCalls),
                List.copyOf(fileCalls)
            );
        }

        private static int uniquePathCount(final List<PathCount> paths) {
            int result = 0;
            String previous = "";
            for (int index = 0; index < paths.size(); index++) {
                final String path = paths.get(index).path();
                if (index == 0 || !path.equals(previous)) {
                    result++;
                    previous = path;
                }
            }
            return result;
        }
    }

    private static void incrementPath(final List<PathCount> paths, final String path, final String operation) {
        for (int index = 0; index < paths.size(); index++) {
            final PathCount existing = paths.get(index);
            final int comparison = Strings2.compareAscii(path, existing.path());
            if (comparison == 0 && operation.equals(existing.operation())) {
                paths.set(index, new PathCount(path, operation, existing.count() + 1));
                return;
            }
            if (comparison < 0 || (comparison == 0 && Strings2.compareAscii(operation, existing.operation()) < 0)) {
                paths.add(index, new PathCount(path, operation, 1));
                return;
            }
        }
        paths.add(new PathCount(path, operation, 1));
    }

    private static void incrementCall(final List<CallCount> calls, final String target, final String operation) {
        for (int index = 0; index < calls.size(); index++) {
            final CallCount existing = calls.get(index);
            final int comparison = Strings2.compareAscii(target, existing.target());
            if (comparison == 0 && operation.equals(existing.operation())) {
                calls.set(index, new CallCount(target, operation, existing.count() + 1));
                return;
            }
            if (comparison < 0 || (comparison == 0 && Strings2.compareAscii(operation, existing.operation()) < 0)) {
                calls.add(index, new CallCount(target, operation, 1));
                return;
            }
        }
        calls.add(new CallCount(target, operation, 1));
    }

    record Report(
        int reachableFileCallSiteCount,
        int readCallSiteCount,
        int writeCallSiteCount,
        int deleteCallSiteCount,
        int copyCallSiteCount,
        int metadataCallSiteCount,
        int directoryCallSiteCount,
        int unknownOperationCallSiteCount,
        int knownFilePathCount,
        int knownPathReferenceCount,
        int unknownPathCallSiteCount,
        List<PathCount> knownPaths,
        List<CallCount> unknownPathCalls,
        List<CallCount> fileCalls
    ) {
    }

    record PathCount(String path, String operation, int count) {
    }

    record CallCount(String target, String operation, int count) {
    }
}

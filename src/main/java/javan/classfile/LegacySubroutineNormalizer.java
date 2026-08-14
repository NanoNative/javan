package javan.classfile;

import javan.compat.BytecodeSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Inlines legacy {@code jsr}/{@code ret} subroutines into ordinary control flow. */
final class LegacySubroutineNormalizer {
    private LegacySubroutineNormalizer() {
    }

    static CodeAttribute normalize(final CodeAttribute code) throws IOException {
        if (!containsLegacySubroutine(code.instructions())) {
            return code;
        }
        return new Normalizer(code).normalize();
    }

    private static boolean containsLegacySubroutine(final List<Instruction> instructions) {
        for (final Instruction instruction : instructions) {
            if (isJsr(instruction) || isRet(instruction)) {
                return true;
            }
        }
        return false;
    }

    private static final class Normalizer {
        private final CodeAttribute source;
        private final List<Instruction> instructions;
        private final Map<Integer, Integer> indexes = new LinkedHashMap<>();
        private final Map<Integer, Set<Integer>> routineMembers = new HashMap<>();
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Location, Integer> nodeIndexes = new HashMap<>();
        private int nextContext;

        private Normalizer(final CodeAttribute source) throws IOException {
            this.source = source;
            instructions = source.instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Integer previous = indexes.put(
                    Integer.valueOf(instructions.get(index).offset()),
                    Integer.valueOf(index)
                );
                if (previous != null) {
                    throw invalid("duplicate instruction offset " + instructions.get(index).offset());
                }
            }
        }

        private CodeAttribute normalize() throws IOException {
            if (instructions.isEmpty()) {
                throw invalid("legacy subroutine method has no instructions");
            }
            emitRoutine(newContext(), 0, Optional.empty(), new LinkedHashSet<>());
            final int[] offsets = offsets();
            final List<Instruction> normalized = instructions(offsets);
            final byte[] bytecode = bytecode(normalized, offsets[nodes.size()]);
            final List<CodeException> handlers = exceptionHandlers(offsets);
            return new CodeAttribute(
                source.maxStack(),
                source.maxLocals(),
                bytecode,
                handlers.size(),
                handlers,
                lineNumbers(offsets),
                normalized
            );
        }

        private void emitRoutine(
            final Context context,
            final int startIndex,
            final Optional<Location> returnLocation,
            final Set<Integer> activeTargets
        ) throws IOException {
            if (!activeTargets.add(Integer.valueOf(startIndex))) {
                throw invalid("recursive legacy subroutine at " + instructions.get(startIndex).offset());
            }
            final Set<Integer> members = routineMembers(startIndex);
            boolean returned = returnLocation.isEmpty();
            for (int index = 0; index < instructions.size(); index++) {
                if (!members.contains(Integer.valueOf(index))) {
                    continue;
                }
                final Instruction instruction = instructions.get(index);
                final Location location = new Location(context.id(), index);
                nodeIndexes.put(location, Integer.valueOf(nodes.size()));
                if (isJsr(instruction)) {
                    final int continuation = nextIndex(index);
                    nodes.add(Node.plain(index, context, plain(instruction, 1, "aconst_null", new byte[0])));
                    final int target = targetIndex(instruction);
                    emitRoutine(
                        newContext(),
                        target,
                        Optional.of(new Location(context.id(), continuation)),
                        activeTargets
                    );
                    continue;
                }
                if (isRet(instruction)) {
                    if (returnLocation.isEmpty()) {
                        throw invalid("ret outside a legacy subroutine at " + instruction.offset());
                    }
                    nodes.add(Node.jump(index, context, returnLocation.orElseThrow()));
                    returned = true;
                    continue;
                }
                if (conditional(instruction.opcode())) {
                    nodes.add(Node.plain(index, context, plain(
                        instruction,
                        inverse(instruction.opcode()),
                        BytecodeSupport.mnemonic(inverse(instruction.opcode())),
                        new byte[]{0, 8}
                    )));
                    nodes.add(Node.jump(index, context, location(context, branchTarget(instruction))));
                    continue;
                }
                if (instruction.opcode() == 167 || instruction.opcode() == 200) {
                    nodes.add(Node.jump(index, context, location(context, branchTarget(instruction))));
                    continue;
                }
                if (instruction.opcode() == 170 || instruction.opcode() == 171) {
                    nodes.add(Node.switchNode(index, context, switchData(context, instruction)));
                    continue;
                }
                nodes.add(Node.plain(index, context, instruction));
            }
            activeTargets.remove(Integer.valueOf(startIndex));
            if (!returned) {
                throw invalid("legacy subroutine at " + instructions.get(startIndex).offset() + " has no ret");
            }
        }

        private Context newContext() {
            final int id = nextContext;
            nextContext = id + 1;
            return new Context(id);
        }

        private Set<Integer> routineMembers(final int startIndex) throws IOException {
            final Set<Integer> cached = routineMembers.get(Integer.valueOf(startIndex));
            if (cached != null) {
                return cached;
            }
            final Set<Integer> members = new LinkedHashSet<>();
            final List<Integer> pending = new ArrayList<>();
            pending.add(Integer.valueOf(startIndex));
            int cursor = 0;
            while (cursor < pending.size()) {
                final int index = pending.get(cursor++).intValue();
                if (!members.add(Integer.valueOf(index))) {
                    continue;
                }
                final Instruction instruction = instructions.get(index);
                for (final int successor : successors(index, instruction)) {
                    addUnique(pending, successor);
                }
                for (final CodeException handler : source.exceptionTable()) {
                    if (instruction.offset() >= handler.startPc() && instruction.offset() < handler.endPc()) {
                        addUnique(pending, indexAt(handler.handlerPc(), "exception handler"));
                    }
                }
            }
            final Set<Integer> result = Set.copyOf(members);
            routineMembers.put(Integer.valueOf(startIndex), result);
            return result;
        }

        private List<Integer> successors(final int index, final Instruction instruction) throws IOException {
            final int opcode = instruction.opcode();
            if (isRet(instruction) || terminal(opcode)) {
                return List.of();
            }
            if (isJsr(instruction)) {
                return List.of(Integer.valueOf(nextIndex(index)));
            }
            if (conditional(opcode)) {
                return List.of(Integer.valueOf(targetIndex(instruction)), Integer.valueOf(nextIndex(index)));
            }
            if (opcode == 167 || opcode == 200) {
                return List.of(Integer.valueOf(targetIndex(instruction)));
            }
            if (opcode == 170 || opcode == 171) {
                final List<Integer> result = new ArrayList<>();
                for (final int offset : switchTargetOffsets(instruction)) {
                    addUnique(result, indexAt(offset, "switch"));
                }
                return List.copyOf(result);
            }
            return List.of(Integer.valueOf(nextIndex(index)));
        }

        private int nextIndex(final int index) throws IOException {
            if (index + 1 >= instructions.size()) {
                throw invalid("instruction at " + instructions.get(index).offset() + " falls past method end");
            }
            return index + 1;
        }

        private int targetIndex(final Instruction instruction) throws IOException {
            return indexAt(branchTarget(instruction), "branch");
        }

        private int indexAt(final int offset, final String kind) throws IOException {
            final Integer index = indexes.get(Integer.valueOf(offset));
            if (index == null) {
                throw invalid(kind + " targets non-instruction offset " + offset);
            }
            return index.intValue();
        }

        private Location location(final Context context, final int offset) throws IOException {
            return new Location(context.id(), indexAt(offset, "branch"));
        }

        private SwitchData switchData(final Context context, final Instruction instruction) throws IOException {
            final byte[] operands = instruction.operands();
            final int padding = switchPadding(instruction.offset());
            final int defaultOffset = instruction.offset() + int32(operands, padding);
            final List<Location> targets = new ArrayList<>();
            final List<Integer> matches = new ArrayList<>();
            if (instruction.opcode() == 170) {
                final int low = int32(operands, padding + 4);
                final int high = int32(operands, padding + 8);
                int cursor = padding + 12;
                for (int value = low; value <= high; value++) {
                    targets.add(location(context, instruction.offset() + int32(operands, cursor)));
                    cursor += 4;
                }
                return new SwitchData(true, location(context, defaultOffset), low, matches, targets);
            }
            final int pairs = int32(operands, padding + 4);
            int cursor = padding + 8;
            for (int index = 0; index < pairs; index++) {
                matches.add(Integer.valueOf(int32(operands, cursor)));
                targets.add(location(context, instruction.offset() + int32(operands, cursor + 4)));
                cursor += 8;
            }
            return new SwitchData(false, location(context, defaultOffset), 0, matches, targets);
        }

        private int[] offsets() throws IOException {
            final int[] result = new int[nodes.size() + 1];
            int offset = 0;
            for (int index = 0; index < nodes.size(); index++) {
                result[index] = offset;
                offset += nodes.get(index).length(offset);
                if (offset > 65535) {
                    throw invalid("normalized method exceeds 65535 bytecode bytes");
                }
            }
            result[nodes.size()] = offset;
            return result;
        }

        private List<Instruction> instructions(final int[] offsets) throws IOException {
            final List<Instruction> result = new ArrayList<>();
            for (int index = 0; index < nodes.size(); index++) {
                final Node node = nodes.get(index);
                final int offset = offsets[index];
                if (node.kind() == NodeKind.PLAIN) {
                    result.add(copyAt(node.instruction(), offset));
                } else if (node.kind() == NodeKind.JUMP) {
                    final int target = targetOffset(node.target().orElseThrow(), offsets);
                    result.add(plain(node.instruction(), offset, 200, "goto_w", intBytes(target - offset)));
                } else {
                    result.add(switchInstruction(node, offset, offsets));
                }
            }
            return List.copyOf(result);
        }

        private Instruction switchInstruction(final Node node, final int offset, final int[] offsets) throws IOException {
            final SwitchData data = node.switchData().orElseThrow();
            final int padding = switchPadding(offset);
            final int size = data.table() ? padding + 12 + data.targets().size() * 4
                : padding + 8 + data.targets().size() * 8;
            final byte[] operands = new byte[size];
            putInt(operands, padding, targetOffset(data.defaultTarget(), offsets) - offset);
            if (data.table()) {
                putInt(operands, padding + 4, data.low());
                putInt(operands, padding + 8, data.low() + data.targets().size() - 1);
                int cursor = padding + 12;
                for (final Location target : data.targets()) {
                    putInt(operands, cursor, targetOffset(target, offsets) - offset);
                    cursor += 4;
                }
            } else {
                putInt(operands, padding + 4, data.targets().size());
                int cursor = padding + 8;
                for (int index = 0; index < data.targets().size(); index++) {
                    putInt(operands, cursor, data.matches().get(index).intValue());
                    putInt(operands, cursor + 4, targetOffset(data.targets().get(index), offsets) - offset);
                    cursor += 8;
                }
            }
            return plain(node.instruction(), offset, node.instruction().opcode(), node.instruction().mnemonic(), operands);
        }

        private int targetOffset(final Location target, final int[] offsets) throws IOException {
            final Integer nodeIndex = nodeIndexes.get(target);
            if (nodeIndex == null) {
                throw invalid("normalized branch target is not reachable");
            }
            return offsets[nodeIndex.intValue()];
        }

        private List<CodeException> exceptionHandlers(final int[] offsets) throws IOException {
            final List<CodeException> result = new ArrayList<>();
            for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                final Node node = nodes.get(nodeIndex);
                final int sourceOffset = instructions.get(node.originalIndex()).offset();
                for (final CodeException handler : source.exceptionTable()) {
                    if (sourceOffset < handler.startPc() || sourceOffset >= handler.endPc()) {
                        continue;
                    }
                    final CodeException normalized = new CodeException(
                        offsets[nodeIndex],
                        offsets[nodeIndex + 1],
                        targetOffset(new Location(node.context().id(), indexAt(handler.handlerPc(), "exception handler")), offsets),
                        handler.catchType()
                    );
                    if (!result.contains(normalized)) {
                        result.add(normalized);
                    }
                }
            }
            return List.copyOf(result);
        }

        private List<LineNumberEntry> lineNumbers(final int[] offsets) {
            final List<LineNumberEntry> result = new ArrayList<>();
            int previousLine = Integer.MIN_VALUE;
            for (int index = 0; index < nodes.size(); index++) {
                final int sourceOffset = instructions.get(nodes.get(index).originalIndex()).offset();
                final Optional<Integer> line = source.lineForOffset(sourceOffset);
                if (line.isPresent() && line.orElseThrow().intValue() != previousLine) {
                    previousLine = line.orElseThrow().intValue();
                    result.add(new LineNumberEntry(offsets[index], previousLine));
                }
            }
            return List.copyOf(result);
        }

        private byte[] bytecode(final List<Instruction> normalized, final int length) {
            final byte[] result = new byte[length];
            for (final Instruction instruction : normalized) {
                result[instruction.offset()] = (byte) instruction.opcode();
                System.arraycopy(
                    instruction.operands(), 0, result, instruction.offset() + 1, instruction.operands().length
                );
            }
            return result;
        }

        private IOException invalid(final String reason) {
            return new IOException("Invalid legacy jsr/ret bytecode: " + reason);
        }
    }

    private enum NodeKind {
        PLAIN,
        JUMP,
        SWITCH
    }

    private record Context(int id) {
    }

    private record Location(int contextId, int instructionIndex) {
    }

    private record SwitchData(
        boolean table,
        Location defaultTarget,
        int low,
        List<Integer> matches,
        List<Location> targets
    ) {
        private SwitchData {
            matches = List.copyOf(matches);
            targets = List.copyOf(targets);
        }
    }

    private record Node(
        int originalIndex,
        Context context,
        NodeKind kind,
        Instruction instruction,
        Optional<Location> target,
        Optional<SwitchData> switchData
    ) {
        private static Node plain(final int originalIndex, final Context context, final Instruction instruction) {
            return new Node(originalIndex, context, NodeKind.PLAIN, instruction, Optional.empty(), Optional.empty());
        }

        private static Node jump(final int originalIndex, final Context context, final Location target) {
            return new Node(
                originalIndex,
                context,
                NodeKind.JUMP,
                plainInstruction(200, "goto_w"),
                Optional.of(target),
                Optional.empty()
            );
        }

        private static Node switchNode(final int originalIndex, final Context context, final SwitchData data) {
            return new Node(originalIndex, context, NodeKind.SWITCH, data.table()
                ? plainInstruction(170, "tableswitch") : plainInstruction(171, "lookupswitch"),
                Optional.empty(), Optional.of(data));
        }

        private int length(final int offset) {
            if (kind == NodeKind.JUMP) {
                return 5;
            }
            if (kind == NodeKind.SWITCH) {
                final int padding = switchPadding(offset);
                return 1 + (switchData.orElseThrow().table()
                    ? padding + 12 + switchData.orElseThrow().targets().size() * 4
                    : padding + 8 + switchData.orElseThrow().targets().size() * 8);
            }
            return 1 + instruction.operands().length;
        }
    }

    private static Instruction plainInstruction(final int opcode, final String mnemonic) {
        return new Instruction(0, opcode, mnemonic, new byte[0], Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction plain(
        final Instruction source,
        final int opcode,
        final String mnemonic,
        final byte[] operands
    ) {
        return plain(source, source.offset(), opcode, mnemonic, operands);
    }

    private static Instruction plain(
        final Instruction source,
        final int offset,
        final int opcode,
        final String mnemonic,
        final byte[] operands
    ) {
        return new Instruction(offset, opcode, mnemonic, operands, source.methodRef(), source.fieldRef(), source.className(),
            source.stringValue(), source.intValue(), source.longValue(), source.floatValue(), source.doubleValue(),
            source.dynamicRef(), source.constantPoolTag());
    }

    private static Instruction copyAt(final Instruction source, final int offset) {
        return plain(source, offset, source.opcode(), source.mnemonic(), source.operands());
    }

    private static int branchTarget(final Instruction instruction) throws IOException {
        final byte[] operands = instruction.operands();
        if (instruction.opcode() == 200 || instruction.opcode() == 201) {
            if (operands.length != 4) {
                throw new IOException("Invalid legacy jsr/ret bytecode: truncated wide branch at " + instruction.offset());
            }
            return instruction.offset() + int32(operands, 0);
        }
        if (operands.length != 2) {
            throw new IOException("Invalid legacy jsr/ret bytecode: truncated branch at " + instruction.offset());
        }
        return instruction.offset() + (short) ((unsigned(operands[0]) << 8) | unsigned(operands[1]));
    }

    private static List<Integer> switchTargetOffsets(final Instruction instruction) throws IOException {
        final byte[] operands = instruction.operands();
        final int padding = switchPadding(instruction.offset());
        final List<Integer> result = new ArrayList<>();
        result.add(Integer.valueOf(instruction.offset() + int32(operands, padding)));
        if (instruction.opcode() == 170) {
            final int low = int32(operands, padding + 4);
            final int high = int32(operands, padding + 8);
            int cursor = padding + 12;
            for (int value = low; value <= high; value++) {
                result.add(Integer.valueOf(instruction.offset() + int32(operands, cursor)));
                cursor += 4;
            }
        } else {
            final int pairs = int32(operands, padding + 4);
            int cursor = padding + 8;
            for (int index = 0; index < pairs; index++) {
                result.add(Integer.valueOf(instruction.offset() + int32(operands, cursor + 4)));
                cursor += 8;
            }
        }
        return List.copyOf(result);
    }

    private static int int32(final byte[] bytes, final int offset) throws IOException {
        if (offset < 0 || offset + 3 >= bytes.length) {
            throw new IOException("Invalid legacy jsr/ret bytecode: truncated four-byte operand");
        }
        return (unsigned(bytes[offset]) << 24) | (unsigned(bytes[offset + 1]) << 16)
            | (unsigned(bytes[offset + 2]) << 8) | unsigned(bytes[offset + 3]);
    }

    private static byte[] intBytes(final int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private static void putInt(final byte[] bytes, final int offset, final int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static int inverse(final int opcode) {
        return switch (opcode) {
            case 153 -> 154;
            case 154 -> 153;
            case 155 -> 156;
            case 156 -> 155;
            case 157 -> 158;
            case 158 -> 157;
            case 159 -> 160;
            case 160 -> 159;
            case 161 -> 162;
            case 162 -> 161;
            case 163 -> 164;
            case 164 -> 163;
            case 165 -> 166;
            case 166 -> 165;
            case 198 -> 199;
            case 199 -> 198;
            default -> throw new IllegalArgumentException("Not a conditional branch: " + opcode);
        };
    }

    private static boolean conditional(final int opcode) {
        return opcode >= 153 && opcode <= 166 || opcode == 198 || opcode == 199;
    }

    private static boolean terminal(final int opcode) {
        return opcode >= 172 && opcode <= 177 || opcode == 191;
    }

    private static boolean isJsr(final Instruction instruction) {
        return instruction.opcode() == 168 || instruction.opcode() == 201;
    }

    private static boolean isRet(final Instruction instruction) {
        return instruction.opcode() == 169
            || instruction.opcode() == 196 && instruction.operands().length > 0
            && unsigned(instruction.operands()[0]) == 169;
    }

    private static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor - offset - 1;
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static void addUnique(final List<Integer> values, final int value) {
        if (!values.contains(Integer.valueOf(value))) {
            values.add(Integer.valueOf(value));
        }
    }
}

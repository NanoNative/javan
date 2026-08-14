package javan.analysis;

import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds and validates the canonical bytecode control-flow graph for one method. */
public final class BytecodeControlFlow {
    private BytecodeControlFlow() {
    }

    /** Control-flow edge kind. */
    public enum EdgeKind {
        FALLTHROUGH,
        BRANCH,
        SWITCH,
        EXCEPTION
    }

    /** One basic block, identified by inclusive start and end instruction offsets. */
    public record Block(int id, int startOffset, int endOffset, List<Integer> instructionOffsets) {
        public Block {
            instructionOffsets = List.copyOf(instructionOffsets);
        }
    }

    /** One directed basic-block edge. */
    public record Edge(int fromBlock, int toBlock, EdgeKind kind) {
    }

    /** Immutable graph with deterministic block and edge order. */
    public record Graph(List<Block> blocks, List<Edge> edges, Map<Integer, List<Integer>> instructionSuccessors) {
        public Graph {
            blocks = List.copyOf(blocks);
            edges = List.copyOf(edges);
            final Map<Integer, List<Integer>> successors = new LinkedHashMap<>();
            for (final Map.Entry<Integer, List<Integer>> entry : instructionSuccessors.entrySet()) {
                successors.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            instructionSuccessors = Collections.unmodifiableMap(successors);
        }

        /** Returns successor instruction indexes for a decoded instruction index. */
        public List<Integer> successors(final int instructionIndex) {
            return instructionSuccessors.getOrDefault(Integer.valueOf(instructionIndex), List.of());
        }
    }

    /** Graph construction result. Invalid bytecode still returns its partial graph and deterministic issues. */
    public record Result(Graph graph, List<String> issues, boolean structurallyValid) {
        public Result {
            issues = List.copyOf(issues);
        }

        /** Returns whether graph construction and stack validation succeeded. */
        public boolean valid() {
            return issues.isEmpty();
        }
    }

    /** Builds a canonical graph from decoded method bytecode. */
    public static Result analyze(final MethodInfo method) {
        if (method.code().isEmpty()) {
            return new Result(new Graph(List.of(), List.of(), Map.of()), List.of(), true);
        }
        return analyze(method.code().orElseThrow());
    }

    static Result analyze(final CodeAttribute code) {
        return new Builder(code).build();
    }

    private static final class Builder {
        private final CodeAttribute code;
        private final List<Instruction> instructions;
        private final Map<Integer, Integer> indexes = new LinkedHashMap<>();
        private final List<String> issues = new ArrayList<>();
        private final List<List<InstructionEdge>> instructionEdges = new ArrayList<>();

        private Builder(final CodeAttribute code) {
            this.code = code;
            this.instructions = code.instructions();
            for (int index = 0; index < instructions.size(); index++) {
                indexes.put(Integer.valueOf(instructions.get(index).offset()), Integer.valueOf(index));
                instructionEdges.add(new ArrayList<>());
            }
        }

        private Result build() {
            if (instructions.isEmpty()) {
                if (code.bytecode().length != 0) {
                    issues.add("method has bytecode but no decoded instructions");
                }
                return result(List.of(), List.of());
            }
            normalEdges();
            exceptionEdges();
            final boolean structurallyValid = issues.isEmpty();
            validateStackDepths();
            final List<Block> blocks = blocks();
            return result(blocks, blockEdges(blocks), structurallyValid);
        }

        private Result result(final List<Block> blocks, final List<Edge> edges) {
            return result(blocks, edges, issues.isEmpty());
        }

        private Result result(final List<Block> blocks, final List<Edge> edges, final boolean structurallyValid) {
            final Map<Integer, List<Integer>> successors = new LinkedHashMap<>();
            for (int index = 0; index < instructionEdges.size(); index++) {
                final List<Integer> targets = new ArrayList<>();
                for (final InstructionEdge edge : instructionEdges.get(index)) {
                    if (edge.kind() != EdgeKind.EXCEPTION
                        && !targets.contains(Integer.valueOf(edge.targetIndex()))) {
                        targets.add(Integer.valueOf(edge.targetIndex()));
                    }
                }
                successors.put(Integer.valueOf(index), List.copyOf(targets));
            }
            return new Result(new Graph(blocks, edges, successors), issues, structurallyValid);
        }

        private void normalEdges() {
            for (int index = 0; index < instructions.size(); index++) {
                final Instruction instruction = instructions.get(index);
                final int opcode = instruction.opcode();
                if (conditional(opcode)) {
                    addTarget(index, branchTarget(instruction), EdgeKind.BRANCH, "branch");
                    addFallthrough(index);
                } else if (opcode == 167 || opcode == 200) {
                    addTarget(index, branchTarget(instruction), EdgeKind.BRANCH, "branch");
                } else if (opcode == 170 || opcode == 171) {
                    for (final Integer target : switchTargets(instruction)) {
                        addTarget(index, target.intValue(), EdgeKind.SWITCH, "switch");
                    }
                } else if (!terminal(opcode) && opcode != 168 && opcode != 169 && opcode != 201) {
                    addFallthrough(index);
                }
            }
        }

        private void exceptionEdges() {
            for (final CodeException handler : code.exceptionTable()) {
                if (handler.startPc() >= handler.endPc()
                    || !indexes.containsKey(Integer.valueOf(handler.startPc()))
                    || handler.endPc() != code.bytecode().length && !indexes.containsKey(Integer.valueOf(handler.endPc()))) {
                    addIssue("exception range " + handler.startPc() + ".." + handler.endPc() + " is not instruction-aligned");
                    continue;
                }
                final Integer handlerIndex = indexes.get(Integer.valueOf(handler.handlerPc()));
                if (handlerIndex == null) {
                    addIssue("exception handler targets non-instruction offset " + handler.handlerPc());
                    continue;
                }
                for (int index = 0; index < instructions.size(); index++) {
                    final int offset = instructions.get(index).offset();
                    if (offset >= handler.startPc() && offset < handler.endPc()) {
                        addEdge(index, handlerIndex.intValue(), EdgeKind.EXCEPTION);
                    }
                }
            }
        }

        private void addFallthrough(final int index) {
            if (index + 1 >= instructions.size()) {
                addIssue("instruction at " + instructions.get(index).offset() + " falls past method end");
                return;
            }
            addEdge(index, index + 1, EdgeKind.FALLTHROUGH);
        }

        private void addTarget(
            final int sourceIndex,
            final int targetOffset,
            final EdgeKind kind,
            final String description
        ) {
            final Integer targetIndex = indexes.get(Integer.valueOf(targetOffset));
            if (targetIndex == null) {
                addIssue(description + " at " + instructions.get(sourceIndex).offset()
                    + " targets non-instruction offset " + targetOffset);
                return;
            }
            addEdge(sourceIndex, targetIndex.intValue(), kind);
        }

        private void addEdge(final int sourceIndex, final int targetIndex, final EdgeKind kind) {
            final InstructionEdge edge = new InstructionEdge(targetIndex, kind);
            if (!instructionEdges.get(sourceIndex).contains(edge)) {
                instructionEdges.get(sourceIndex).add(edge);
            }
        }

        private int branchTarget(final Instruction instruction) {
            final byte[] operands = instruction.operands();
            if (instruction.opcode() == 200 || instruction.opcode() == 201) {
                if (operands.length < 4) {
                    addIssue("truncated branch operands at " + instruction.offset());
                    return Integer.MIN_VALUE;
                }
                return instruction.offset() + int32(operands, 0, instruction.offset());
            }
            if (operands.length < 2) {
                addIssue("truncated branch operands at " + instruction.offset());
                return Integer.MIN_VALUE;
            }
            final int relative = (short) ((unsigned(operands[0]) << 8) | unsigned(operands[1]));
            return instruction.offset() + relative;
        }

        private List<Integer> switchTargets(final Instruction instruction) {
            final byte[] operands = instruction.operands();
            final int padding = switchPadding(instruction.offset());
            final Set<Integer> result = new LinkedHashSet<>();
            if (operands.length < padding + 8) {
                addIssue("truncated switch operands at " + instruction.offset());
                return List.of();
            }
            result.add(Integer.valueOf(instruction.offset() + int32(operands, padding, instruction.offset())));
            if (instruction.opcode() == 170) {
                if (operands.length < padding + 12) {
                    addIssue("truncated tableswitch operands at " + instruction.offset());
                    return List.copyOf(result);
                }
                final int low = int32(operands, padding + 4, instruction.offset());
                final int high = int32(operands, padding + 8, instruction.offset());
                final long count = (long) high - low + 1L;
                int cursor = padding + 12;
                if (count < 0L || count > (operands.length - cursor) / 4L) {
                    addIssue("invalid tableswitch range at " + instruction.offset());
                    return List.copyOf(result);
                }
                for (long index = 0; index < count; index++) {
                    result.add(Integer.valueOf(instruction.offset() + int32(operands, cursor, instruction.offset())));
                    cursor += 4;
                }
            } else {
                final int pairs = int32(operands, padding + 4, instruction.offset());
                int cursor = padding + 8;
                if (pairs < 0 || pairs > (operands.length - cursor) / 8) {
                    addIssue("invalid lookupswitch pairs at " + instruction.offset());
                    return List.copyOf(result);
                }
                for (int index = 0; index < pairs; index++) {
                    result.add(Integer.valueOf(instruction.offset() + int32(operands, cursor + 4, instruction.offset())));
                    cursor += 8;
                }
            }
            return List.copyOf(result);
        }

        private int int32(final byte[] bytes, final int offset, final int instructionOffset) {
            if (offset < 0 || offset + 3 >= bytes.length) {
                addIssue("truncated four-byte operand at " + instructionOffset);
                return 0;
            }
            return (unsigned(bytes[offset]) << 24)
                | (unsigned(bytes[offset + 1]) << 16)
                | (unsigned(bytes[offset + 2]) << 8)
                | unsigned(bytes[offset + 3]);
        }

        private void validateStackDepths() {
            final Integer[] depths = new Integer[instructions.size()];
            depths[0] = Integer.valueOf(0);
            final List<Integer> pending = new ArrayList<>();
            pending.add(Integer.valueOf(0));
            int cursor = 0;
            while (cursor < pending.size()) {
                final int index = pending.get(cursor).intValue();
                cursor++;
                final int depth = depths[index].intValue();
                final StackEffect effect = stackEffect(instructions.get(index));
                if (depth < effect.popWords()) {
                    addIssue("stack underflow at " + instructions.get(index).offset());
                    continue;
                }
                final int outgoing = depth - effect.popWords() + effect.pushWords();
                if (outgoing > code.maxStack()) {
                    addIssue("stack depth " + outgoing + " exceeds max_stack " + code.maxStack()
                        + " at " + instructions.get(index).offset());
                    continue;
                }
                for (final InstructionEdge edge : instructionEdges.get(index)) {
                    final int candidate = edge.kind() == EdgeKind.EXCEPTION ? 1 : outgoing;
                    final Integer existing = depths[edge.targetIndex()];
                    if (existing == null) {
                        depths[edge.targetIndex()] = Integer.valueOf(candidate);
                        pending.add(Integer.valueOf(edge.targetIndex()));
                    } else if (existing.intValue() != candidate) {
                        addIssue("stack merge at " + instructions.get(edge.targetIndex()).offset()
                            + " has depths " + existing + " and " + candidate);
                    }
                }
            }
        }

        private List<Block> blocks() {
            final Set<Integer> starts = new LinkedHashSet<>();
            starts.add(Integer.valueOf(0));
            for (final CodeException handler : code.exceptionTable()) {
                final Integer start = indexes.get(Integer.valueOf(handler.startPc()));
                if (start != null) {
                    starts.add(start);
                }
                final Integer end = indexes.get(Integer.valueOf(handler.endPc()));
                if (end != null) {
                    starts.add(end);
                }
            }
            for (int index = 0; index < instructionEdges.size(); index++) {
                for (final InstructionEdge edge : instructionEdges.get(index)) {
                    if (edge.kind() != EdgeKind.FALLTHROUGH) {
                        starts.add(Integer.valueOf(edge.targetIndex()));
                    }
                }
                if (endsBlock(instructions.get(index).opcode()) && index + 1 < instructions.size()) {
                    starts.add(Integer.valueOf(index + 1));
                }
            }
            final List<Block> result = new ArrayList<>();
            int start = 0;
            for (int index = 1; index <= instructions.size(); index++) {
                if (index == instructions.size() || starts.contains(Integer.valueOf(index))) {
                    final List<Integer> offsets = new ArrayList<>();
                    for (int instruction = start; instruction < index; instruction++) {
                        offsets.add(Integer.valueOf(instructions.get(instruction).offset()));
                    }
                    result.add(new Block(result.size(), instructions.get(start).offset(),
                        instructions.get(index - 1).offset(), offsets));
                    start = index;
                }
            }
            return List.copyOf(result);
        }

        private List<Edge> blockEdges(final List<Block> blocks) {
            final int[] blockByInstruction = new int[instructions.size()];
            for (final Block block : blocks) {
                for (final Integer offset : block.instructionOffsets()) {
                    blockByInstruction[indexes.get(offset).intValue()] = block.id();
                }
            }
            final List<Edge> result = new ArrayList<>();
            for (int source = 0; source < instructionEdges.size(); source++) {
                for (final InstructionEdge edge : instructionEdges.get(source)) {
                    final Edge blockEdge = new Edge(blockByInstruction[source], blockByInstruction[edge.targetIndex()], edge.kind());
                    if ((blockEdge.fromBlock() != blockEdge.toBlock() || edge.kind() != EdgeKind.FALLTHROUGH)
                        && !result.contains(blockEdge)) {
                        result.add(blockEdge);
                    }
                }
            }
            return List.copyOf(result);
        }

        private StackEffect stackEffect(final Instruction instruction) {
            final int opcode = wideOpcode(instruction);
            if (opcode == 0 || opcode == 132 || opcode == 167 || opcode == 169 || opcode == 177 || opcode == 200) {
                return effect(0, 0);
            }
            if (opcode >= 2 && opcode <= 8 || opcode >= 11 && opcode <= 13 || opcode == 16 || opcode == 17
                || opcode == 18 || opcode == 19 || opcode == 1) {
                return effect(0, 1);
            }
            if (opcode == 9 || opcode == 10 || opcode == 14 || opcode == 15 || opcode == 20) {
                return effect(0, 2);
            }
            if (opcode == 21 || opcode == 23 || opcode == 25 || opcode >= 26 && opcode <= 29
                || opcode >= 34 && opcode <= 37 || opcode >= 42 && opcode <= 45) {
                return effect(0, 1);
            }
            if (opcode == 22 || opcode == 24 || opcode >= 30 && opcode <= 33 || opcode >= 38 && opcode <= 41) {
                return effect(0, 2);
            }
            if (opcode >= 46 && opcode <= 53) {
                return effect(2, opcode == 47 || opcode == 49 ? 2 : 1);
            }
            if (opcode == 54 || opcode == 56 || opcode == 58 || opcode >= 59 && opcode <= 62
                || opcode >= 67 && opcode <= 70 || opcode >= 75 && opcode <= 78) {
                return effect(1, 0);
            }
            if (opcode == 55 || opcode == 57 || opcode >= 63 && opcode <= 66 || opcode >= 71 && opcode <= 74) {
                return effect(2, 0);
            }
            if (opcode >= 79 && opcode <= 86) {
                return effect(opcode == 80 || opcode == 82 ? 4 : 3, 0);
            }
            if (opcode == 87) return effect(1, 0);
            if (opcode == 88) return effect(2, 0);
            if (opcode == 89) return effect(1, 2);
            if (opcode == 90) return effect(2, 3);
            if (opcode == 91) return effect(3, 4);
            if (opcode == 92) return effect(2, 4);
            if (opcode == 93) return effect(3, 5);
            if (opcode == 94) return effect(4, 6);
            if (opcode == 95) return effect(2, 2);
            if (opcode >= 96 && opcode <= 115) {
                final int kind = (opcode - 96) % 4;
                final int width = kind == 1 || kind == 3 ? 2 : 1;
                return effect(width * 2, width);
            }
            if (opcode >= 120 && opcode <= 125) return opcode % 2 == 0 ? effect(2, 1) : effect(3, 2);
            if (opcode >= 126 && opcode <= 131) return opcode % 2 == 0 ? effect(2, 1) : effect(4, 2);
            if (opcode >= 116 && opcode <= 119) return effect(opcode == 117 || opcode == 119 ? 2 : 1, opcode == 117 || opcode == 119 ? 2 : 1);
            if (opcode >= 133 && opcode <= 147) return conversionEffect(opcode);
            if (opcode >= 148 && opcode <= 152) return effect(opcode == 148 || opcode == 151 || opcode == 152 ? 4 : 2, 1);
            if (opcode >= 153 && opcode <= 158 || opcode == 198 || opcode == 199) return effect(1, 0);
            if (opcode >= 159 && opcode <= 166) return effect(2, 0);
            if (opcode == 170 || opcode == 171) return effect(1, 0);
            if (opcode >= 172 && opcode <= 176) return effect(opcode == 173 || opcode == 175 ? 2 : 1, 0);
            if (opcode >= 178 && opcode <= 181) return fieldEffect(instruction);
            if (opcode >= 182 && opcode <= 186) return invokeEffect(instruction);
            if (opcode == 187) return effect(0, 1);
            if (opcode == 188 || opcode == 189) return effect(1, 1);
            if (opcode == 190 || opcode == 193) return effect(1, 1);
            if (opcode == 191 || opcode == 194 || opcode == 195) return effect(1, 0);
            if (opcode == 192) return effect(1, 1);
            if (opcode == 197) return effect(unsignedOperand(instruction, 2), 1);
            return effect(0, 0);
        }

        private StackEffect fieldEffect(final Instruction instruction) {
            if (instruction.fieldRef().isEmpty()) return effect(0, 0);
            final FieldRef field = instruction.fieldRef().orElseThrow();
            final int width = descriptorWidth(field.descriptor());
            if (instruction.opcode() == 178) return effect(0, width);
            if (instruction.opcode() == 179) return effect(width, 0);
            if (instruction.opcode() == 180) return effect(1, width);
            return effect(1 + width, 0);
        }

        private StackEffect invokeEffect(final Instruction instruction) {
            final String descriptor;
            if (instruction.methodRef().isPresent()) {
                descriptor = instruction.methodRef().orElseThrow().descriptor();
            } else if (instruction.dynamicRef().isPresent()) {
                descriptor = instruction.dynamicRef().orElseThrow().descriptor();
            } else {
                return effect(0, 0);
            }
            int pops = parameterWords(descriptor);
            if (instruction.opcode() != 184 && instruction.opcode() != 186) pops++;
            return effect(pops, returnWords(descriptor));
        }

        private void addIssue(final String issue) {
            if (!issues.contains(issue)) issues.add(issue);
        }
    }

    private record InstructionEdge(int targetIndex, EdgeKind kind) {
    }

    private record StackEffect(int popWords, int pushWords) {
    }

    private static StackEffect effect(final int pops, final int pushes) {
        return new StackEffect(pops, pushes);
    }

    private static StackEffect conversionEffect(final int opcode) {
        if (opcode == 133 || opcode == 135) return effect(1, 2);
        if (opcode == 134) return effect(1, 1);
        if (opcode == 136 || opcode == 137) return effect(2, 1);
        if (opcode == 138) return effect(2, 2);
        if (opcode == 139 || opcode == 142) return effect(1, 1);
        if (opcode == 140 || opcode == 141) return effect(1, 2);
        if (opcode == 143) return effect(2, 2);
        if (opcode == 144) return effect(2, 1);
        return effect(1, 1);
    }

    private static int descriptorWidth(final String descriptor) {
        return descriptor.startsWith("J") || descriptor.startsWith("D") ? 2 : 1;
    }

    private static int parameterWords(final String descriptor) {
        int words = 0;
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            if (descriptor.charAt(index) == '[') {
                while (descriptor.charAt(index) == '[') index++;
                index = descriptor.charAt(index) == 'L' ? descriptor.indexOf(';', index) + 1 : index + 1;
                words++;
                continue;
            }
            final char value = descriptor.charAt(index);
            if (value == 'L') {
                index = descriptor.indexOf(';', index) + 1;
                words++;
            } else {
                words += value == 'J' || value == 'D' ? 2 : 1;
                index++;
            }
        }
        return words;
    }

    private static int returnWords(final String descriptor) {
        final int index = descriptor.indexOf(')') + 1;
        if (index <= 0 || index >= descriptor.length() || descriptor.charAt(index) == 'V') return 0;
        return descriptor.charAt(index) == 'J' || descriptor.charAt(index) == 'D' ? 2 : 1;
    }

    private static int wideOpcode(final Instruction instruction) {
        if (instruction.opcode() != 196 || instruction.operands().length == 0) return instruction.opcode();
        return unsigned(instruction.operands()[0]);
    }

    private static int unsignedOperand(final Instruction instruction, final int index) {
        return index < instruction.operands().length ? unsigned(instruction.operands()[index]) : 0;
    }

    private static boolean conditional(final int opcode) {
        return opcode >= 153 && opcode <= 166 || opcode == 198 || opcode == 199;
    }

    private static boolean terminal(final int opcode) {
        return opcode >= 172 && opcode <= 177 || opcode == 191;
    }

    private static boolean endsBlock(final int opcode) {
        return conditional(opcode) || opcode == 167 || opcode == 168 || opcode == 169 || opcode == 170
            || opcode == 171 || terminal(opcode) || opcode == 200 || opcode == 201;
    }

    private static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) cursor++;
        return cursor - offset - 1;
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }
}

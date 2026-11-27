package javan.analysis;

import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.CodeException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds literal string values that can reach a local bytecode expression. */
public final class ReflectionStringFlow {
    private record Search(int index, int local, boolean stackValue) {
    }

    private record Predecessor(int index, boolean exceptional) {
    }

    private ReflectionStringFlow() {
    }

    /**
     * Finds direct literals and literals assigned to a loaded local along reachable control flow.
     * Unknown values do not prevent an explicit reflection declaration from supplying targets.
     *
     * @param method enclosing method with decoded code
     * @param sourceIndex index of the instruction producing the string argument
     * @return literal values found at that use
     */
    public static Set<String> literals(final MethodInfo method, final int sourceIndex) {
        if (method.code().isEmpty()) {
            return Set.of();
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        if (sourceIndex < 0 || sourceIndex >= instructions.size()) {
            return Set.of();
        }
        final Instruction source = instructions.get(sourceIndex);
        if (source.stringValue().isPresent()) {
            return Set.of(source.stringValue().orElseThrow());
        }
        final int initialLocal = referenceLoad(source);
        if (initialLocal < 0) {
            return Set.of();
        }
        final BytecodeControlFlow.Result flow = BytecodeControlFlow.analyze(method);
        if (!flow.structurallyValid()) {
            return Set.of();
        }
        final List<List<Predecessor>> predecessors = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            predecessors.add(new ArrayList<>());
        }
        for (int index = 0; index < instructions.size(); index++) {
            for (final Integer successor : flow.graph().successors(index)) {
                predecessors.get(successor.intValue()).add(new Predecessor(index, false));
            }
        }
        for (final CodeException handler : method.code().orElseThrow().exceptionTable()) {
            int handlerIndex = -1;
            for (int index = 0; index < instructions.size(); index++) {
                if (instructions.get(index).offset() == handler.handlerPc()) {
                    handlerIndex = index;
                    break;
                }
            }
            if (handlerIndex < 0) {
                continue;
            }
            for (int index = 0; index < instructions.size(); index++) {
                final int offset = instructions.get(index).offset();
                if (offset >= handler.startPc() && offset < handler.endPc()) {
                    predecessors.get(handlerIndex).add(new Predecessor(index, true));
                }
            }
        }
        final Set<String> values = new HashSet<>();
        final Set<Search> visited = new HashSet<>();
        final List<Search> work = new ArrayList<>();
        work.add(new Search(sourceIndex, initialLocal, false));
        for (int cursor = 0; cursor < work.size(); cursor++) {
            final Search search = work.get(cursor);
            if (!visited.add(search)) {
                continue;
            }
            for (final Predecessor predecessor : predecessors.get(search.index())) {
                final int index = predecessor.index();
                final Instruction instruction = instructions.get(index);
                final int opcode = instruction.opcode();
                if (search.stackValue()) {
                    if (predecessor.exceptional()) {
                        continue;
                    }
                    if (instruction.stringValue().isPresent()) {
                        values.add(instruction.stringValue().orElseThrow());
                    } else if (opcode == 0 || opcode == 167 || opcode == 192 || opcode == 200) {
                        work.add(new Search(index, -1, true));
                    } else {
                        final int local = referenceLoad(instruction);
                        if (local >= 0) {
                            work.add(new Search(index, local, false));
                        }
                    }
                    continue;
                }
                if (opcode == 196 || opcode == 168 || opcode == 169 || opcode == 201) {
                    continue;
                }
                if (predecessor.exceptional()) {
                    work.add(new Search(index, search.local(), false));
                    continue;
                }
                final int stored = storedLocal(instruction);
                final boolean wide = opcode == 55 || opcode == 57
                    || opcode >= 63 && opcode <= 66 || opcode >= 71 && opcode <= 74;
                if (stored == search.local() || wide && stored + 1 == search.local()) {
                    if (opcode == 58 || opcode >= 75 && opcode <= 78) {
                        work.add(new Search(index, -1, true));
                    }
                } else if (opcode != 132 || (instruction.operands()[0] & 0xff) != search.local()) {
                    work.add(new Search(index, search.local(), false));
                }
            }
        }
        return Set.copyOf(values);
    }

    private static int referenceLoad(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 25) {
            return instruction.operands()[0] & 0xff;
        }
        return opcode >= 42 && opcode <= 45 ? opcode - 42 : -1;
    }

    private static int storedLocal(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 54 && opcode <= 58) {
            return instruction.operands()[0] & 0xff;
        }
        return opcode >= 59 && opcode <= 78 ? (opcode - 59) % 4 : -1;
    }
}

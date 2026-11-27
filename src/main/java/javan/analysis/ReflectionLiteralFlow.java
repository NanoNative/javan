package javan.analysis;

import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.CodeException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds literal strings and classes that can reach a local bytecode expression. */
public final class ReflectionLiteralFlow {
    private record Search(int index, int local, boolean stackValue) {
    }

    private record Predecessor(int index, boolean exceptional) {
    }

    private ReflectionLiteralFlow() {
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
        return literals(method, sourceIndex, false);
    }

    /**
     * Finds class literals and one-argument Class.forName literals reaching an expression.
     *
     * @param method enclosing method with decoded code
     * @param sourceIndex index of the instruction producing the class argument
     * @return discovered JVM class names
     */
    public static Set<String> classNames(final MethodInfo method, final int sourceIndex) {
        return literals(method, sourceIndex, true);
    }

    private static Set<String> literals(final MethodInfo method, final int sourceIndex, final boolean classes) {
        if (method.code().isEmpty()) {
            return Set.of();
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        if (sourceIndex < 0 || sourceIndex >= instructions.size()) {
            return Set.of();
        }
        final Instruction source = instructions.get(sourceIndex);
        final Set<String> direct = literal(method, instructions, sourceIndex, classes);
        if (!direct.isEmpty()) {
            return direct;
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
                    final Set<String> literal = literal(method, instructions, index, classes);
                    if (!literal.isEmpty()) {
                        values.addAll(literal);
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

    private static Set<String> literal(final MethodInfo method, final List<Instruction> instructions,
                                       final int index, final boolean classes) {
        final Instruction instruction = instructions.get(index);
        if (!classes) {
            if (instruction.stringValue().isPresent()) return Set.of(instruction.stringValue().orElseThrow());
            return Set.of();
        }
        if ((instruction.opcode() == 18 || instruction.opcode() == 19) && instruction.className().isPresent()) {
            return Set.of(instruction.className().orElseThrow());
        }
        if (instruction.opcode() == 184 && instruction.methodRef().isPresent()) {
            final var target = instruction.methodRef().orElseThrow();
            if ("java/lang/Class".equals(target.owner()) && "forName".equals(target.name())
                && "(Ljava/lang/String;)Ljava/lang/Class;".equals(target.descriptor())) {
                final Set<String> names = new HashSet<>();
                for (final String name : literals(method, index - 1)) names.add(name.replace('.', '/'));
                return Set.copyOf(names);
            }
        }
        return Set.of();
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

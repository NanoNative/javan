package javan.codegen;

import javan.analysis.EntryPoint;
import javan.analysis.GeneratedObjectCloneSupport;
import javan.classfile.ClassFile;
import javan.classfile.FieldInfo;
import javan.classfile.MethodInfo;
import javan.ir.IrClass;
import javan.ir.IrExpression;
import javan.ir.IrField;
import javan.ir.IrParameter;
import javan.ir.IrType;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static javan.codegen.BytecodeToIR.*;

final class BytecodeToIRMetadataSupport {
    static List<IrClass> lowerClasses(final Map<String, ClassFile> classes) {
        final List<IrClass> result = new ArrayList<>();
        final List<ClassFile> sorted = sortedClasses(classes);
        for (final ClassFile classFile : sorted) {
            result.add(new IrClass(
                classFile.name(),
                classSymbol(classFile.name()),
                instanceFields(classes, classFile),
                fields(classFile, true),
                enumConstants(classFile),
                GeneratedObjectCloneSupport.status(classes, classFile)
                    == GeneratedObjectCloneSupport.Status.SUPPORTED
            ));
        }
        return List.copyOf(result);
    }
    static List<EntryPoint> sortedEntryPoints(final List<EntryPoint> entries) {
        final List<SymbolEntry> keyed = new ArrayList<>();
        for (final EntryPoint entry : entries) {
            keyed.add(new SymbolEntry(entry, symbol(entry)));
        }
        sortSymbolEntries(keyed, 0, keyed.size());
        final List<EntryPoint> result = new ArrayList<>();
        for (final SymbolEntry entry : keyed) {
            result.add(entry.entryPoint());
        }
        return List.copyOf(result);
    }

    private static void sortSymbolEntries(
        final List<SymbolEntry> entries,
        final int from,
        final int to
    ) {
        if (to - from <= 1) {
            return;
        }
        final int middle = from + ((to - from) / 2);
        sortSymbolEntries(entries, from, middle);
        sortSymbolEntries(entries, middle, to);
        mergeSymbolEntries(entries, from, middle, to);
    }

    private static void mergeSymbolEntries(
        final List<SymbolEntry> entries,
        final int from,
        final int middle,
        final int to
    ) {
        final List<SymbolEntry> left = new ArrayList<>();
        for (int index = from; index < middle; index++) {
            left.add(entries.get(index));
        }
        int leftIndex = 0;
        int rightIndex = middle;
        int target = from;
        while (leftIndex < left.size() && rightIndex < to) {
            final SymbolEntry leftValue = left.get(leftIndex);
            final SymbolEntry rightValue = entries.get(rightIndex);
            if (Strings2.compareAscii(leftValue.symbol(), rightValue.symbol()) <= 0) {
                entries.set(target, leftValue);
                leftIndex++;
            } else {
                entries.set(target, rightValue);
                rightIndex++;
            }
            target++;
        }
        while (leftIndex < left.size()) {
            entries.set(target, left.get(leftIndex));
            leftIndex++;
            target++;
        }
    }

    private record SymbolEntry(EntryPoint entryPoint, String symbol) {
    }

    private static List<IrField> instanceFields(
        final Map<String, ClassFile> classes,
        final ClassFile classFile
    ) {
        final List<ClassFile> hierarchy = new ArrayList<>();
        collectGeneratedHierarchy(classes, classFile, new HashSet<>(), hierarchy);
        List<IrField> result = List.of();
        for (final ClassFile owner : hierarchy) {
            result = appendDeclaredFields(result, fields(owner, false));
        }
        return result;
    }

    private static void collectGeneratedHierarchy(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final Set<String> visited,
        final List<ClassFile> hierarchy
    ) {
        if (!visited.add(classFile.name())) {
            return;
        }
        final String superName = classFile.superName();
        final ClassFile superClass = superName == null || superName.isEmpty()
            ? null
            : classes.get(superName);
        if (superClass != null) {
            collectGeneratedHierarchy(classes, superClass, visited, hierarchy);
        }
        hierarchy.add(classFile);
    }

    private static List<IrField> appendDeclaredFields(
        final List<IrField> inherited,
        final List<IrField> declared
    ) {
        final Set<String> declaredSymbols = new HashSet<>();
        final Set<String> unavailableSymbols = new HashSet<>();
        for (final IrField field : inherited) {
            unavailableSymbols.add(field.symbol());
        }
        for (final IrField field : declared) {
            declaredSymbols.add(field.symbol());
            unavailableSymbols.add(field.symbol());
        }
        final List<IrField> result = new ArrayList<>();
        for (final IrField field : inherited) {
            if (!declaredSymbols.contains(field.symbol())) {
                result.add(field);
                continue;
            }
            int suffix = 0;
            String symbol;
            do {
                symbol = field.symbol() + "_javan_super_" + suffix;
                suffix++;
            } while (unavailableSymbols.contains(symbol));
            unavailableSymbols.add(symbol);
            result.add(new IrField(field.type(), field.name(), symbol));
        }
        result.addAll(declared);
        return List.copyOf(result);
    }

    static List<ClassFile> sortedClasses(final Map<String, ClassFile> classes) {
        final List<ClassFile> result = new ArrayList<>();
        for (final ClassFile classFile : classes.values()) {
            int index = 0;
            while (index < result.size() && Strings2.compareAscii(result.get(index).name(), classFile.name()) <= 0) {
                index++;
            }
            result.add(index, classFile);
        }
        return List.copyOf(result);
    }
    static List<IrField> fields(final ClassFile classFile, final boolean statics) {
        final List<IrField> result = new ArrayList<>();
        for (final FieldInfo field : classFile.fields()) {
            if (field.isStatic() != statics) {
                continue;
            }
            final Optional<IrType> type = fieldType(field.descriptor());
            if (type.isPresent()) {
                result.add(new IrField(type.orElseThrow(), field.name(), fieldSymbol(field.name())));
            }
        }
        return List.copyOf(result);
    }
    static List<String> enumConstants(final ClassFile classFile) {
        final List<String> result = new ArrayList<>();
        for (final FieldInfo field : classFile.fields()) {
            if (field.isEnumConstant()) {
                result.add(field.name());
            }
        }
        return List.copyOf(result);
    }
    static List<IrParameter> parameters(final MethodInfo method, final MethodDescriptor descriptor) {
        final List<IrParameter> result = new ArrayList<>();
        if (!method.isStatic()) {
            result.add(new IrParameter(IrType.OBJECT, "self"));
        }
        for (int index = 0; index < descriptor.parameterTypes().size(); index++) {
            result.add(new IrParameter(descriptor.parameterTypes().get(index), "arg" + index));
        }
        return List.copyOf(result);
    }
    static void bindParameters(
        final MethodInfo method,
        final MethodDescriptor descriptor,
        final List<IrParameter> parameters,
        final Map<Integer, IrExpression> locals
    ) {
        int parameterIndex = 0;
        int slot = 0;
        if (!method.isStatic()) {
            locals.put(slot, parameterExpression(parameters.get(parameterIndex)));
            parameterIndex++;
            slot++;
        }
        for (int index = 0; index < descriptor.parameterTypes().size(); index++) {
            locals.put(slot, parameterExpression(parameters.get(parameterIndex)));
            parameterIndex++;
            slot += descriptor.parameterTypes().get(index).slotWidth();
        }
    }
    static IrExpression parameterExpression(final IrParameter parameter) {
        if (parameter.type() == IrType.INT) {
            return IrExpression.intLocal(parameter.name());
        }
        if (parameter.type() == IrType.LONG) {
            return IrExpression.longLocal(parameter.name());
        }
        if (parameter.type() == IrType.FLOAT) {
            return IrExpression.floatLocal(parameter.name());
        }
        if (parameter.type() == IrType.DOUBLE) {
            return IrExpression.doubleLocal(parameter.name());
        }
        if (parameter.type() == IrType.OBJECT) {
            return IrExpression.objectLocal(parameter.name());
        }
        if (parameter.type() == IrType.VOID) {
            throw new IllegalArgumentException("void parameter is invalid");
        }
        throw new IllegalStateException("Unsupported IR type");
    }
    static Optional<IrType> fieldType(final String descriptor) {
        if ("B".equals(descriptor) || "C".equals(descriptor) || "I".equals(descriptor) || "S".equals(descriptor) || "Z".equals(descriptor)) {
            return Optional.of(IrType.INT);
        }
        if ("J".equals(descriptor)) {
            return Optional.of(IrType.LONG);
        }
        if ("F".equals(descriptor)) {
            return Optional.of(IrType.FLOAT);
        }
        if ("D".equals(descriptor)) {
            return Optional.of(IrType.DOUBLE);
        }
        if (descriptor.startsWith("L") || descriptor.startsWith("[")) {
            return Optional.of(IrType.OBJECT);
        }
        return Optional.empty();
    }

}

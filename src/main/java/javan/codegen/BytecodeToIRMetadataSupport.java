package javan.codegen;

import javan.analysis.CallGraph;
import javan.analysis.ClassInitializationGraph;
import javan.analysis.EntryPoint;
import javan.analysis.GeneratedObjectCloneSupport;
import javan.classfile.ClassFile;
import javan.classfile.ClassFileScanner;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.FieldInfo;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.ir.IrClass;
import javan.ir.IrExpression;
import javan.ir.IrField;
import javan.ir.IrMethodMetadata;
import javan.ir.IrParameter;
import javan.ir.IrReflectedClass;
import javan.ir.IrType;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
                classFile.isEnum(),
                GeneratedObjectCloneSupport.status(classes, classFile)
                    == GeneratedObjectCloneSupport.Status.SUPPORTED
            ));
        }
        return List.copyOf(result);
    }

    private static List<IrMethodMetadata> declaredMethods(final ClassFile classFile) {
        final List<IrMethodMetadata> result = new ArrayList<>();
        for (final MethodInfo method : classFile.methods()) {
            if (!"<init>".equals(method.name()) && !"<clinit>".equals(method.name())) {
                result.add(new IrMethodMetadata(
                    method.name(),
                    BytecodeToIRDynamicSupport.parameterDescriptors(method.descriptor()).orElseThrow()
                ));
            }
        }
        return List.copyOf(result);
    }

    static ReflectionClasses reflectionClasses(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        boolean declaredMethodLookup = false;
        boolean dynamicClassLookup = false;
        final Set<String> classLiterals = new HashSet<>();
        for (final EntryPoint entryPoint : reachableMethods) {
            final ClassFile owner = classes.get(entryPoint.className());
            final Optional<MethodInfo> method = owner == null
                ? Optional.empty()
                : owner.method(entryPoint.methodName(), entryPoint.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.methodRef().isPresent()) {
                    final MethodRef reference = instruction.methodRef().orElseThrow();
                    declaredMethodLookup |= "java/lang/Class".equals(reference.owner())
                        && "getDeclaredMethod".equals(reference.name())
                        && "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;".equals(reference.descriptor());
                    dynamicClassLookup |= isClassForName(reference);
                }
                if ((instruction.opcode() == 18 || instruction.opcode() == 19 || instruction.opcode() == 20)
                    && instruction.className().isPresent()) {
                    final String jvmName = instruction.className().orElseThrow();
                    if (!jvmName.startsWith("[") && !classes.containsKey(jvmName)) {
                        classLiterals.add(jvmName);
                    }
                }
            }
        }
        if (!declaredMethodLookup) {
            return new ReflectionClasses(false, List.of());
        }
        if (dynamicClassLookup) {
            classLiterals.addAll(List.of(
                "java/lang/String",
                "java/lang/Object",
                "java/lang/Class",
                "java/lang/ClassLoader",
                "java/util/ArrayList",
                "java/util/HashMap"
            ));
        }
        final List<String> sorted = new ArrayList<>();
        for (final String classLiteral : classLiterals) {
            int index = 0;
            while (index < sorted.size() && Strings2.compareAscii(sorted.get(index), classLiteral) < 0) {
                index++;
            }
            sorted.add(index, classLiteral);
        }
        return new ReflectionClasses(true, List.copyOf(sorted));
    }

    static List<IrReflectedClass> loadExternalReflectedClasses(
        final ReflectionClasses reflection,
        final Path outputDirectory
    ) throws IOException, InterruptedException {
        final List<IrReflectedClass> result = new ArrayList<>();
        final ClassFileScanner scanner = new ClassFileScanner();
        for (final String jvmName : reflection.externalClassNames()) {
            result.add(new IrReflectedClass(
                jvmName,
                declaredMethods(scanner.readRuntimeClass(jvmName, outputDirectory))
            ));
        }
        return List.copyOf(result);
    }

    static List<IrReflectedClass> reflectedClasses(
        final Map<String, ClassFile> classes,
        final List<IrClass> retainedClasses,
        final ReflectionClasses reflection,
        final List<IrReflectedClass> externalClasses
    ) {
        if (!reflection.declaredMethodLookup()) {
            return List.of();
        }
        final List<IrReflectedClass> result = new ArrayList<>();
        for (final IrClass retained : retainedClasses) {
            final ClassFile classFile = classes.get(retained.jvmName());
            if (classFile != null) {
                result.add(new IrReflectedClass(classFile.name(), declaredMethods(classFile)));
            }
        }
        result.addAll(externalClasses);
        return List.copyOf(result);
    }

    record ReflectionClasses(boolean declaredMethodLookup, List<String> externalClassNames) {
        ReflectionClasses {
            externalClassNames = List.copyOf(externalClassNames);
        }
    }

    static List<IrClass> lowerReachableClasses(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph,
        final ClassInitializationGraph.Result classInitialization
    ) {
        if (!callGraph.instantiatedTypes().complete()) {
            return lowerClasses(classes);
        }
        final Set<String> retained = new HashSet<>();
        for (final EntryPoint entryPoint : callGraph.reachableMethods()) {
            if (isClassForName(entryPoint.className(), entryPoint.methodName(), entryPoint.descriptor())) {
                return lowerClasses(classes);
            }
            addClass(classes, retained, entryPoint.className());
            final ClassFile owner = classes.get(entryPoint.className());
            final Optional<MethodInfo> method = owner == null
                ? Optional.empty()
                : owner.method(entryPoint.methodName(), entryPoint.descriptor());
            if (method.isEmpty()) {
                continue;
            }
            final Optional<CodeAttribute> methodCode = method.orElseThrow().code();
            if (methodCode.isEmpty()) {
                continue;
            }
            final CodeAttribute code = methodCode.orElseThrow();
            for (final CodeException handler : code.exceptionTable()) {
                if (handler.catchType().isPresent()) {
                    addClass(classes, retained, handler.catchType().orElseThrow());
                }
            }
            for (final Instruction instruction : code.instructions()) {
                if (instruction.className().isPresent()) {
                    addClass(classes, retained, instruction.className().orElseThrow());
                }
                if (instruction.methodRef().isPresent()) {
                    final MethodRef reference = instruction.methodRef().orElseThrow();
                    if (isClassForName(reference)) {
                        return lowerClasses(classes);
                    }
                    addClass(classes, retained, reference.owner());
                }
                if (instruction.fieldRef().isPresent()) {
                    addClass(classes, retained, instruction.fieldRef().orElseThrow().owner());
                }
            }
        }
        for (final String type : callGraph.instantiatedTypes().types()) {
            addClass(classes, retained, type);
        }
        for (final Map.Entry<String, List<String>> entry : classInitialization.dependencies().entrySet()) {
            addClass(classes, retained, entry.getKey());
            for (final String dependency : entry.getValue()) {
                addClass(classes, retained, dependency);
            }
        }
        final Map<String, ClassFile> selected = new LinkedHashMap<>();
        for (final ClassFile classFile : sortedClasses(classes)) {
            if (retained.contains(classFile.name())) {
                selected.put(classFile.name(), classFile);
            }
        }
        return lowerClasses(selected);
    }

    static Map<String, Integer> retainedTypeIds(
        final Map<String, ClassFile> classes,
        final List<IrClass> retainedClasses
    ) {
        final Set<String> retained = new HashSet<>();
        for (final IrClass classInfo : retainedClasses) {
            retained.add(classInfo.jvmName());
        }
        final Map<String, Integer> result = new LinkedHashMap<>();
        final List<ClassFile> sorted = sortedClasses(classes);
        for (int index = 0; index < sorted.size(); index++) {
            final String name = sorted.get(index).name();
            if (retained.contains(name)) {
                result.put(name, index + 1);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean isClassForName(final MethodRef reference) {
        return isClassForName(reference.owner(), reference.name(), reference.descriptor());
    }

    private static boolean isClassForName(final String owner, final String name, final String descriptor) {
        return "java/lang/Class".equals(owner)
            && "forName".equals(name)
            && "(Ljava/lang/String;)Ljava/lang/Class;".equals(descriptor);
    }

    private static void addClass(
        final Map<String, ClassFile> classes,
        final Set<String> retained,
        final String reference
    ) {
        if (reference == null || reference.isEmpty()) {
            return;
        }
        String name = reference;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        final ClassFile classFile = classes.get(name);
        if (classFile == null || !retained.add(name)) {
            return;
        }
        addClass(classes, retained, classFile.superName());
        for (final String interfaceName : classFile.interfaces()) {
            addClass(classes, retained, interfaceName);
        }
    }
    static List<EntryPoint> sortedEntryPoints(final List<EntryPoint> entries) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final EntryPoint entry : entries) {
            int index = 0;
            final String value = symbol(entry);
            while (index < result.size() && Strings2.compareAscii(symbol(result.get(index)), value) <= 0) {
                index++;
            }
            result.add(index, entry);
        }
        return List.copyOf(result);
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

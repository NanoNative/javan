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

    private static List<IrMethodMetadata> declaredMethods(
        final ClassFile classFile,
        final Map<String, ClassFile> classes,
        final Set<String> classpathClasses
    ) {
        final List<IrMethodMetadata> result = new ArrayList<>();
        for (final MethodInfo method : classFile.methods()) {
            if (!"<init>".equals(method.name()) && !"<clinit>".equals(method.name())) {
                result.add(new IrMethodMetadata(
                    classFile.name(),
                    classFile.nestHost(),
                    method.name(),
                    BytecodeToIRDynamicSupport.parameterDescriptors(method.descriptor()).orElseThrow(),
                    returnDescriptor(method.descriptor()),
                    method.accessFlags(),
                    classFile.isPublic(),
                    classpathClasses.contains(classFile.name()) || (method.isPublic() && classFile.isPublic()),
                    protectedOverrideCallers(classFile, method, classes, classpathClasses)
                ));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> protectedOverrideCallers(
        final ClassFile declaringClass,
        final MethodInfo method,
        final Map<String, ClassFile> classes,
        final Set<String> classpathClasses
    ) {
        if (classpathClasses.contains(declaringClass.name())
            || !declaringClass.isPublic()
            || (method.accessFlags() & 0x0004) == 0
            || !method.isStatic()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (final ClassFile candidate : sortedClasses(classes)) {
            if (classpathClasses.contains(candidate.name())
                && BytecodeToIR.isAssignableTo(classes, candidate.name(), declaringClass.name())) {
                result.add(candidate.name());
            }
        }
        return List.copyOf(result);
    }

    private static List<IrMethodMetadata> publicMethods(
        final ClassFile receiver,
        final Map<String, ClassFile> classes,
        final Set<String> classpathClasses
    ) {
        final List<IrMethodMetadata> result = new ArrayList<>();
        for (final List<PublicMethodCandidate> candidates : publicMethodCandidates(
            receiver, classes, classpathClasses, true, new HashSet<>()
        ).values()) {
            PublicMethodCandidate selected = candidates.getFirst();
            for (int index = 1; index < candidates.size(); index++) {
                final PublicMethodCandidate candidate = candidates.get(index);
                if (!selected.method().returnDescriptor().equals(candidate.method().returnDescriptor())
                    && returnTypeAssignableFrom(
                        selected.method().returnDescriptor(), candidate.method().returnDescriptor(), classes
                    )) {
                    selected = candidate;
                }
            }
            result.add(selected.method());
        }
        return List.copyOf(result);
    }

    private static Map<MethodShape, List<PublicMethodCandidate>> publicMethodCandidates(
        final ClassFile classFile,
        final Map<String, ClassFile> classes,
        final Set<String> classpathClasses,
        final boolean includeStatic,
        final Set<String> visiting
    ) {
        if (!visiting.add(classFile.name())) {
            return Map.of();
        }
        final Map<MethodShape, List<PublicMethodCandidate>> result = new LinkedHashMap<>();
        final Set<MethodShape> declaredShapes = new HashSet<>();
        for (final MethodInfo method : classFile.methods()) {
            if (!method.isPublic()
                || !includeStatic && method.isStatic()
                || "<init>".equals(method.name())
                || "<clinit>".equals(method.name())) {
                continue;
            }
            final List<String> parameters = BytecodeToIRDynamicSupport.parameterDescriptors(
                method.descriptor()
            ).orElseThrow();
            final MethodShape shape = new MethodShape(method.name(), parameters);
            declaredShapes.add(shape);
            List<PublicMethodCandidate> declared = result.get(shape);
            if (declared == null) {
                declared = new ArrayList<>();
                result.put(shape, declared);
            }
            declared.add(new PublicMethodCandidate(
                new IrMethodMetadata(
                    classFile.name(), classFile.nestHost(), method.name(), parameters,
                    returnDescriptor(method.descriptor()), method.accessFlags(), classFile.isPublic(),
                    classpathClasses.contains(classFile.name()) || (method.isPublic() && classFile.isPublic()),
                    protectedOverrideCallers(classFile, method, classes, classpathClasses)
                ),
                classFile.isInterface()
            ));
        }
        final ClassFile superclass = classFile.isInterface() ? null : classes.get(classFile.superName());
        if (superclass != null) {
            mergeInheritedMethods(
                result,
                declaredShapes,
                publicMethodCandidates(
                    superclass, classes, classpathClasses, includeStatic, new HashSet<>(visiting)
                ),
                classes
            );
        }
        for (final String interfaceName : classFile.interfaces()) {
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null) {
                mergeInheritedMethods(
                    result,
                    declaredShapes,
                    publicMethodCandidates(
                        interfaceClass, classes, classpathClasses, false, new HashSet<>(visiting)
                    ),
                    classes
                );
            }
        }
        return result;
    }

    private static void mergeInheritedMethods(
        final Map<MethodShape, List<PublicMethodCandidate>> result,
        final Set<MethodShape> declaredShapes,
        final Map<MethodShape, List<PublicMethodCandidate>> inherited,
        final Map<String, ClassFile> classes
    ) {
        for (final Map.Entry<MethodShape, List<PublicMethodCandidate>> entry : inherited.entrySet()) {
            if (declaredShapes.contains(entry.getKey())) {
                continue;
            }
            List<PublicMethodCandidate> merged = result.get(entry.getKey());
            if (merged == null) {
                merged = new ArrayList<>();
                result.put(entry.getKey(), merged);
            }
            for (final PublicMethodCandidate candidate : entry.getValue()) {
                mergePublicMethod(merged, candidate, classes);
            }
        }
    }

    private static void mergePublicMethod(
        final List<PublicMethodCandidate> methods,
        final PublicMethodCandidate candidate,
        final Map<String, ClassFile> classes
    ) {
        int index = 0;
        while (index < methods.size()) {
            final PublicMethodCandidate existing = methods.get(index);
            if (!candidate.method().returnDescriptor().equals(existing.method().returnDescriptor())) {
                index++;
                continue;
            }
            if (candidate.declaringInterface() != existing.declaringInterface()) {
                if (candidate.declaringInterface()) {
                    return;
                }
                methods.remove(index);
                continue;
            }
            if (BytecodeToIR.isAssignableTo(
                classes, existing.method().declaringJvmName(), candidate.method().declaringJvmName()
            )) {
                return;
            }
            if (BytecodeToIR.isAssignableTo(
                classes, candidate.method().declaringJvmName(), existing.method().declaringJvmName()
            )) {
                methods.remove(index);
                continue;
            }
            index++;
        }
        methods.add(candidate);
    }

    private static String returnDescriptor(final String methodDescriptor) {
        return methodDescriptor.substring(methodDescriptor.indexOf(')') + 1);
    }

    private static boolean returnTypeAssignableFrom(
        final String expected,
        final String candidate,
        final Map<String, ClassFile> classes
    ) {
        if (expected.equals(candidate)) {
            return true;
        }
        if ((candidate.startsWith("[") || candidate.startsWith("L"))
            && "Ljava/lang/Object;".equals(expected)) {
            return true;
        }
        if (candidate.startsWith("[")
            && ("Ljava/lang/Cloneable;".equals(expected) || "Ljava/io/Serializable;".equals(expected))) {
            return true;
        }
        if (expected.startsWith("[") && candidate.startsWith("[")) {
            return returnTypeAssignableFrom(expected.substring(1), candidate.substring(1), classes);
        }
        if (!expected.startsWith("L") || !candidate.startsWith("L")) {
            return false;
        }
        return BytecodeToIR.isAssignableTo(
            classes,
            candidate.substring(1, candidate.length() - 1),
            expected.substring(1, expected.length() - 1)
        );
    }

    static ReflectionClasses reflectionClasses(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        boolean declaredMethodLookup = false;
        boolean publicMethodLookup = false;
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
                    publicMethodLookup |= "java/lang/Class".equals(reference.owner())
                        && "getMethod".equals(reference.name())
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
        if (!declaredMethodLookup && !publicMethodLookup) {
            return new ReflectionClasses(false, false, List.of());
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
        return new ReflectionClasses(declaredMethodLookup, publicMethodLookup, List.copyOf(sorted));
    }

    static Map<String, ClassFile> loadExternalReflectionClasses(
        final Map<String, ClassFile> projectClasses,
        final ReflectionClasses reflection,
        final Path outputDirectory
    ) throws IOException, InterruptedException {
        final Map<String, ClassFile> result = new LinkedHashMap<>();
        final ClassFileScanner scanner = new ClassFileScanner();
        for (final String jvmName : reflection.externalClassNames()) {
            loadExternalClass(
                jvmName, projectClasses, result, scanner, outputDirectory, reflection.publicMethodLookup()
            );
        }
        if (reflection.publicMethodLookup()) {
            loadExternalClass("java/lang/Object", projectClasses, result, scanner, outputDirectory, true);
            for (final ClassFile classFile : projectClasses.values()) {
                loadExternalClass(classFile.superName(), projectClasses, result, scanner, outputDirectory, true);
                for (final String interfaceName : classFile.interfaces()) {
                    loadExternalClass(interfaceName, projectClasses, result, scanner, outputDirectory, true);
                }
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static void loadExternalClass(
        final String jvmName,
        final Map<String, ClassFile> projectClasses,
        final Map<String, ClassFile> result,
        final ClassFileScanner scanner,
        final Path outputDirectory,
        final boolean hierarchy
    ) throws IOException, InterruptedException {
        if (jvmName == null || jvmName.isEmpty() || projectClasses.containsKey(jvmName) || result.containsKey(jvmName)) {
            return;
        }
        final ClassFile classFile = scanner.readRuntimeClass(jvmName, outputDirectory);
        result.put(jvmName, classFile);
        if (!hierarchy) {
            return;
        }
        loadExternalClass(classFile.superName(), projectClasses, result, scanner, outputDirectory, true);
        for (final String interfaceName : classFile.interfaces()) {
            loadExternalClass(interfaceName, projectClasses, result, scanner, outputDirectory, true);
        }
    }

    static List<IrReflectedClass> reflectedClasses(
        final Map<String, ClassFile> classes,
        final List<IrClass> retainedClasses,
        final ReflectionClasses reflection,
        final Map<String, ClassFile> externalClasses
    ) {
        if (!reflection.declaredMethodLookup() && !reflection.publicMethodLookup()) {
            return List.of();
        }
        final Map<String, ClassFile> allClasses = new LinkedHashMap<>(externalClasses);
        allClasses.putAll(classes);
        final Set<String> classpathClasses = classes.keySet();
        final List<IrReflectedClass> result = new ArrayList<>();
        for (final IrClass retained : retainedClasses) {
            final ClassFile classFile = classes.get(retained.jvmName());
            if (classFile != null) {
                result.add(reflectedClass(classFile, allClasses, classpathClasses, reflection));
            }
        }
        for (final String jvmName : reflection.externalClassNames()) {
            final ClassFile classFile = externalClasses.get(jvmName);
            if (classFile != null) {
                result.add(reflectedClass(classFile, allClasses, classpathClasses, reflection));
            }
        }
        if (reflection.publicMethodLookup()) {
            final ClassFile objectClass = allClasses.get("java/lang/Object");
            if (objectClass != null) {
                result.add(new IrReflectedClass(
                    "java/lang/Object",
                    List.of(),
                    publicMethods(objectClass, allClasses, classpathClasses),
                    true
                ));
            }
        }
        return List.copyOf(result);
    }

    private static IrReflectedClass reflectedClass(
        final ClassFile classFile,
        final Map<String, ClassFile> classes,
        final Set<String> classpathClasses,
        final ReflectionClasses reflection
    ) {
        return new IrReflectedClass(
            classFile.name(),
            reflection.declaredMethodLookup()
                ? declaredMethods(classFile, classes, classpathClasses)
                : List.of(),
            reflection.publicMethodLookup() ? publicMethods(classFile, classes, classpathClasses) : List.of()
        );
    }

    record ReflectionClasses(
        boolean declaredMethodLookup,
        boolean publicMethodLookup,
        List<String> externalClassNames
    ) {
        ReflectionClasses {
            externalClassNames = List.copyOf(externalClassNames);
        }
    }

    private record MethodShape(String name, List<String> parameters) {
        private MethodShape {
            parameters = List.copyOf(parameters);
        }
    }

    private record PublicMethodCandidate(
        IrMethodMetadata method,
        boolean declaringInterface
    ) {
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

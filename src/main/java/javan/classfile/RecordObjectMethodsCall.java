package javan.classfile;

import javan.util.Strings2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exact parsed metadata for a javac record {@code ObjectMethods.bootstrap} call site.
 *
 * @param components matched record components and their canonical value shapes
 */
public record RecordObjectMethodsCall(List<RecordObjectMethodsCall.Component> components) {
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final String BOOTSTRAP_DESCRIPTOR =
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;"
            + "Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;";
    private static final String LIST = "java/util/List";
    private static final String ARRAY_LIST = "java/util/ArrayList";
    private static final String MAP = "java/util/Map";

    public RecordObjectMethodsCall {
        components = List.copyOf(components);
    }

    /**
     * One matched record component and its parsed value shape.
     *
     * @param field backing instance field
     * @param shape canonical declared value shape
     */
    public record Component(FieldInfo field, Shape shape) {
        /**
         * Returns the most precise declared type text for diagnostics.
         *
         * @return generic signature when present, otherwise the field descriptor
         */
        public String diagnosticType() {
            return field.signature().orElse(field.descriptor());
        }
    }

    /**
     * Closed direct-reference record component plan.
     */
    public sealed interface DirectReferencePlan permits ExactReferencePlan, SealedReferenceUnionPlan {
        /**
         * Returns exact receiver targets in deterministic order.
         *
         * @return exact receiver targets
         */
        List<ReferenceTarget> targets();
    }

    /**
     * Declared component position that determines whether a sealed union is admissible.
     */
    public enum ReferenceContext {
        /** A direct record component, which may use a closed sealed-interface union. */
        DIRECT_COMPONENT,
        /** A List element, which is limited to an exact final concrete reference. */
        LIST_ELEMENT
    }

    /**
     * Exact final-owner plan for one direct record component type.
     *
     * @param target exact receiver target
     */
    public record ExactReferencePlan(ReferenceTarget target) implements DirectReferencePlan {
        @Override
        public List<ReferenceTarget> targets() {
            return List.of(target);
        }
    }

    /**
     * Closed sealed-interface union plan for one direct record component type.
     *
     * @param targets permitted exact receiver targets in ASCII owner order
     */
    public record SealedReferenceUnionPlan(List<ReferenceTarget> targets) implements DirectReferencePlan {
        public SealedReferenceUnionPlan {
            targets = List.copyOf(targets);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("sealed reference union requires at least one target");
            }
        }
    }

    /**
     * Exact receiver semantics required by a record object-method operation.
     *
     * @param owner exact receiver owner
     * @param executableOwner exact owner of the executable operation, empty for identity semantics
     */
    public record ReferenceTarget(String owner, String executableOwner) {
        public ReferenceTarget {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(executableOwner, "executableOwner");
        }

        /**
         * Returns true when this target invokes an executable implementation.
         *
         * @return true for executable value semantics
         */
        public boolean executable() {
            return !executableOwner.isEmpty();
        }

        /**
         * Returns true when this target uses identity semantics.
         *
         * @return true for identity semantics
         */
        public boolean identity() {
            return executableOwner.isEmpty();
        }
    }

    /**
     * Plans a non-list, non-array declared reference component as either one final owner or a closed sealed union.
     *
     * @param classes loaded closed-world classes
     * @param shape canonical declared component shape
     * @param hashCode true for hashCode, false for equals
     * @return exact final-owner or sealed-union plan when supported
     */
    public static Optional<DirectReferencePlan> directReferencePlan(
        final Map<String, ClassFile> classes,
        final Shape shape,
        final boolean hashCode
    ) {
        return referencePlan(classes, shape, hashCode, ReferenceContext.DIRECT_COMPONENT);
    }

    /**
     * Plans a declared reference according to its record component position.
     *
     * @param classes loaded closed-world classes
     * @param shape canonical declared component shape
     * @param hashCode true for hashCode, false for equals
     * @param context direct component or nested List element position
     * @return exact final-owner or direct sealed-union plan when supported
     */
    public static Optional<DirectReferencePlan> referencePlan(
        final Map<String, ClassFile> classes,
        final Shape shape,
        final boolean hashCode,
        final ReferenceContext context
    ) {
        Objects.requireNonNull(context, "context");
        if (!shape.valid()
            || shape.isArray()
            || shape.isList()
            || shape.isStringMap()
            || shape.referenceOwner().isEmpty()) {
            return Optional.empty();
        }
        final String owner = shape.referenceOwner().orElseThrow();
        final ClassFile declared = classes.get(owner);
        if (declared == null) {
            return Optional.empty();
        }
        if (!declared.isInterface()) {
            return exactFinalReferencePlan(classes, declared, hashCode);
        }
        if (context == ReferenceContext.LIST_ELEMENT) {
            return Optional.empty();
        }
        if (declared.permittedSubclasses().isEmpty()) {
            return Optional.empty();
        }
        final List<ReferenceTarget> targets = new ArrayList<>(declared.permittedSubclasses().size());
        final Set<String> permittedOwners = new HashSet<>();
        for (final String permittedOwner : declared.permittedSubclasses()) {
            if (!permittedOwners.add(permittedOwner)) {
                return Optional.empty();
            }
            final ClassFile permitted = classes.get(permittedOwner);
            if (permitted == null
                || permitted.isInterface()
                || permitted.isAbstract()
                || permitted.isEnum()
                || !permitted.isFinal()
                || !permitted.interfaces().contains(declared.name())) {
                return Optional.empty();
            }
            final Optional<ReferenceTarget> target = referenceTarget(classes, permitted, hashCode);
            if (target.isEmpty()) {
                return Optional.empty();
            }
            insertReferenceTarget(targets, target.orElseThrow());
        }
        return Optional.of(new SealedReferenceUnionPlan(targets));
    }

    private static void insertReferenceTarget(
        final List<ReferenceTarget> targets,
        final ReferenceTarget target
    ) {
        int index = 0;
        while (index < targets.size() && Strings2.compareAscii(targets.get(index).owner(), target.owner()) <= 0) {
            index++;
        }
        targets.add(index, target);
    }

    private static Optional<DirectReferencePlan> exactFinalReferencePlan(
        final Map<String, ClassFile> classes,
        final ClassFile owner,
        final boolean hashCode
    ) {
        if (owner.isInterface() || owner.isAbstract() || !owner.isFinal()) {
            return Optional.empty();
        }
        final Optional<ReferenceTarget> target = referenceTarget(classes, owner, hashCode);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExactReferencePlan(target.orElseThrow()));
    }

    private static Optional<ReferenceTarget> referenceTarget(
        final Map<String, ClassFile> classes,
        final ClassFile receiver,
        final boolean hashCode
    ) {
        if (receiver.isEnum()) {
            return Optional.of(new ReferenceTarget(receiver.name(), ""));
        }
        final String methodName = hashCode ? "hashCode" : "equals";
        final String descriptor = hashCode ? "()I" : "(Ljava/lang/Object;)Z";
        String current = receiver.name();
        final Set<String> visited = new HashSet<>();
        while (visited.add(current)) {
            if ("java/lang/Object".equals(current)) {
                return Optional.of(new ReferenceTarget(receiver.name(), ""));
            }
            final ClassFile currentClass = classes.get(current);
            if (currentClass == null) {
                return Optional.empty();
            }
            final Optional<MethodInfo> method = currentClass.method(methodName, descriptor);
            if (method.isPresent()) {
                return !method.orElseThrow().isStatic() && method.orElseThrow().code().isPresent()
                    ? Optional.of(new ReferenceTarget(receiver.name(), current))
                    : Optional.empty();
            }
            current = currentClass.superName();
        }
        return Optional.empty();
    }

    /**
     * Recursive declared shape used by verification, reachability, and lowering.
     *
     * @param descriptor erased JVM descriptor
     * @param referenceOwner direct reference owner when present
     * @param listElement exact List element shape when present
     * @param stringMap true for an exact direct Map&lt;String, String&gt; component
     * @param valid true when the signature has a closed syntactic shape
     */
    public record Shape(
        String descriptor,
        Optional<String> referenceOwner,
        Optional<Shape> listElement,
        boolean stringMap,
        boolean valid
    ) {
        public Shape {
            if (referenceOwner == null) {
                throw new IllegalArgumentException("record shape owner optional is null");
            }
            if (listElement == null) {
                throw new IllegalArgumentException("record shape element optional is null");
            }
        }

        /**
         * Creates a shape without exact String Map metadata.
         *
         * @param descriptor erased JVM descriptor
         * @param referenceOwner direct reference owner when present
         * @param listElement exact List element shape when present
         * @param valid true when the signature has a closed syntactic shape
         */
        public Shape(
            final String descriptor,
            final Optional<String> referenceOwner,
            final Optional<Shape> listElement,
            final boolean valid
        ) {
            this(descriptor, referenceOwner, listElement, false, valid);
        }

        /**
         * Returns true for an exact List or ArrayList shape.
         *
         * @return true when this shape has one planned List element
         */
        public boolean isList() {
            return listElement.isPresent();
        }

        /**
         * Returns true for an exact Map&lt;String, String&gt; shape.
         *
         * @return true when this shape is the admitted Map shape
         */
        public boolean isStringMap() {
            return stringMap;
        }

        /**
         * Returns true for an array shape.
         *
         * @return true when the erased descriptor is an array
         */
        public boolean isArray() {
            return descriptor.startsWith("[");
        }
    }

    /**
     * Returns the matched component fields in classfile order.
     *
     * @return component fields
     */
    public List<FieldInfo> fields() {
        final List<FieldInfo> result = new ArrayList<>(components.size());
        for (final Component component : components) {
            result.add(component.field());
        }
        return List.copyOf(result);
    }

    /**
     * Recognizes the exact javac record-equals bootstrap contract.
     *
     * @param classFile enclosing class
     * @param method enclosing method
     * @param dynamicRef resolved call-site metadata
     * @return normalized record metadata when every required dimension matches
     */
    public static Optional<RecordObjectMethodsCall> resolve(
        final ClassFile classFile,
        final MethodInfo method,
        final DynamicRef dynamicRef
    ) {
        return resolve(
            classFile,
            method,
            dynamicRef,
            "equals",
            "(Ljava/lang/Object;)Z",
            "(L" + classFile.name() + ";Ljava/lang/Object;)Z"
        );
    }

    /**
     * Recognizes the exact javac record-hashCode bootstrap contract.
     *
     * @param classFile enclosing class
     * @param method enclosing method
     * @param dynamicRef resolved call-site metadata
     * @return normalized record metadata when every required dimension matches
     */
    public static Optional<RecordObjectMethodsCall> resolveHashCode(
        final ClassFile classFile,
        final MethodInfo method,
        final DynamicRef dynamicRef
    ) {
        return resolve(classFile, method, dynamicRef, "hashCode", "()I", "(L" + classFile.name() + ";)I");
    }

    private static Optional<RecordObjectMethodsCall> resolve(
        final ClassFile classFile,
        final MethodInfo method,
        final DynamicRef dynamicRef,
        final String name,
        final String methodDescriptor,
        final String dynamicDescriptor
    ) {
        if (!classFile.isRecord()
            || !classFile.isFinal()
            || !"java/lang/Record".equals(classFile.superName())
            || method.isStatic()
            || !name.equals(method.name())
            || !methodDescriptor.equals(method.descriptor())
            || !name.equals(dynamicRef.name())
            || !dynamicDescriptor.equals(dynamicRef.descriptor())
            || dynamicRef.bootstrapReferenceKind() != 6
            || !"java/lang/runtime/ObjectMethods".equals(dynamicRef.bootstrapOwner())
            || !"bootstrap".equals(dynamicRef.bootstrapName())
            || !BOOTSTRAP_DESCRIPTOR.equals(dynamicRef.bootstrapDescriptor())) {
            return Optional.empty();
        }
        final List<FieldInfo> fields = instanceFields(classFile);
        if (!recordComponentsMatch(classFile.recordComponents().orElse(List.of()), fields)) {
            return Optional.empty();
        }
        final List<BootstrapArgument> arguments = dynamicRef.bootstrapArgumentDetails();
        if (arguments.size() != fields.size() + 2
            || arguments.get(0).kind() != BootstrapArgument.Kind.CLASS
            || !classFile.name().equals(arguments.get(0).text())
            || arguments.get(1).kind() != BootstrapArgument.Kind.STRING
            || !fieldNames(fields).equals(arguments.get(1).text())) {
            return Optional.empty();
        }
        for (int index = 0; index < fields.size(); index++) {
            if (!matchesGetter(arguments.get(index + 2), classFile.name(), fields.get(index))) {
                return Optional.empty();
            }
        }
        final List<Component> components = new ArrayList<>(fields.size());
        for (final FieldInfo field : fields) {
            components.add(new Component(field, shape(field)));
        }
        return Optional.of(new RecordObjectMethodsCall(components));
    }

    private static boolean matchesGetter(
        final BootstrapArgument argument,
        final String owner,
        final FieldInfo field
    ) {
        if (argument.kind() != BootstrapArgument.Kind.METHOD_HANDLE
            || argument.referenceKind() != 1
            || argument.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef getter = argument.methodRef().orElseThrow();
        return owner.equals(getter.owner())
            && field.name().equals(getter.name())
            && field.descriptor().equals(getter.descriptor());
    }

    private static boolean recordComponentsMatch(
        final List<RecordComponentInfo> components,
        final List<FieldInfo> fields
    ) {
        if (components.size() != fields.size()) {
            return false;
        }
        for (int index = 0; index < fields.size(); index++) {
            final RecordComponentInfo component = components.get(index);
            final FieldInfo field = fields.get(index);
            if (!field.hasValidDescriptor()
                || (field.accessFlags() & (ACC_PRIVATE | ACC_STATIC | ACC_FINAL)) != (ACC_PRIVATE | ACC_FINAL)
                || !component.name().equals(field.name())
                || !component.descriptor().equals(field.descriptor())
                || !sameOptionalString(component.signature(), field.signature())) {
                return false;
            }
        }
        return true;
    }

    private static Shape shape(final FieldInfo field) {
        final String descriptor = field.descriptor();
        if (descriptor.length() == 1) {
            return field.signature().isEmpty() ? primitiveShape(descriptor) : invalidShape(descriptor);
        }
        if (descriptor.startsWith("[")) {
            if (field.signature().isEmpty()) {
                return arrayShape(descriptor);
            }
            final Optional<ParsedType> parsed = parseSignature(field.signature().orElseThrow());
            return parsed.isPresent()
                && descriptor.equals(parsed.orElseThrow().erasure())
                && parsed.orElseThrow().shape().isArray()
                && parsed.orElseThrow().shape().valid()
                ? arrayShape(descriptor)
                : invalidShape(descriptor);
        }
        final Optional<String> owner = field.referenceOwner();
        if (owner.isEmpty()) {
            return invalidShape(descriptor);
        }
        if (isListOwner(owner.orElseThrow())) {
            if (field.signature().isEmpty()) {
                return invalidShape(descriptor);
            }
            final Optional<ParsedType> parsed = parseSignature(field.signature().orElseThrow());
            if (parsed.isEmpty()
                || !descriptor.equals(parsed.orElseThrow().erasure())
                || !parsed.orElseThrow().shape().isList()
                || !sameOptionalString(owner, parsed.orElseThrow().shape().referenceOwner())) {
                return invalidShape(descriptor);
            }
            return parsed.orElseThrow().shape();
        }
        if (isMapOwner(owner.orElseThrow())) {
            if (field.signature().isEmpty()) {
                return invalidShape(descriptor);
            }
            final Optional<ParsedType> parsed = parseSignature(field.signature().orElseThrow());
            if (parsed.isEmpty()
                || !descriptor.equals(parsed.orElseThrow().erasure())
                || !parsed.orElseThrow().shape().isStringMap()) {
                return invalidShape(descriptor);
            }
            return parsed.orElseThrow().shape();
        }
        if (field.signature().isEmpty()) {
            return referenceShape(descriptor, owner.orElseThrow());
        }
        final Optional<ParsedType> parsed = parseSignature(field.signature().orElseThrow());
        if (parsed.isEmpty()
            || !descriptor.equals(parsed.orElseThrow().erasure())
            || !parsed.orElseThrow().shape().valid()
            || !sameOptionalString(owner, parsed.orElseThrow().shape().referenceOwner())
            || parsed.orElseThrow().shape().isList()) {
            return invalidShape(descriptor);
        }
        return parsed.orElseThrow().shape();
    }

    private static boolean sameOptionalString(final Optional<String> left, final Optional<String> right) {
        if (left.isEmpty()) {
            return right.isEmpty();
        }
        return right.isPresent() && left.orElseThrow().equals(right.orElseThrow());
    }

    private static Optional<ParsedType> parseSignature(final String signature) {
        final SignatureCursor cursor = new SignatureCursor(signature);
        final Optional<ParsedType> parsed = cursor.type(false);
        if (parsed.isEmpty() || !cursor.exhausted()) {
            return Optional.empty();
        }
        return parsed;
    }

    private static Shape primitiveShape(final String descriptor) {
        return new Shape(descriptor, Optional.empty(), Optional.empty(), false, true);
    }

    private static Shape arrayShape(final String descriptor) {
        return new Shape(descriptor, Optional.empty(), Optional.empty(), false, true);
    }

    private static Shape referenceShape(final String descriptor, final String owner) {
        return new Shape(descriptor, Optional.of(owner), Optional.empty(), false, true);
    }

    private static Shape listShape(final String descriptor, final String owner, final Shape element) {
        return new Shape(descriptor, Optional.of(owner), Optional.of(element), false, element.valid());
    }

    private static Shape stringMapShape(final String descriptor, final String owner) {
        return new Shape(descriptor, Optional.of(owner), Optional.empty(), true, true);
    }

    private static Shape invalidShape(final String descriptor) {
        return new Shape(descriptor, Optional.empty(), Optional.empty(), false, false);
    }

    private static boolean isListOwner(final String owner) {
        return LIST.equals(owner) || ARRAY_LIST.equals(owner);
    }

    private static boolean isMapOwner(final String owner) {
        return MAP.equals(owner);
    }

    private record ParsedType(Shape shape, String erasure) {
    }

    private static final class SignatureCursor {
        private final String value;
        private int index;

        private SignatureCursor(final String value) {
            this.value = value;
        }

        private boolean exhausted() {
            return index == value.length();
        }

        private Optional<ParsedType> type(final boolean primitiveAllowed) {
            if (index >= value.length()) {
                return Optional.empty();
            }
            final char marker = value.charAt(index);
            if ("BCDFIJSZ".indexOf(marker) >= 0) {
                if (!primitiveAllowed) {
                    return Optional.empty();
                }
                final String descriptor = value.substring(index, index + 1);
                index++;
                return Optional.of(new ParsedType(primitiveShape(descriptor), descriptor));
            }
            if (marker == '[') {
                index++;
                final Optional<ParsedType> element = type(true);
                if (element.isEmpty()) {
                    return Optional.empty();
                }
                final String descriptor = "[" + element.orElseThrow().erasure();
                final Shape shape = element.orElseThrow().shape().valid()
                    ? arrayShape(descriptor)
                    : invalidShape(descriptor);
                return Optional.of(new ParsedType(shape, descriptor));
            }
            if (marker == 'T') {
                final int end = value.indexOf(';', index + 1);
                if (end < 0 || end == index + 1) {
                    return Optional.empty();
                }
                index = end + 1;
                return Optional.of(new ParsedType(invalidShape("Ljava/lang/Object;"), "Ljava/lang/Object;"));
            }
            if (marker != 'L') {
                return Optional.empty();
            }
            return classType();
        }

        private Optional<ParsedType> classType() {
            index++;
            final int ownerStart = index;
            while (index < value.length() && value.charAt(index) != ';' && value.charAt(index) != '<') {
                index++;
            }
            if (index == ownerStart || index >= value.length()) {
                return Optional.empty();
            }
            final String owner = value.substring(ownerStart, index);
            final String descriptor = "L" + owner + ";";
            if (!FieldInfo.isValidDescriptor(descriptor)) {
                return Optional.empty();
            }
            if (value.charAt(index) == ';') {
                index++;
                return Optional.of(new ParsedType(referenceShape(descriptor, owner), descriptor));
            }
            index++;
            final List<Shape> arguments = new ArrayList<>();
            while (index < value.length() && value.charAt(index) != '>') {
                final Optional<Shape> argument = typeArgument();
                if (argument.isEmpty()) {
                    return Optional.empty();
                }
                arguments.add(argument.orElseThrow());
            }
            if (arguments.isEmpty() || index >= value.length() || value.charAt(index) != '>') {
                return Optional.empty();
            }
            index++;
            if (index >= value.length() || value.charAt(index) != ';') {
                return Optional.empty();
            }
            index++;
            final Shape shape;
            if ("java/lang/String".equals(owner)) {
                shape = invalidShape(descriptor);
            } else if (isListOwner(owner) && arguments.size() == 1) {
                shape = listShape(descriptor, owner, arguments.getFirst());
            } else if (isMapOwner(owner)
                && arguments.size() == 2
                && exactStringShape(arguments.get(0))
                && exactStringShape(arguments.get(1))) {
                shape = stringMapShape(descriptor, owner);
            } else if (isMapOwner(owner)) {
                shape = invalidShape(descriptor);
            } else if (closed(arguments)) {
                shape = referenceShape(descriptor, owner);
            } else {
                shape = invalidShape(descriptor);
            }
            return Optional.of(new ParsedType(shape, descriptor));
        }

        private static boolean closed(final List<Shape> arguments) {
            for (final Shape argument : arguments) {
                if (!argument.valid()) {
                    return false;
                }
            }
            return true;
        }

        private static boolean exactStringShape(final Shape shape) {
            return shape.valid() && "Ljava/lang/String;".equals(shape.descriptor());
        }

        private Optional<Shape> typeArgument() {
            if (index >= value.length()) {
                return Optional.empty();
            }
            final char marker = value.charAt(index);
            if (marker == '*') {
                index++;
                return Optional.of(invalidShape("Ljava/lang/Object;"));
            }
            if (marker == '+' || marker == '-') {
                index++;
                final Optional<ParsedType> bound = type(false);
                return bound.isEmpty()
                    ? Optional.empty()
                    : Optional.of(invalidShape(bound.orElseThrow().erasure()));
            }
            final Optional<ParsedType> parsed = type(false);
            return parsed.isPresent()
                ? Optional.of(parsed.orElseThrow().shape())
                : Optional.empty();
        }
    }

    private static List<FieldInfo> instanceFields(final ClassFile classFile) {
        final List<FieldInfo> result = new ArrayList<>();
        for (final FieldInfo field : classFile.fields()) {
            if (!field.isStatic()) {
                result.add(field);
            }
        }
        return List.copyOf(result);
    }

    private static String fieldNames(final List<FieldInfo> fields) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                result.append(";");
            }
            result.append(fields.get(index).name());
        }
        return result.toString();
    }
}

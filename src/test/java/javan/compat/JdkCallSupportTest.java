package javan.compat;

import javan.classfile.ClassFile;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class JdkCallSupportTest {
    @Test
    void boundedOptionalOrElseThrowSupplierHasExactSupportAndRuntimeModule() {
        final MethodRef method = new MethodRef(
            "java/util/Optional",
            "orElseThrow",
            "(Ljava/util/function/Supplier;)Ljava/lang/Object;"
        );

        assertThat(List.of(
            JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(method),
            JdkCallSupport.isSupported(method),
            JdkCallSupport.runtimeModules(method)
        )).containsExactly(true, true, List.of("optional"));
    }

    @Test
    void boundedOptionalOrElseThrowSupplierRejectsWrongDescriptor() {
        final MethodRef method = new MethodRef(
            "java/util/Optional",
            "orElseThrow",
            "(Ljava/util/function/Supplier;)Ljava/lang/Throwable;"
        );

        assertThat(List.of(
            JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(method),
            JdkCallSupport.isSupported(method),
            JdkCallSupport.runtimeModules(method)
        )).containsExactly(false, false, List.of());
    }

    @Test
    void objectCloneIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Object",
            "clone",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mathRoundFloatIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "round",
            "(F)I"
        ))).isTrue();
    }

    @Test
    void mathMultiplyExactLongIntIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "multiplyExact",
            "(JI)J"
        ))).isTrue();
    }

    @Test
    void mathMultiplyExactLongLongIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "multiplyExact",
            "(JJ)J"
        ))).isTrue();
    }

    @Test
    void mathAddExactLongIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "addExact",
            "(JJ)J"
        ))).isTrue();
    }

    @Test
    void floatToRawIntBitsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Float",
            "floatToRawIntBits",
            "(F)I"
        ))).isTrue();
    }

    @Test
    void floatIsFiniteIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Float",
            "isFinite",
            "(F)Z"
        ))).isTrue();
    }

    @Test
    void doubleIsFiniteIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "isFinite",
            "(D)Z"
        ))).isTrue();
    }

    @Test
    void booleanParseBooleanIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Boolean",
            "parseBoolean",
            "(Ljava/lang/String;)Z"
        ))).isTrue();
    }

    @Test
    void stringIsBlankIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "isBlank",
            "()Z"
        ))).isTrue();
    }

    @Test
    void stringIsBlankRequiresStringsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/String",
            "isBlank",
            "()Z"
        ))).containsExactly("strings");
    }

    @Test
    void classDescriptorStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "descriptorString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void classComponentTypeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "componentType",
            "()Ljava/lang/Class;"
        ))).isTrue();
    }

    @Test
    void classArrayTypeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "arrayType",
            "()Ljava/lang/Class;"
        ))).isTrue();
    }

    @Test
    void classIsPrimitiveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "isPrimitive",
            "()Z"
        ))).isTrue();
    }

    @Test
    void classGetComponentTypeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getComponentType",
            "()Ljava/lang/Class;"
        ))).isTrue();
    }

    @Test
    void classGetTypeNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getTypeName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void classGetPackageNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getPackageName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void classGetSimpleNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Class",
            "getSimpleName",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void objectsRequireNonNullElseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "requireNonNullElse",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void objectsRequireNonNullElseGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "requireNonNullElseGet",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void objectsIsNullIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "isNull",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void objectsNonNullIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "nonNull",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void objectsToStringObjectIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "toString",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void objectsToStringObjectDefaultIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Objects",
            "toString",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void objectsToStringRequiresStringsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Objects",
            "toString",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        ))).containsExactly("strings");
    }

    @Test
    void hashMapComputeIfAbsentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "computeIfAbsent",
            "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void linkedHashMapComputeIfAbsentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "computeIfAbsent",
            "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void treeMapComputeIfAbsentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/TreeMap",
            "computeIfAbsent",
            "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void optionalOrElseGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Optional",
            "orElseGet",
            "(Ljava/util/function/Supplier;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void optionalIfPresentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Optional",
            "ifPresent",
            "(Ljava/util/function/Consumer;)V"
        ))).isTrue();
    }

    @Test
    void optionalOrIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Optional",
            "or",
            "(Ljava/util/function/Supplier;)Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void supplierGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/Supplier",
            "get",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void functionApplyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/Function",
            "apply",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void optionalFlatMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Optional",
            "flatMap",
            "(Ljava/util/function/Function;)Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void collectionsEmptyListIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "emptyList",
            "()Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void collectionsEmptySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "emptySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void collectionsUnmodifiableCollectionIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "unmodifiableCollection",
            "(Ljava/util/Collection;)Ljava/util/Collection;"
        ))).isTrue();
    }

    @Test
    void hashMapNewHashMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "newHashMap",
            "(I)Ljava/util/HashMap;"
        ))).isTrue();
    }

    @Test
    void linkedHashMapNewLinkedHashMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "newLinkedHashMap",
            "(I)Ljava/util/LinkedHashMap;"
        ))).isTrue();
    }

    @Test
    void hashSetNewHashSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "newHashSet",
            "(I)Ljava/util/HashSet;"
        ))).isTrue();
    }

    @Test
    void hashSetCapacityConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void hashSetLoadFactorConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "<init>",
            "(IF)V"
        ))).isTrue();
    }

    @Test
    void linkedHashSetNewLinkedHashSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "newLinkedHashSet",
            "(I)Ljava/util/LinkedHashSet;"
        ))).isTrue();
    }

    @Test
    void linkedHashSetCapacityConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void linkedHashSetLoadFactorConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "<init>",
            "(IF)V"
        ))).isTrue();
    }

    @Test
    void collectionAddIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "add",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void collectionAddAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "addAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void collectionRemoveAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void collectionRetainAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void listRemoveAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void listRetainAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListRemoveAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListRetainAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void arrayListIsEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "isEmpty",
            "()Z"
        ))).isTrue();
    }

    @Test
    void arrayListContainsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "contains",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "get",
            "(I)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListGetFirstIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "getFirst",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListGetLastIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "getLast",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListIndexOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "indexOf",
            "(Ljava/lang/Object;)I"
        ))).isTrue();
    }

    @Test
    void arrayListLastIndexOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "lastIndexOf",
            "(Ljava/lang/Object;)I"
        ))).isTrue();
    }

    @Test
    void arrayListRemoveObjectIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListAddAllAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "addAll",
            "(ILjava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "set",
            "(ILjava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListRemoveAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "remove",
            "(I)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListRemoveLastIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "removeLast",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListAddFirstIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "addFirst",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void arrayListAddLastIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "addLast",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void arrayListRemoveFirstIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "removeFirst",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void abstractListAddAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "add",
            "(ILjava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void abstractListAddIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "add",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void abstractListAddAllAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "addAll",
            "(ILjava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void abstractListClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void abstractListGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "get",
            "(I)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void abstractListIndexOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "indexOf",
            "(Ljava/lang/Object;)I"
        ))).isTrue();
    }

    @Test
    void abstractListIteratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "iterator",
            "()Ljava/util/Iterator;"
        ))).isTrue();
    }

    @Test
    void abstractListListIteratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "listIterator",
            "()Ljava/util/ListIterator;"
        ))).isTrue();
    }

    @Test
    void abstractListListIteratorAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "listIterator",
            "(I)Ljava/util/ListIterator;"
        ))).isTrue();
    }

    @Test
    void abstractListLastIndexOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "lastIndexOf",
            "(Ljava/lang/Object;)I"
        ))).isTrue();
    }

    @Test
    void abstractListSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void abstractListRemoveAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "remove",
            "(I)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void abstractListSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "set",
            "(ILjava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void arrayListIteratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "iterator",
            "()Ljava/util/Iterator;"
        ))).isTrue();
    }

    @Test
    void arrayListListIteratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "listIterator",
            "()Ljava/util/ListIterator;"
        ))).isTrue();
    }

    @Test
    void arrayListListIteratorAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "listIterator",
            "(I)Ljava/util/ListIterator;"
        ))).isTrue();
    }

    @Test
    void arrayListToArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void setAddAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "addAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void setRemoveAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void setRetainAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetAddAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "addAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetRemoveAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetRetainAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetAddAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "addAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetRemoveAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetRetainAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetContainsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "contains",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetContainsAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "containsAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void hashSetClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void hashSetSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void hashSetIsEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "isEmpty",
            "()Z"
        ))).isTrue();
    }

    @Test
    void hashSetIteratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "iterator",
            "()Ljava/util/Iterator;"
        ))).isTrue();
    }

    @Test
    void iteratorRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Iterator",
            "remove",
            "()V"
        ))).isTrue();
    }

    @Test
    void iteratorForEachRemainingIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Iterator",
            "forEachRemaining",
            "(Ljava/util/function/Consumer;)V"
        ))).isTrue();
    }

    @Test
    void iterableForEachIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Iterable",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).isTrue();
    }

    @Test
    void collectionRemoveIfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "removeIf",
            "(Ljava/util/function/Predicate;)Z"
        ))).isTrue();
    }

    @Test
    void arrayListRemoveIfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "removeIf",
            "(Ljava/util/function/Predicate;)Z"
        ))).isTrue();
    }

    @Test
    void predicateTestIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void mapForEachIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "forEach",
            "(Ljava/util/function/BiConsumer;)V"
        ))).isTrue();
    }

    @Test
    void biConsumerAcceptIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/BiConsumer",
            "accept",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void biFunctionApplyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/BiFunction",
            "apply",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapComputeIfPresentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "computeIfPresent",
            "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void hashMapComputeIfPresentIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "computeIfPresent",
            "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapComputeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "compute",
            "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void hashMapComputeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "compute",
            "(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapMergeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "merge",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void hashMapMergeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "merge",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void hashSetToArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void linkedHashSetContainsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "contains",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetContainsAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "containsAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void linkedHashSetSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void linkedHashSetIsEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "isEmpty",
            "()Z"
        ))).isTrue();
    }

    @Test
    void linkedHashSetIteratorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "iterator",
            "()Ljava/util/Iterator;"
        ))).isTrue();
    }

    @Test
    void linkedHashSetToArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void collectionToArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void collectionContainsAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "containsAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void collectionRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void collectionClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collection",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void listToArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void listContainsAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "containsAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void setToArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void setContainsAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "containsAll",
            "(Ljava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void setRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void setClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void hashSetCollectionConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "<init>",
            "(Ljava/util/Collection;)V"
        ))).isTrue();
    }

    @Test
    void linkedHashSetCollectionConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "<init>",
            "(Ljava/util/Collection;)V"
        ))).isTrue();
    }

    @Test
    void hashMapMapConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "<init>",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void linkedHashMapMapConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "<init>",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void mapRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "remove",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void hashMapRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "remove",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void linkedHashMapRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "remove",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void treeMapRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/TreeMap",
            "remove",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapRemoveIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "remove",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapRemoveKeyValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "remove",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void hashMapRemoveKeyValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "remove",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void linkedHashMapRemoveKeyValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "remove",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void treeMapRemoveKeyValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/TreeMap",
            "remove",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapRemoveKeyValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "remove",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void collectionsUnmodifiableSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "unmodifiableSet",
            "(Ljava/util/Set;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void collectionsUnmodifiableListIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "unmodifiableList",
            "(Ljava/util/List;)Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void collectionsSingletonSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "singleton",
            "(Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void collectionsSingletonListIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "singletonList",
            "(Ljava/lang/Object;)Ljava/util/List;"
        ))).isTrue();
    }

    @Test
    void collectionsSingletonMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "singletonMap",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void collectionsUnmodifiableMapIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Collections",
            "unmodifiableMap",
            "(Ljava/util/Map;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfSingletonIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapEntryIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "entry",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;"
        ))).isTrue();
    }

    @Test
    void setOfEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setCopyOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "copyOf",
            "(Ljava/util/Collection;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfSingletonIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfPairIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfTripleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfQuadrupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfQuintupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfSextupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfSeptupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfOctupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfNonupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfDecupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void setOfVarargsArrayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Set",
            "of",
            "([Ljava/lang/Object;)Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void hashMapCapacityConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void linkedHashMapCapacityConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapCapacityConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void hashMapLoadFactorConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "<init>",
            "(IF)V"
        ))).isTrue();
    }

    @Test
    void linkedHashMapLoadFactorConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "<init>",
            "(IF)V"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapMapConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "<init>",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapLoadFactorConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "<init>",
            "(IF)V"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapConcurrencyLevelConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "<init>",
            "(IFI)V"
        ))).isTrue();
    }

    @Test
    void mapOfPairIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfTripleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfQuadrupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfQuintupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfSextupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfSeptupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfOctupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfNonupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfDecupleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapOfEntriesIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "ofEntries",
            "([Ljava/util/Map$Entry;)Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void stringHashCodeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "hashCode",
            "()I"
        ))).isTrue();
    }

    @Test
    void threadLocalGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef("java/lang/ThreadLocal", "get", "()Ljava/lang/Object;")))
            .isTrue();
    }

    @Test
    void threadLocalCallsRequireThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef("java/lang/ThreadLocal", "set", "(Ljava/lang/Object;)V")))
            .containsExactly("threads");
    }

    @Test
    void socketGetSoLingerIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getSoLinger",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetSoLingerIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setSoLinger",
            "(ZI)V"
        ))).isTrue();
    }

    @Test
    void socketGetOobInlineIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getOOBInline",
            "()Z"
        ))).isTrue();
    }

    @Test
    void socketSetOobInlineIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setOOBInline",
            "(Z)V"
        ))).isTrue();
    }

    @Test
    void socketGetTrafficClassIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getTrafficClass",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetTrafficClassIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setTrafficClass",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void socketHostLocalBindConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "<init>",
            "(Ljava/lang/String;ILjava/net/InetAddress;I)V"
        ))).isTrue();
    }

    @Test
    void socketInetAddressLocalBindConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "<init>",
            "(Ljava/net/InetAddress;ILjava/net/InetAddress;I)V"
        ))).isTrue();
    }

    @Test
    void socketNoArgConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "<init>",
            "()V"
        ))).isTrue();
    }

    @Test
    void socketConnectSocketAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "connect",
            "(Ljava/net/SocketAddress;)V"
        ))).isTrue();
    }

    @Test
    void serverSocketNoArgConstructorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "<init>",
            "()V"
        ))).isTrue();
    }

    @Test
    void serverSocketBindSocketAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "bind",
            "(Ljava/net/SocketAddress;)V"
        ))).isTrue();
    }

    @Test
    void inetAddressGetByAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/InetAddress",
            "getByAddress",
            "([B)Ljava/net/InetAddress;"
        ))).isTrue();
    }

    @Test
    void inetAddressGetByAddressNamedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/InetAddress",
            "getByAddress",
            "(Ljava/lang/String;[B)Ljava/net/InetAddress;"
        ))).isTrue();
    }

    @Test
    void inetAddressGetAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/InetAddress",
            "getAddress",
            "()[B"
        ))).isTrue();
    }

    @Test
    void socketGetChannelIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getChannel",
            "()Ljava/nio/channels/SocketChannel;"
        ))).isTrue();
    }

    @Test
    void serverSocketGetChannelIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "getChannel",
            "()Ljava/nio/channels/ServerSocketChannel;"
        ))).isTrue();
    }

    @Test
    void threadBuilderUnstartedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "unstarted",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).isTrue();
    }

    @Test
    void genericThreadBuilderStartIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder",
            "start",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).isTrue();
    }

    @Test
    void genericThreadBuilderNameCounterIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder",
            "name",
            "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;"
        ))).isTrue();
    }

    @Test
    void typedThreadBuilderInheritInheritableThreadLocalsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "inheritInheritableThreadLocals",
            "(Z)Ljava/lang/Thread$Builder$OfVirtual;"
        ))).isTrue();
    }

    @Test
    void threadYieldIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "yield",
            "()V"
        ))).isTrue();
    }

    @Test
    void threadOnSpinWaitIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "onSpinWait",
            "()V"
        ))).isTrue();
    }

    @Test
    void threadGetPriorityIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "getPriority",
            "()I"
        ))).isTrue();
    }

    @Test
    void threadSetPriorityIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "setPriority",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void threadSetNameIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "setName",
            "(Ljava/lang/String;)V"
        ))).isTrue();
    }

    @Test
    void threadBuilderUnstartedRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "unstarted",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).containsExactly("threads");
    }

    @Test
    void genericThreadBuilderFactoryRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder",
            "factory",
            "()Ljava/util/concurrent/ThreadFactory;"
        ))).containsExactly("threads");
    }

    @Test
    void hashSetContainsRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/HashSet",
            "contains",
            "(Ljava/lang/Object;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void collectionClearRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Collection",
            "clear",
            "()V"
        ))).containsExactly("collections");
    }

    @Test
    void collectionAddAllRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Collection",
            "addAll",
            "(Ljava/util/Collection;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void collectionAddRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Collection",
            "add",
            "(Ljava/lang/Object;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void collectionRemoveAllRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Collection",
            "removeAll",
            "(Ljava/util/Collection;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void collectionRetainAllRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Collection",
            "retainAll",
            "(Ljava/util/Collection;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListSizeRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "size",
            "()I"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListGetFirstRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "getFirst",
            "()Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListIndexOfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "indexOf",
            "(Ljava/lang/Object;)I"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListLastIndexOfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "lastIndexOf",
            "(Ljava/lang/Object;)I"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListRemoveObjectRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListAddAllAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "addAll",
            "(ILjava/util/Collection;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListSetRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "set",
            "(ILjava/lang/Object;)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListRemoveAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "remove",
            "(I)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListAddLastRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "addLast",
            "(Ljava/lang/Object;)V"
        ))).containsExactly("collections");
    }

    @Test
    void arrayListRemoveFirstRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ArrayList",
            "removeFirst",
            "()Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListAddAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "add",
            "(ILjava/lang/Object;)V"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListAddRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "add",
            "(Ljava/lang/Object;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListAddAllAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "addAll",
            "(ILjava/util/Collection;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListClearRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "clear",
            "()V"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListGetRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "get",
            "(I)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListIndexOfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "indexOf",
            "(Ljava/lang/Object;)I"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListIteratorRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "iterator",
            "()Ljava/util/Iterator;"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListListIteratorRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "listIterator",
            "()Ljava/util/ListIterator;"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListLastIndexOfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "lastIndexOf",
            "(Ljava/lang/Object;)I"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListSizeRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "size",
            "()I"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListRemoveAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "remove",
            "(I)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void abstractListSetRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/AbstractList",
            "set",
            "(ILjava/lang/Object;)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void listIteratorPreviousIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ListIterator",
            "previous",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void listIteratorAddIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/ListIterator",
            "add",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void listIteratorRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/ListIterator",
            "previous",
            "()Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void iteratorRemoveRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Iterator",
            "remove",
            "()V"
        ))).containsExactly("collections");
    }

    @Test
    void iteratorForEachRemainingRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Iterator",
            "forEachRemaining",
            "(Ljava/util/function/Consumer;)V"
        ))).containsExactly("collections");
    }

    @Test
    void iterableForEachRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Iterable",
            "forEach",
            "(Ljava/util/function/Consumer;)V"
        ))).containsExactly("collections");
    }

    @Test
    void collectionRemoveIfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/Collection",
            "removeIf",
            "(Ljava/util/function/Predicate;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void predicateTestRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/function/Predicate",
            "test",
            "(Ljava/lang/Object;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void listListIteratorAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "listIterator",
            "(I)Ljava/util/ListIterator;"
        ))).isTrue();
    }

    @Test
    void listRemoveAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "remove",
            "(I)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void listRemoveAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/List",
            "remove",
            "(I)Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void listIndexOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "indexOf",
            "(Ljava/lang/Object;)I"
        ))).isTrue();
    }

    @Test
    void listIndexOfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/List",
            "indexOf",
            "(Ljava/lang/Object;)I"
        ))).containsExactly("collections");
    }

    @Test
    void listLastIndexOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "lastIndexOf",
            "(Ljava/lang/Object;)I"
        ))).isTrue();
    }

    @Test
    void listLastIndexOfRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/List",
            "lastIndexOf",
            "(Ljava/lang/Object;)I"
        ))).containsExactly("collections");
    }

    @Test
    void listRemoveObjectIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void listAddAllAtIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/List",
            "addAll",
            "(ILjava/util/Collection;)Z"
        ))).isTrue();
    }

    @Test
    void listRemoveObjectRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/List",
            "remove",
            "(Ljava/lang/Object;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void listAddAllAtRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/List",
            "addAll",
            "(ILjava/util/Collection;)Z"
        ))).containsExactly("collections");
    }

    @Test
    void linkedHashSetToArrayRequiresCollectionsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/LinkedHashSet",
            "toArray",
            "()[Ljava/lang/Object;"
        ))).containsExactly("collections");
    }

    @Test
    void inheritableThreadLocalGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/InheritableThreadLocal",
            "get",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void inheritableThreadLocalSetRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/InheritableThreadLocal",
            "set",
            "(Ljava/lang/Object;)V"
        ))).containsExactly("threads");
    }

    @Test
    void builtinCollectionInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("java/util/Collection"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_COLLECTION);
    }

    @Test
    void builtinMapInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("java/util/Map"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_MAP);
    }

    @Test
    void builtinMapEntryInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("java/util/Map$Entry"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_MAP_ENTRY);
    }

    @Test
    void builtinObjectArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[Ljava/lang/Object;"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_OBJECT_ARRAY);
    }

    @Test
    void builtinIntArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[I"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_INT_ARRAY);
    }

    @Test
    void builtinLongArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[J"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_LONG_ARRAY);
    }

    @Test
    void builtinFloatArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[F"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_FLOAT_ARRAY);
    }

    @Test
    void builtinDoubleArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[D"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_DOUBLE_ARRAY);
    }

    @Test
    void builtinByteArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[B"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_BYTE_ARRAY);
    }

    @Test
    void builtinBooleanArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[Z"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_BOOLEAN_ARRAY);
    }

    @Test
    void builtinShortArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[S"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_SHORT_ARRAY);
    }

    @Test
    void builtinCharArrayInstanceOfTargetIsSupported() {
        assertThat(JdkCallSupport.builtinInstanceOfTargetId("[C"))
            .contains(JdkCallSupport.BUILTIN_INSTANCEOF_CHAR_ARRAY);
    }

    @Test
    void characterValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "valueOf",
            "(C)Ljava/lang/Character;"
        ))).isTrue();
    }

    @Test
    void characterCharValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "charValue",
            "()C"
        ))).isTrue();
    }

    @Test
    void byteValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Byte",
            "valueOf",
            "(B)Ljava/lang/Byte;"
        ))).isTrue();
    }

    @Test
    void byteByteValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Byte",
            "byteValue",
            "()B"
        ))).isTrue();
    }

    @Test
    void shortValueOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Short",
            "valueOf",
            "(S)Ljava/lang/Short;"
        ))).isTrue();
    }

    @Test
    void shortShortValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Short",
            "shortValue",
            "()S"
        ))).isTrue();
    }

    @Test
    void virtualThreadPerTaskExecutorIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/Executors",
            "newVirtualThreadPerTaskExecutor",
            "()Ljava/util/concurrent/ExecutorService;"
        ))).isTrue();
    }

    @Test
    void threadPerTaskExecutorRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/Executors",
            "newThreadPerTaskExecutor",
            "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
        ))).containsExactly("threads");
    }

    @Test
    void mathAbsFloatIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "abs",
            "(F)F"
        ))).isTrue();
    }

    @Test
    void mathAbsDoubleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "abs",
            "(D)D"
        ))).isTrue();
    }

    @Test
    void mathMinFloatIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "min",
            "(FF)F"
        ))).isTrue();
    }

    @Test
    void mathMinDoubleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "min",
            "(DD)D"
        ))).isTrue();
    }

    @Test
    void mathMaxFloatIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "max",
            "(FF)F"
        ))).isTrue();
    }

    @Test
    void mathMaxDoubleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Math",
            "max",
            "(DD)D"
        ))).isTrue();
    }

    @Test
    void executorCloseIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "close",
            "()V"
        ))).isTrue();
    }

    @Test
    void futureStateQueriesAreSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/Future",
            "isDone",
            "()Z"
        ))).isTrue();
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/Future",
            "isCancelled",
            "()Z"
        ))).isTrue();
    }

    @Test
    void dateTimeFormatterBuilderAppendPatternIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/format/DateTimeFormatterBuilder",
            "appendPattern",
            "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;"
        ))).isTrue();
    }

    @Test
    void dateTimeFormatterBuilderToFormatterRequiresTimeRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/time/format/DateTimeFormatterBuilder",
            "toFormatter",
            "(Ljava/util/Locale;)Ljava/time/format/DateTimeFormatter;"
        ))).containsExactly("time");
    }

    @Test
    void executorExecuteRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/Executor",
            "execute",
            "(Ljava/lang/Runnable;)V"
        ))).containsExactly("threads");
    }

    @Test
    void executorSubmitRunnableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "submit",
            "(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"
        ))).isTrue();
    }

    @Test
    void futureCancelRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/Future",
            "cancel",
            "(Z)Z"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledThreadPoolExecutorScheduleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).isTrue();
    }

    @Test
    void scheduledThreadPoolExecutorScheduleAtFixedRateRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "scheduleAtFixedRate",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledThreadPoolExecutorScheduleWithFixedDelayIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "scheduleWithFixedDelay",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).isTrue();
    }

    @Test
    void executorAwaitTerminationIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "awaitTermination",
            "(JLjava/util/concurrent/TimeUnit;)Z"
        ))).isTrue();
    }

    @Test
    void executorShutdownNowRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ExecutorService",
            "shutdownNow",
            "()Ljava/util/List;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledThreadPoolExecutorAwaitTerminationIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "awaitTermination",
            "(JLjava/util/concurrent/TimeUnit;)Z"
        ))).isTrue();
    }

    @Test
    void scheduledThreadPoolExecutorShutdownNowRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "shutdownNow",
            "()Ljava/util/List;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceScheduleIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).isTrue();
    }

    @Test
    void scheduledExecutorServiceShutdownRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "shutdown",
            "()V"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceAwaitTerminationIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "awaitTermination",
            "(JLjava/util/concurrent/TimeUnit;)Z"
        ))).isTrue();
    }

    @Test
    void scheduledExecutorServiceShutdownNowRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "shutdownNow",
            "()Ljava/util/List;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceScheduleAtFixedRateRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "scheduleAtFixedRate",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).containsExactly("threads");
    }

    @Test
    void scheduledExecutorServiceScheduleWithFixedDelayRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledExecutorService",
            "scheduleWithFixedDelay",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ))).containsExactly("threads");
    }

    @Test
    void atomicBooleanConstructorWithInitialValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicBoolean",
            "<init>",
            "(Z)V"
        ))).isTrue();
    }

    @Test
    void atomicBooleanGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicBoolean",
            "get",
            "()Z"
        ))).isTrue();
    }

    @Test
    void atomicBooleanSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicBoolean",
            "set",
            "(Z)V"
        ))).isTrue();
    }

    @Test
    void atomicIntegerConstructorWithInitialValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "<init>",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void atomicIntegerGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "get",
            "()I"
        ))).isTrue();
    }

    @Test
    void atomicIntegerSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicInteger",
            "set",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void atomicReferenceConstructorWithInitialValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "<init>",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void atomicReferenceGetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "get",
            "()Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void atomicReferenceCompareAndSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "compareAndSet",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void atomicReferenceSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicReference",
            "set",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void atomicLongSetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/atomic/AtomicLong",
            "set",
            "(J)V"
        ))).isTrue();
    }

    @Test
    void normalizesInheritedScheduledThreadPoolExecutorCallFromApplicationSubclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void normalizesInheritedScheduledThreadPoolExecutorScheduleAtFixedRateCallFromApplicationSubclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "(I)V", Optional.empty())
            ),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "scheduleAtFixedRate",
                "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "scheduleAtFixedRate",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void normalizesInheritedScheduledThreadPoolExecutorScheduleWithFixedDelayCallFromApplicationSubclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "scheduleWithFixedDelay",
                "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "scheduleWithFixedDelay",
            "(Ljava/lang/Runnable;JJLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void normalizeInheritedSupportedJdkCallRejectsMissingClassFileEntry() {
        final Map<String, ClassFile> classes = new java.util.HashMap<>();
        classes.put("com/acme/Scheduler", null);

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            classes,
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).isEmpty();
    }

    @Test
    void normalizeInheritedSupportedJdkCallRejectsSubclassWithoutSuperclass() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "()V", Optional.empty())),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).isEmpty();
    }

    @Test
    void normalizeInheritedSupportedJdkCallRejectsSubclassOverride() {
        final ClassFile scheduler = new ClassFile(
            69,
            "com/acme/Scheduler",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(
                new MethodInfo(0, "<init>", "(I)V", Optional.empty()),
                new MethodInfo(0, "schedule", "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;", Optional.empty())
            ),
            Path.of("Scheduler.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(scheduler.name(), scheduler),
            new javan.classfile.MethodRef(
                "com/acme/Scheduler",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).isEmpty();
    }

    @Test
    void normalizeInheritedSupportedJdkCallResolvesAcrossIntermediateApplicationSuperclass() {
        final ClassFile middle = new ClassFile(
            69,
            "com/acme/Middle",
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Middle.class"),
            true
        );
        final ClassFile leaf = new ClassFile(
            69,
            "com/acme/Leaf",
            "com/acme/Middle",
            0,
            List.of(),
            List.of(),
            List.of(new MethodInfo(0, "<init>", "(I)V", Optional.empty())),
            Path.of("Leaf.class"),
            true
        );

        assertThat(JdkCallSupport.normalizeInheritedSupportedJdkCall(
            Map.of(middle.name(), middle, leaf.name(), leaf),
            new javan.classfile.MethodRef(
                "com/acme/Leaf",
                "schedule",
                "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
            )
        )).contains(new javan.classfile.MethodRef(
            "java/util/concurrent/ScheduledThreadPoolExecutor",
            "schedule",
            "(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"
        ));
    }

    @Test
    void lockSupportParkIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "park",
            "()V"
        ))).isTrue();
    }

    @Test
    void lockSupportParkNanosIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "parkNanos",
            "(J)V"
        ))).isTrue();
    }

    @Test
    void lockSupportParkUntilIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "parkUntil",
            "(J)V"
        ))).isTrue();
    }

    @Test
    void lockSupportBlockerOverloadIsNotSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "parkNanos",
            "(Ljava/lang/Object;J)V"
        ))).isFalse();
    }

    @Test
    void lockSupportUnparkRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/locks/LockSupport",
            "unpark",
            "(Ljava/lang/Thread;)V"
        ))).containsExactly("threads");
    }

    @Test
    void mapOfEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "of",
            "()Ljava/util/Map;"
        ))).isTrue();
    }

    @Test
    void mapContainsValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "containsValue",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void mapClearIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "clear",
            "()V"
        ))).isTrue();
    }

    @Test
    void mapPutAllIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "putAll",
            "(Ljava/util/Map;)V"
        ))).isTrue();
    }

    @Test
    void mapReplaceIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "replace",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void mapReplaceKeyValueIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "replace",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void mapIsEmptyIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "isEmpty",
            "()Z"
        ))).isTrue();
    }

    @Test
    void mapSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "size",
            "()I"
        ))).isTrue();
    }

    @Test
    void mapValuesIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "values",
            "()Ljava/util/Collection;"
        ))).isTrue();
    }

    @Test
    void booleanEqualsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Boolean",
            "equals",
            "(Ljava/lang/Object;)Z"
        ))).isTrue();
    }

    @Test
    void socketGetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getSoTimeout",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketIsBoundIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "isBound",
            "()Z"
        ))).isTrue();
    }

    @Test
    void socketIsInputShutdownIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "isInputShutdown",
            "()Z"
        ))).isTrue();
    }

    @Test
    void socketIsOutputShutdownIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "isOutputShutdown",
            "()Z"
        ))).isTrue();
    }

    @Test
    void socketSetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setSoTimeout",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void socketShutdownInputIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "shutdownInput",
            "()V"
        ))).isTrue();
    }

    @Test
    void socketShutdownOutputIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "shutdownOutput",
            "()V"
        ))).isTrue();
    }

    @Test
    void socketGetReuseAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getReuseAddress",
            "()Z"
        ))).isTrue();
    }

    @Test
    void socketSetReuseAddressIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setReuseAddress",
            "(Z)V"
        ))).isTrue();
    }

    @Test
    void socketGetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getReceiveBufferSize",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setReceiveBufferSize",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void socketGetSendBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "getSendBufferSize",
            "()I"
        ))).isTrue();
    }

    @Test
    void socketSetSendBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/Socket",
            "setSendBufferSize",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void serverSocketGetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "getSoTimeout",
            "()I"
        ))).isTrue();
    }

    @Test
    void serverSocketIsBoundIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "isBound",
            "()Z"
        ))).isTrue();
    }

    @Test
    void serverSocketIsClosedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "isClosed",
            "()Z"
        ))).isTrue();
    }

    @Test
    void serverSocketGetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "getReceiveBufferSize",
            "()I"
        ))).isTrue();
    }

    @Test
    void serverSocketSetReceiveBufferSizeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "setReceiveBufferSize",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void serverSocketSetSoTimeoutIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/net/ServerSocket",
            "setSoTimeout",
            "(I)V"
        ))).isTrue();
    }

    @Test
    void consumerAcceptIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/function/Consumer",
            "accept",
            "(Ljava/lang/Object;)V"
        ))).isTrue();
    }

    @Test
    void zonedDateTimeToInstantIsUnsupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/time/ZonedDateTime",
            "toInstant",
            "()Ljava/time/Instant;"
        ))).isFalse();
    }

    @Test
    void mapKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Map",
            "keySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void hashMapKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "keySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void linkedHashMapKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "keySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void treeMapKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/TreeMap",
            "keySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapKeySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "keySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void hashMapEntrySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/HashMap",
            "entrySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void linkedHashMapEntrySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/LinkedHashMap",
            "entrySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void treeMapEntrySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/TreeMap",
            "entrySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void concurrentHashMapEntrySetIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/concurrent/ConcurrentHashMap",
            "entrySet",
            "()Ljava/util/Set;"
        ))).isTrue();
    }

    @Test
    void typedThreadBuilderNameCounterRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "name",
            "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;"
        ))).containsExactly("threads");
    }

    @Test
    void typedThreadBuilderInheritInheritableThreadLocalsRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Thread$Builder$OfVirtual",
            "inheritInheritableThreadLocals",
            "(Z)Ljava/lang/Thread$Builder$OfVirtual;"
        ))).containsExactly("threads");
    }

    @Test
    void threadFactoryNewThreadRequiresThreadsRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/util/concurrent/ThreadFactory",
            "newThread",
            "(Ljava/lang/Runnable;)Ljava/lang/Thread;"
        ))).containsExactly("threads");
    }

    @Test
    void stringValueOfIntIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "valueOf",
            "(I)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stringDescribeConstableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "describeConstable",
            "()Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void stringResolveConstantDescStringReturnIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void stringResolveConstantDescObjectBridgeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/String",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void integerDescribeConstableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Integer",
            "describeConstable",
            "()Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void integerInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Integer",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void integerResolveConstantDescTypedReturnIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Integer",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Integer;"
        ))).isTrue();
    }

    @Test
    void integerResolveConstantDescObjectBridgeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Integer",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void longDescribeConstableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "describeConstable",
            "()Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void longCompareIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "compare",
            "(JJ)I"
        ))).isTrue();
    }

    @Test
    void longCompareUnsignedIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "compareUnsigned",
            "(JJ)I"
        ))).isTrue();
    }

    @Test
    void longInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void longResolveConstantDescTypedReturnIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Long;"
        ))).isTrue();
    }

    @Test
    void longResolveConstantDescObjectBridgeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Long",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void floatDescribeConstableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Float",
            "describeConstable",
            "()Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void floatInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Float",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void floatResolveConstantDescTypedReturnIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Float",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Float;"
        ))).isTrue();
    }

    @Test
    void floatResolveConstantDescObjectBridgeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Float",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void doubleDescribeConstableIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "describeConstable",
            "()Ljava/util/Optional;"
        ))).isTrue();
    }

    @Test
    void doubleInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void doubleResolveConstantDescTypedReturnIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Double;"
        ))).isTrue();
    }

    @Test
    void doubleResolveConstantDescObjectBridgeIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Double",
            "resolveConstantDesc",
            "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/Object;"
        ))).isTrue();
    }

    @Test
    void booleanInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Boolean",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void byteInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Byte",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void shortInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Short",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void characterInstanceToStringIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/lang/Character",
            "toString",
            "()Ljava/lang/String;"
        ))).isTrue();
    }

    @Test
    void booleanArraysCopyOfIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "copyOf",
            "([ZI)[Z"
        ))).isTrue();
    }

    @Test
    void byteArraysWholeFillIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "fill",
            "([BB)V"
        ))).isTrue();
    }

    @Test
    void byteArraysRangedFillIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "fill",
            "([BIIB)V"
        ))).isTrue();
    }

    @Test
    void byteArraysEqualsIsSupported() {
        assertThat(JdkCallSupport.isSupported(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "equals",
            "([B[B)Z"
        ))).isTrue();
    }

    @Test
    void pathsGetRequiresFilesystemRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/nio/file/Paths",
            "get",
            "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"
        ))).containsExactly("filesystem");
    }

    @Test
    void fileNotFoundExceptionIsAssignableToIOException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/io/FileNotFoundException", "java/io/IOException"))
            .isTrue();
    }

    @Test
    void arrayIndexOutOfBoundsExceptionIsAssignableToIndexOutOfBoundsException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable(
            "java/lang/ArrayIndexOutOfBoundsException",
            "java/lang/IndexOutOfBoundsException"
        )).isTrue();
    }

    @Test
    void noSuchElementExceptionIsAssignableToRuntimeException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/util/NoSuchElementException", "java/lang/RuntimeException"))
            .isTrue();
    }

    @Test
    void errorIsNotAssignableToException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/lang/Error", "java/lang/Exception"))
            .isFalse();
    }

    @Test
    void applicationThrowableIsNotPlatformAssignable() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("com/acme/ProblemException", "java/lang/Exception"))
            .isFalse();
    }

    @Test
    void platformThrowableIsNotAssignableToApplicationCatchType() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable("java/lang/IllegalStateException", "com/acme/ProblemException"))
            .isFalse();
    }

    @Test
    void stringIndexOutOfBoundsExceptionIsAssignableToIndexOutOfBoundsException() {
        assertThat(JdkCallSupport.isPlatformThrowableAssignable(
            "java/lang/StringIndexOutOfBoundsException",
            "java/lang/IndexOutOfBoundsException"
        )).isTrue();
    }

    @Test
    void platformThrowableCauseConstructorIsRecognized() {
        assertThat(JdkCallSupport.isPlatformThrowableCauseConstructor(new javan.classfile.MethodRef(
            "java/lang/IllegalStateException",
            "<init>",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V"
        ))).isTrue();
    }

    @Test
    void applicationCauseConstructorIsNotRecognized() {
        assertThat(JdkCallSupport.isPlatformThrowableCauseConstructor(new javan.classfile.MethodRef(
            "com/acme/ProblemException",
            "<init>",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V"
        ))).isFalse();
    }

    @Test
    void platformThrowableCauseFactoryIsNotRecognized() {
        assertThat(JdkCallSupport.isPlatformThrowableCauseConstructor(new javan.classfile.MethodRef(
            "java/lang/IllegalStateException",
            "create",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V"
        ))).isFalse();
    }

    @Test
    void platformThrowableWrongCauseDescriptorIsNotRecognized() {
        assertThat(JdkCallSupport.isPlatformThrowableCauseConstructor(new javan.classfile.MethodRef(
            "java/lang/IllegalStateException",
            "<init>",
            "(Ljava/lang/Throwable;)V"
        ))).isFalse();
    }

    @Test
    void exactMathCallTransportsArithmeticException() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/lang/Math",
            "toIntExact",
            "(J)I"
        ))).containsExactly("java/lang/ArithmeticException");
    }

    @Test
    void localeLowerCaseTransportsNullPointerException() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/lang/String",
            "toLowerCase",
            "(Ljava/util/Locale;)Ljava/lang/String;"
        ))).containsExactly("java/lang/NullPointerException");
    }

    @Test
    void doubleParseTransportsItsTwoPlatformExceptions() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/lang/Double",
            "parseDouble",
            "(Ljava/lang/String;)D"
        ))).containsExactly(
            "java/lang/NullPointerException",
            "java/lang/NumberFormatException"
        );
    }

    @Test
    void doubleParseRequiresStringRuntimeModule() {
        assertThat(JdkCallSupport.runtimeModules(new javan.classfile.MethodRef(
            "java/lang/Double",
            "parseDouble",
            "(Ljava/lang/String;)D"
        ))).containsExactly("strings");
    }

    @Test
    void wholeByteArrayFillTransportsNullPointerException() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "fill",
            "([BB)V"
        ))).containsExactly("java/lang/NullPointerException");
    }

    @Test
    void rangedByteArrayFillTransportsItsThreePlatformExceptions() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "fill",
            "([BIIB)V"
        ))).containsExactly(
            "java/lang/ArrayIndexOutOfBoundsException",
            "java/lang/IllegalArgumentException",
            "java/lang/NullPointerException"
        );
    }

    @Test
    void unsupportedArrayFillDescriptorHasNoTransportedPlatformExceptions() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/util/Arrays",
            "fill",
            "([II)V"
        ))).isEmpty();
    }

    @Test
    void threadJoinTransportsInterruptedException() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/lang/Thread",
            "join",
            "()V"
        ))).containsExactly("java/lang/InterruptedException");
    }

    @Test
    void nonTransportingCallHasNoPlatformThrowableTypes() {
        assertThat(JdkCallSupport.transportedPlatformThrowableTypes(new javan.classfile.MethodRef(
            "java/lang/String",
            "length",
            "()I"
        ))).isEmpty();
    }

    @Test
    void unknownJdkThrowableIsNotAdmittedWithoutAnExactHierarchyEdge() {
        assertThat(JdkCallSupport.isPlatformThrowable("java/nio/file/NoSuchFileException")).isFalse();
    }

    @Test
    void platformThrowableParentSnapshotIsImmutable() {
        assertThatThrownBy(() -> JdkCallSupport.platformThrowableParents().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}

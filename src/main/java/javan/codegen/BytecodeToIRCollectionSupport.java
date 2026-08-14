package javan.codegen;

import javan.analysis.EntryPoint;
import javan.analysis.FunctionValueFlow;
import javan.analysis.InstantiatedTypeAnalysis;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.ir.IrDispatch;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrType;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRDynamicSupport.*;
import static javan.codegen.BytecodeToIRInvokeSupport.*;
import static javan.codegen.BytecodeToIRThreadSupport.*;
import static javan.codegen.BytecodeToIRMetadataSupport.*;

final class BytecodeToIRCollectionSupport {
    private static final int ARRAYS_FILL_STATUS_SUCCESS = 0;
    private static final int ARRAYS_FILL_STATUS_NULL = 1;
    private static final int ARRAYS_FILL_STATUS_INVERTED_RANGE = 2;

    private BytecodeToIRCollectionSupport() {
    }

    static boolean lowerJdkCollectionStaticCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        final String owner = methodRef.owner();
        final String name = methodRef.name();
        final String descriptor = methodRef.descriptor();
        if ("java/util/HashMap".equals(owner)
            && "newHashMap".equals(name)
            && "(I)Ljava/util/HashMap;".equals(descriptor)) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_hashmap_new_with_expected_mappings", arguments)));
            return true;
        }
        if ("java/util/LinkedHashMap".equals(owner)
            && "newLinkedHashMap".equals(name)
            && "(I)Ljava/util/LinkedHashMap;".equals(descriptor)) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_linkedhashmap_new_with_expected_mappings", arguments)));
            return true;
        }
        if ("java/util/HashSet".equals(owner)
            && "newHashSet".equals(name)
            && "(I)Ljava/util/HashSet;".equals(descriptor)) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_hashset_new_with_expected_elements", arguments)));
            return true;
        }
        if ("java/util/LinkedHashSet".equals(owner)
            && "newLinkedHashSet".equals(name)
            && "(I)Ljava/util/LinkedHashSet;".equals(descriptor)) {
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_linkedhashset_new_with_expected_elements", arguments)));
            return true;
        }
        if ("java/util/Map".equals(owner)) {
            if ("entry".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_entry_new", arguments)));
                return true;
            }
            if ("of".equals(name) && "()Ljava/util/Map;".equals(descriptor)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_empty", List.of())));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_singleton", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_pair", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_triple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_quadruple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_quintuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_sextuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_septuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_octuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_nonuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_decuple", arguments)));
                return true;
            }
            if ("ofEntries".equals(name) && "([Ljava/util/Map$Entry;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_of_entries", arguments)));
                return true;
            }
            if (!"copyOf".equals(name) || !"(Ljava/util/Map;)Ljava/util/Map;".equals(descriptor)) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_copy_of", arguments)));
            return true;
        }
        if ("java/util/Collections".equals(owner)) {
            if ("unmodifiableCollection".equals(name) && "(Ljava/util/Collection;)Ljava/util/Collection;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_unmodifiable", arguments)));
                return true;
            }
            if ("emptySet".equals(name) && "()Ljava/util/Set;".equals(descriptor)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_empty", List.of())));
                return true;
            }
            if ("singleton".equals(name) && "(Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_singleton", arguments)));
                return true;
            }
            if ("singletonList".equals(name) && "(Ljava/lang/Object;)Ljava/util/List;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                final List<IrExpression> callArguments = new ArrayList<>();
                callArguments.add(IrExpression.intLiteral(1));
                callArguments.addAll(arguments);
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of", callArguments)));
                return true;
            }
            if ("emptyList".equals(name) && "()Ljava/util/List;".equals(descriptor)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of", List.of(IrExpression.intLiteral(0)))));
                return true;
            }
            if ("unmodifiableList".equals(name) && "(Ljava/util/List;)Ljava/util/List;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_unmodifiable", arguments)));
                return true;
            }
            if ("singletonMap".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_singleton", arguments)));
                return true;
            }
            if ("emptyMap".equals(name) && "()Ljava/util/Map;".equals(descriptor)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_empty", List.of())));
                return true;
            }
            if ("unmodifiableMap".equals(name) && "(Ljava/util/Map;)Ljava/util/Map;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_unmodifiable", arguments)));
                return true;
            }
            if (!"unmodifiableSet".equals(name) || !"(Ljava/util/Set;)Ljava/util/Set;".equals(descriptor)) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_unmodifiable", arguments)));
            return true;
        }
        if (!"java/util/List".equals(owner)) {
            if (!"java/util/Set".equals(owner)) {
                return false;
            }
            if ("copyOf".equals(name) && "(Ljava/util/Collection;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_copy_of", arguments)));
                return true;
            }
            if ("of".equals(name) && "()Ljava/util/Set;".equals(descriptor)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_empty", List.of())));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_singleton", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_pair", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_triple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_quadruple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_quintuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_sextuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_septuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_octuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_nonuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_decuple", arguments)));
                return true;
            }
            if ("of".equals(name) && "([Ljava/lang/Object;)Ljava/util/Set;".equals(descriptor)) {
                final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_set_of_array", arguments)));
                return true;
            }
            return false;
        }
        if ("copyOf".equals(name)) {
            if (!"(Ljava/util/Collection;)Ljava/util/List;".equals(descriptor)) {
                return false;
            }
            final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_copy_of", arguments)));
            return true;
        }
        if (!"of".equals(name)) {
            return false;
        }
        final List<IrExpression> arguments = popArguments(classFile, method, stack, MethodDescriptor.parse(methodRef.descriptor()));
        if ("([Ljava/lang/Object;)Ljava/util/List;".equals(descriptor)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of_array", arguments)));
            return true;
        }
        final List<IrExpression> callArguments = new ArrayList<>();
        callArguments.add(IrExpression.intLiteral(arguments.size()));
        callArguments.addAll(arguments);
        stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_of", callArguments)));
        return true;
    }

    static boolean lowerJdkCollectionInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ((!isJdkCollectionOwner(methodRef.owner())
            && !isJdkSetEqualsOwner(methodRef.owner())
            && !"java/lang/CharSequence".equals(methodRef.owner()))
            || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        if (isJdkSetEqualsOwner(methodRef.owner())
            && "equals(Ljava/lang/Object;)Z".equals(methodRef.name() + methodRef.descriptor())
            && !isJdkSetEqualsInvocation(instruction, methodRef)) {
            return false;
        }
        if (isSupportedMapComputeIfAbsentOwner(methodRef)
            && hasTopStackKind(stack, StackKind.LAMBDA_FUNCTION)) {
            lowerMapComputeIfAbsentLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        if (isInlineCollectionRemoveIfLambdaCall(methodRef, stack)) {
            lowerCollectionRemoveIfLambdaCall(classFile, method, instruction, instructions, stack, localDeclarations);
            return true;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        return lowerJdkCollectionInstanceCall(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            materializedLambdaMethods,
            functionValueFlow.isMaterializedFunction(
                classFile.name(),
                method.name(),
                method.descriptor(),
                instruction.offset()
            ),
            instantiatedTypes,
            methodRef,
            instructions,
            stack,
            localDeclarations,
            arguments,
            receiver
        );
    }

    private static void lowerMapComputeIfAbsentLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_FUNCTION, "function lambda");
        final IrExpression key = popObject(classFile, method, instruction, stack);
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String existingLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            existingLocal,
            IrExpression.objectCall("javan_map_get", List.of(receiver, key))
        ));
        final String presentLabel = "label_map_compute_if_absent_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_map_compute_if_absent_end_" + instruction.offset() + "_" + localDeclarations.size();
        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.branchIf(
            presentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(existingLocal), IrExpression.objectNull())
        ));
        final String computedLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            computedLocal,
            invokeFunctionLambdaExpression(lambda, key)
        ));
        final String storeLabel = "label_map_compute_if_absent_store_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            storeLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(computedLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(storeLabel));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_map_put",
            List.of(receiver, key, IrExpression.objectLocal(computedLocal))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(presentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(existingLocal)));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static boolean isSupportedMapComputeIfAbsentOwner(final MethodRef methodRef) {
        if (!"computeIfAbsent".equals(methodRef.name())
            || !"(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(methodRef.descriptor())) {
            return false;
        }
        return "java/util/Map".equals(methodRef.owner())
            || "java/util/HashMap".equals(methodRef.owner())
            || "java/util/LinkedHashMap".equals(methodRef.owner())
            || "java/util/TreeMap".equals(methodRef.owner());
    }

    static boolean lowerJdkCollectionConstructorCall(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if ("java/util/ArrayList".equals(methodRef.owner()) && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(I)V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(Ljava/util/Collection;)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_all", List.of(receiver, arguments.getFirst())));
                return true;
            }
        }
        if (("java/util/HashSet".equals(methodRef.owner()) || "java/util/LinkedHashSet".equals(methodRef.owner()))
            && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(I)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_set_initialize_capacity", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("(IF)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid(
                    "javan_set_initialize_capacity_with_load_factor",
                    List.of(receiver, arguments.get(0), arguments.get(1))
                ));
                return true;
            }
            if ("(Ljava/util/Collection;)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_hashset_add_all", List.of(receiver, arguments.getFirst())));
                return true;
            }
        }
        if (("java/util/HashMap".equals(methodRef.owner()) || "java/util/LinkedHashMap".equals(methodRef.owner()))
            && "<init>".equals(methodRef.name())) {
            if ("()V".equals(methodRef.descriptor())) {
                return true;
            }
            if ("(I)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_map_initialize_capacity", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("(IF)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid(
                    "javan_map_initialize_capacity_with_load_factor",
                    List.of(receiver, arguments.get(0), arguments.get(1))
                ));
                return true;
            }
            if ("(Ljava/util/Map;)V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_map_put_all", List.of(receiver, arguments.getFirst())));
                return true;
            }
        }
        if ("java/util/EnumMap".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/lang/Class;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_enummap_initialize",
                List.of(receiver, arguments.getFirst())
            ));
            return true;
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_map_initialize_capacity", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(Ljava/util/Map;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_map_put_all", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(IF)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_map_initialize_capacity_with_load_factor",
                List.of(receiver, arguments.get(0), arguments.get(1))
            ));
            return true;
        }
        if ("java/util/concurrent/ConcurrentHashMap".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "(IFI)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_map_initialize_capacity_with_load_factor_and_concurrency",
                List.of(receiver, arguments.get(0), arguments.get(1), arguments.get(2))
            ));
            return true;
        }
        if (isJdkMapClass(methodRef.owner()) && "<init>".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            return true;
        }
        return false;
    }

    static boolean lowerThreadConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/lang/Thread".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("(Ljava/lang/Runnable;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_target", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_name", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("(Ljava/lang/Runnable;Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_target", List.of(receiver, arguments.get(0))));
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_name", List.of(receiver, arguments.get(1))));
            return true;
        }
        return false;
    }

    static boolean lowerScheduledThreadPoolExecutorConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/util/concurrent/ScheduledThreadPoolExecutor".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_scheduled_thread_pool_executor_init",
                List.of(receiver, arguments.getFirst())
            ));
            return true;
        }
        if ("(ILjava/util/concurrent/ThreadFactory;Ljava/util/concurrent/RejectedExecutionHandler;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_scheduled_thread_pool_executor_init_full",
                List.of(receiver, arguments.get(0), arguments.get(1), arguments.get(2))
            ));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicLongConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/util/concurrent/atomic/AtomicLong".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("(J)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_long_init",
                List.of(receiver, arguments.getFirst())
            ));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicIntegerConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/util/concurrent/atomic/AtomicInteger".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_integer_init",
                List.of(receiver, IrExpression.intLiteral(0))
            ));
            return true;
        }
        if ("(I)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_integer_init",
                List.of(receiver, arguments.getFirst())
            ));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicBooleanConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/util/concurrent/atomic/AtomicBoolean".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_boolean_init",
                List.of(receiver, IrExpression.intLiteral(0))
            ));
            return true;
        }
        if ("(Z)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_boolean_init",
                List.of(receiver, arguments.getFirst())
            ));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicReferenceConstructor(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!"java/util/concurrent/atomic/AtomicReference".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_reference_init",
                List.of(receiver, IrExpression.objectNull())
            ));
            return true;
        }
        if ("(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid(
                "javan_atomic_reference_init",
                List.of(receiver, arguments.getFirst())
            ));
            return true;
        }
        return false;
    }

    static boolean lowerThreadLocalConstructor(final MethodRef methodRef) {
        return ("java/lang/ThreadLocal".equals(methodRef.owner())
            || "java/lang/InheritableThreadLocal".equals(methodRef.owner()))
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }

    static boolean lowerDateTimeFormatterBuilderConstructor(final MethodRef methodRef) {
        return DATE_TIME_FORMATTER_BUILDER_OWNER.equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }

    static boolean lowerDateTimeFormatterBuilderInstanceCall(
        final MethodRef methodRef,
        final List<StackValue> stack,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!DATE_TIME_FORMATTER_BUILDER_OWNER.equals(methodRef.owner())) {
            return false;
        }
        if ("parseCaseInsensitive".equals(methodRef.name())
            && "()Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_datetime_formatter_builder_parse_case_insensitive", List.of(receiver))));
            return true;
        }
        if ("appendPattern".equals(methodRef.name())
            && "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_datetime_formatter_builder_append_pattern", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("appendZoneText".equals(methodRef.name())
            && "(Ljava/time/format/TextStyle;)Ljava/time/format/DateTimeFormatterBuilder;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_datetime_formatter_builder_append_zone_text", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        if ("toFormatter".equals(methodRef.name())
            && "(Ljava/util/Locale;)Ljava/time/format/DateTimeFormatter;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_datetime_formatter_builder_to_formatter", List.of(receiver, arguments.getFirst()))));
            return true;
        }
        return false;
    }

    static boolean lowerDateTimeFormatterBuilderVirtualCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!DATE_TIME_FORMATTER_BUILDER_OWNER.equals(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        return lowerDateTimeFormatterBuilderInstanceCall(methodRef, stack, arguments, receiver);
    }

    static boolean lowerThreadLocalInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!isThreadLocalOwner(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        return lowerThreadLocalInstanceCall(methodRef, instructions, stack, localDeclarations, arguments, receiver);
    }

    static boolean lowerAtomicLongInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/util/concurrent/atomic/AtomicLong".equals(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        if ("get".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_atomic_long_get", List.of(receiver))));
            return true;
        }
        if ("set".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popLong(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_atomic_long_set", List.of(receiver, argument)));
            return true;
        }
        if ("incrementAndGet".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_atomic_long_increment_and_get", List.of(receiver))));
            return true;
        }
        if ("decrementAndGet".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_atomic_long_decrement_and_get", List.of(receiver))));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicBooleanInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/util/concurrent/atomic/AtomicBoolean".equals(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        if ("get".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_atomic_boolean_get", List.of(receiver))));
            return true;
        }
        if ("set".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_atomic_boolean_set", List.of(receiver, argument)));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicIntegerInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/util/concurrent/atomic/AtomicInteger".equals(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        if ("get".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_atomic_integer_get", List.of(receiver))));
            return true;
        }
        if ("set".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popInt(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_atomic_integer_set", List.of(receiver, argument)));
            return true;
        }
        if ("getAndIncrement".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_atomic_integer_get_and_increment", List.of(receiver))));
            return true;
        }
        if ("incrementAndGet".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_atomic_integer_increment_and_get", List.of(receiver))));
            return true;
        }
        if ("decrementAndGet".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_atomic_integer_decrement_and_get", List.of(receiver))));
            return true;
        }
        return false;
    }

    static boolean lowerAtomicReferenceInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (!"java/util/concurrent/atomic/AtomicReference".equals(methodRef.owner()) || !JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        if ("get".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_atomic_reference_get", List.of(receiver))));
            return true;
        }
        if ("compareAndSet".equals(methodRef.name()) && "(Ljava/lang/Object;Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression nextValue = popObject(classFile, method, stack);
            final IrExpression expectedValue = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_atomic_reference_compare_and_set",
                List.of(receiver, expectedValue, nextValue)
            )));
            return true;
        }
        if ("set".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            final IrExpression argument = popObject(classFile, method, stack);
            final IrExpression receiver = popObject(classFile, method, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_atomic_reference_set", List.of(receiver, argument)));
            return true;
        }
        return false;
    }

    static boolean lowerThreadLocalInstanceCall(
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        if (!isThreadLocalOwner(methodRef.owner())) {
            return false;
        }
        if ("get".equals(methodRef.name()) && "()Ljava/lang/Object;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_thread_local_get", List.of(receiver));
            return true;
        }
        if ("set".equals(methodRef.name()) && "(Ljava/lang/Object;)V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_local_set", List.of(receiver, arguments.getFirst())));
            return true;
        }
        if ("remove".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_local_remove", List.of(receiver)));
            return true;
        }
        return false;
    }

    private static boolean isThreadLocalOwner(final String owner) {
        return "java/lang/ThreadLocal".equals(owner)
            || "java/lang/InheritableThreadLocal".equals(owner);
    }

    static boolean lowerJdkCollectionInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final boolean materializedFunction,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final List<IrExpression> arguments,
        final IrExpression receiver
    ) {
        final String signature = methodRef.name() + methodRef.descriptor();
        if ("java/lang/CharSequence".equals(methodRef.owner())) {
            if ("length()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_char_sequence_length", List.of(receiver))));
                return true;
            }
            if ("charAt(I)C".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_char_sequence_char_at", List.of(receiver, arguments.getFirst()))));
                return true;
            }
        }
        if (isJdkListClass(methodRef.owner())) {
            if ("add(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("add(ILjava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_at", List.of(receiver, arguments.get(0), arguments.get(1))));
                return true;
            }
            if ("addAll(ILjava/util/Collection;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add_all_at", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("addAll(Ljava/util/Collection;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add_all", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("get(I)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_list_get", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("getFirst()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_list_get_first", List.of(receiver));
                return true;
            }
            if ("getLast()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_list_get_last", List.of(receiver));
                return true;
            }
            if ("indexOf(Ljava/lang/Object;)I".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_list_index_of", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("lastIndexOf(Ljava/lang/Object;)I".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_list_last_index_of", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("set(ILjava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_set", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("remove(I)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_at", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("removeLast()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_last", List.of(receiver));
                return true;
            }
            if ("addFirst(Ljava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_first", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("addLast(Ljava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_last", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("removeFirst()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_first", List.of(receiver));
                return true;
            }
            if ("listIterator()Ljava/util/ListIterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
            if ("listIterator(I)Ljava/util/ListIterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator_at", List.of(receiver, arguments.getFirst()))));
                return true;
            }
        }
        if ("java/util/AbstractList".equals(methodRef.owner())) {
            if ("add(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_collection_add", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("add(ILjava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_at", List.of(receiver, arguments.get(0), arguments.get(1))));
                return true;
            }
            if ("addAll(ILjava/util/Collection;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_arraylist_add_all_at", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("clear()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_clear", List.of(receiver)));
                return true;
            }
            if ("get(I)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_list_get", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("indexOf(Ljava/lang/Object;)I".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_list_index_of", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("iterator()Ljava/util/Iterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
            if ("listIterator()Ljava/util/ListIterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
            if ("listIterator(I)Ljava/util/ListIterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator_at", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("lastIndexOf(Ljava/lang/Object;)I".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_list_last_index_of", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("size()I".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_list_size", List.of(receiver));
                return true;
            }
            if ("remove(I)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_at", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("set(ILjava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_set", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
        }
        if (isJdkListOrCollection(methodRef.owner())) {
            if ("add(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_collection_add", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("addAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_collection_add_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("removeAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_remove_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("retainAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_retain_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_is_empty", List.of(receiver))));
                return true;
            }
            if ("contains(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_contains", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("remove(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_remove", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("removeIf(Ljava/util/function/Predicate;)Z".equals(signature)) {
                final String changedLocal = lowerCollectionRemoveIfCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.getFirst()
                );
                stack.add(StackValue.intExpression(IrExpression.intLocal(changedLocal)));
                return true;
            }
            if ("containsAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_contains_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("clear()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_clear", List.of(receiver)));
                return true;
            }
            if ("iterator()Ljava/util/Iterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
            if ("listIterator()Ljava/util/ListIterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
            if ("listIterator(I)Ljava/util/ListIterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator_at", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("toArray()[Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_to_array", List.of(receiver))));
                return true;
            }
        }
        if (isJdkSetEqualsOwner(methodRef.owner()) && "equals(Ljava/lang/Object;)Z".equals(signature)) {
            stack.add(StackValue.intExpression(
                IrExpression.intCall("javan_set_equals", List.of(receiver, arguments.getFirst()))
            ));
            return true;
        }
        if (isJdkSetOwner(methodRef.owner())) {
            if ("add(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_set_add", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("addAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_hashset_add_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("removeAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_remove_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("retainAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_retain_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("contains(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_contains", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("remove(Ljava/lang/Object;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_remove", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("removeIf(Ljava/util/function/Predicate;)Z".equals(signature)) {
                final String changedLocal = lowerCollectionRemoveIfCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.getFirst()
                );
                stack.add(StackValue.intExpression(IrExpression.intLocal(changedLocal)));
                return true;
            }
            if ("containsAll(Ljava/util/Collection;)Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_contains_all", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("clear()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_clear", List.of(receiver)));
                return true;
            }
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_is_empty", List.of(receiver))));
                return true;
            }
            if ("iterator()Ljava/util/Iterator;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_iterator", List.of(receiver))));
                return true;
            }
            if ("toArray()[Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_to_array", List.of(receiver))));
                return true;
            }
        }
        if ("java/util/List".equals(methodRef.owner())) {
            if ("addFirst(Ljava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_arraylist_add_first", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("set(ILjava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_set", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("removeLast()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_arraylist_remove_last", List.of(receiver));
                return true;
            }
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_is_empty", List.of(receiver))));
                return true;
            }
            if ("get(I)Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get", List.of(receiver, arguments.getFirst()))));
                return true;
            }
            if ("getFirst()Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get_first", List.of(receiver))));
                return true;
            }
            if ("getLast()Ljava/lang/Object;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_list_get_last", List.of(receiver))));
                return true;
            }
        }
        if ("java/lang/Iterable".equals(methodRef.owner())) {
            if ("forEach(Ljava/util/function/Consumer;)V".equals(signature)) {
                lowerIteratorForEachRemainingCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    IrExpression.objectCall("javan_list_iterator", List.of(receiver)),
                    arguments.getFirst()
                );
                return true;
            }
        }
        if ("java/util/Iterator".equals(methodRef.owner())) {
            if ("hasNext()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_iterator_has_next", List.of(receiver))));
                return true;
            }
            if ("next()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_iterator_next", List.of(receiver));
                return true;
            }
            if ("remove()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_iterator_remove", List.of(receiver)));
                return true;
            }
            if ("forEachRemaining(Ljava/util/function/Consumer;)V".equals(signature)) {
                lowerIteratorForEachRemainingCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.getFirst()
                );
                return true;
            }
        }
        if ("java/util/ListIterator".equals(methodRef.owner())) {
            if ("hasNext()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_iterator_has_next", List.of(receiver))));
                return true;
            }
            if ("next()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_iterator_next", List.of(receiver));
                return true;
            }
            if ("hasPrevious()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_iterator_has_previous", List.of(receiver))));
                return true;
            }
            if ("previous()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_list_iterator_previous", List.of(receiver));
                return true;
            }
            if ("nextIndex()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_iterator_next_index", List.of(receiver))));
                return true;
            }
            if ("previousIndex()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_list_iterator_previous_index", List.of(receiver))));
                return true;
            }
            if ("remove()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_iterator_remove", List.of(receiver)));
                return true;
            }
            if ("set(Ljava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_iterator_set", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("add(Ljava/lang/Object;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_list_iterator_add", List.of(receiver, arguments.getFirst())));
                return true;
            }
        }
        if (isJdkMapOwner(methodRef.owner())) {
            if ("get(Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_get", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_get_or_default", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)
                || ("java/util/EnumMap".equals(methodRef.owner())
                && "put(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature))) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_put", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_put_if_absent", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("replace(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_replace", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("replace(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(
                    instructions,
                    stack,
                    localDeclarations,
                    "javan_map_replace_entry",
                    List.of(receiver, arguments.get(0), arguments.get(1), arguments.get(2))
                );
                return true;
            }
            if ("clear()V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_map_clear", List.of(receiver)));
                return true;
            }
            if ("putAll(Ljava/util/Map;)V".equals(signature)) {
                instructions.add(IrInstruction.callStaticVoid("javan_map_put_all", List.of(receiver, arguments.getFirst())));
                return true;
            }
            if ("forEach(Ljava/util/function/BiConsumer;)V".equals(signature)) {
                lowerMapForEachCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.getFirst()
                );
                return true;
            }
            if ("computeIfPresent(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;".equals(signature)) {
                final String resultLocal = lowerMapComputeIfPresentCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.get(0),
                    arguments.get(1)
                );
                stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
                return true;
            }
            if ("compute(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;".equals(signature)) {
                final String resultLocal = lowerMapComputeCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.get(0),
                    arguments.get(1)
                );
                stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
                return true;
            }
            if ("merge(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;".equals(signature)) {
                final String resultLocal = lowerMapMergeCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    localDeclarations,
                    receiver,
                    arguments.get(0),
                    arguments.get(1),
                    arguments.get(2)
                );
                stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
                return true;
            }
            if ("computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(signature)) {
                final String resultLocal = lowerMapComputeIfAbsentCall(
                    classes,
                    classFile,
                    method,
                    instruction,
                    instructions,
                    dispatches,
                    materializedLambdaMethods,
                    instantiatedTypes,
                    materializedFunction,
                    localDeclarations,
                    receiver,
                    arguments.get(0),
                    arguments.get(1)
                );
                stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
                return true;
            }
            if ("remove(Ljava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_remove", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("remove(Ljava/lang/Object;Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_map_remove_entry", List.of(receiver, arguments.get(0), arguments.get(1)));
                return true;
            }
            if ("containsKey(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_map_contains_key", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("containsValue(Ljava/lang/Object;)Z".equals(signature)) {
                pushIntCall(instructions, stack, localDeclarations, "javan_map_contains_value", List.of(receiver, arguments.getFirst()));
                return true;
            }
            if ("size()I".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_map_size", List.of(receiver))));
                return true;
            }
            if ("isEmpty()Z".equals(signature)) {
                stack.add(StackValue.intExpression(IrExpression.intCall("javan_map_is_empty", List.of(receiver))));
                return true;
            }
            if ("keySet()Ljava/util/Set;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_key_set", List.of(receiver))));
                return true;
            }
            if ("entrySet()Ljava/util/Set;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_entry_set", List.of(receiver))));
                return true;
            }
            if ("values()Ljava/util/Collection;".equals(signature)) {
                stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_map_values", List.of(receiver))));
                return true;
            }
        }
        if ("java/util/Map$Entry".equals(methodRef.owner())) {
            if ("getKey()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_entry_get_key", List.of(receiver));
                return true;
            }
            if ("getValue()Ljava/lang/Object;".equals(signature)) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_map_entry_get_value", List.of(receiver));
                return true;
            }
        }
        throw collectionLoweringRegistryMismatch(classFile, method, methodRef);
    }

    private static void lowerIteratorForEachRemainingCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression iterator,
        final IrExpression consumer
    ) {
        final String iteratorLocal = newObjectLocal(localDeclarations);
        final String loopLabel = "label_iterator_for_each_remaining_loop_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_iterator_for_each_remaining_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_iterator_for_each_remaining_end_" + instruction.offset() + "_" + localDeclarations.size();
        final String hasNextLocal = newIntLocal(localDeclarations);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(iteratorLocal, iterator));
        instructions.add(IrInstruction.label(loopLabel));
        instructions.add(IrInstruction.assignInt(
            hasNextLocal,
            IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(hasNextLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        lowerConsumerAcceptCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            consumer,
            IrExpression.objectLocal(valueLocal)
        );
        instructions.add(IrInstruction.jump(loopLabel));
        instructions.add(IrInstruction.label(endLabel));
    }

    private static String lowerCollectionRemoveIfCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression collection,
        final IrExpression predicate
    ) {
        final String iteratorLocal = newObjectLocal(localDeclarations);
        final String changedLocal = newIntLocal(localDeclarations);
        final String loopLabel = "label_collection_remove_if_loop_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_collection_remove_if_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String removeLabel = "label_collection_remove_if_remove_" + instruction.offset() + "_" + localDeclarations.size();
        final String continueLabel = "label_collection_remove_if_continue_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_collection_remove_if_end_" + instruction.offset() + "_" + localDeclarations.size();
        final String hasNextLocal = newIntLocal(localDeclarations);
        final String valueLocal = newObjectLocal(localDeclarations);
        final String predicateLocal = newIntLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            iteratorLocal,
            IrExpression.objectCall("javan_list_iterator", List.of(collection))
        ));
        instructions.add(IrInstruction.assignInt(changedLocal, IrExpression.intLiteral(0)));
        instructions.add(IrInstruction.label(loopLabel));
        instructions.add(IrInstruction.assignInt(
            hasNextLocal,
            IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(hasNextLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        lowerPredicateTestCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            predicate,
            IrExpression.objectLocal(valueLocal),
            predicateLocal
        );
        instructions.add(IrInstruction.branchIf(
            removeLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(predicateLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(continueLabel));
        instructions.add(IrInstruction.label(removeLabel));
        instructions.add(IrInstruction.callStaticVoid("javan_list_iterator_remove", List.of(IrExpression.objectLocal(iteratorLocal))));
        instructions.add(IrInstruction.assignInt(changedLocal, IrExpression.intLiteral(1)));
        instructions.add(IrInstruction.label(continueLabel));
        instructions.add(IrInstruction.jump(loopLabel));
        instructions.add(IrInstruction.label(endLabel));
        return changedLocal;
    }

    private static void lowerMapForEachCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression map,
        final IrExpression biConsumer
    ) {
        final String iteratorLocal = newObjectLocal(localDeclarations);
        final String loopLabel = "label_map_for_each_loop_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_map_for_each_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_map_for_each_end_" + instruction.offset() + "_" + localDeclarations.size();
        final String hasNextLocal = newIntLocal(localDeclarations);
        final String entryLocal = newObjectLocal(localDeclarations);
        final String keyLocal = newObjectLocal(localDeclarations);
        final String valueLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            iteratorLocal,
            IrExpression.objectCall("javan_list_iterator", List.of(IrExpression.objectCall("javan_map_entry_set", List.of(map))))
        ));
        instructions.add(IrInstruction.label(loopLabel));
        instructions.add(IrInstruction.assignInt(
            hasNextLocal,
            IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(hasNextLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(
            entryLocal,
            IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        instructions.add(IrInstruction.assignObject(
            keyLocal,
            IrExpression.objectCall("javan_map_entry_get_key", List.of(IrExpression.objectLocal(entryLocal)))
        ));
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_map_entry_get_value", List.of(IrExpression.objectLocal(entryLocal)))
        ));
        lowerBiConsumerAcceptCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            biConsumer,
            IrExpression.objectLocal(keyLocal),
            IrExpression.objectLocal(valueLocal)
        );
        instructions.add(IrInstruction.jump(loopLabel));
        instructions.add(IrInstruction.label(endLabel));
    }

    private static String lowerMapComputeIfPresentCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression map,
        final IrExpression key,
        final IrExpression biFunction
    ) {
        final String existingLocal = newObjectLocal(localDeclarations);
        final String resultLocal = newObjectLocal(localDeclarations);
        final String presentLabel = "label_map_compute_if_present_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String storeLabel = "label_map_compute_if_present_store_" + instruction.offset() + "_" + localDeclarations.size();
        final String removeLabel = "label_map_compute_if_present_remove_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_map_compute_if_present_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(
            existingLocal,
            IrExpression.objectCall("javan_map_get", List.of(map, key))
        ));
        instructions.add(IrInstruction.branchIf(
            presentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(existingLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectNull()));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(presentLabel));
        final String computedLocal = newObjectLocal(localDeclarations);
        final String removedLocal = newObjectLocal(localDeclarations);
        lowerBiFunctionApplyCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            biFunction,
            key,
            IrExpression.objectLocal(existingLocal),
            computedLocal
        );
        instructions.add(IrInstruction.branchIf(
            storeLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(computedLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.jump(removeLabel));
        instructions.add(IrInstruction.label(storeLabel));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_map_put",
            List.of(map, key, IrExpression.objectLocal(computedLocal))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(removeLabel));
        instructions.add(IrInstruction.assignObject(
            removedLocal,
            IrExpression.objectCall("javan_map_remove", List.of(map, key))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectNull()));
        instructions.add(IrInstruction.label(endLabel));
        return resultLocal;
    }

    private static String lowerMapComputeCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression map,
        final IrExpression key,
        final IrExpression biFunction
    ) {
        final String existingLocal = newObjectLocal(localDeclarations);
        final String hasKeyLocal = newIntLocal(localDeclarations);
        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            existingLocal,
            IrExpression.objectCall("javan_map_get", List.of(map, key))
        ));
        instructions.add(IrInstruction.assignInt(
            hasKeyLocal,
            IrExpression.intCall("javan_map_contains_key", List.of(map, key))
        ));
        final String computedLocal = newObjectLocal(localDeclarations);
        lowerBiFunctionApplyCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            biFunction,
            key,
            IrExpression.objectLocal(existingLocal),
            computedLocal
        );
        final String storeLabel = "label_map_compute_store_" + instruction.offset() + "_" + localDeclarations.size();
        final String removeLabel = "label_map_compute_remove_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_map_compute_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.branchIf(
            storeLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(computedLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectNull()));
        instructions.add(IrInstruction.branchIf(
            removeLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(existingLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.branchIf(
            removeLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(hasKeyLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(storeLabel));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_map_put",
            List.of(map, key, IrExpression.objectLocal(computedLocal))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(removeLabel));
        final String removedLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            removedLocal,
            IrExpression.objectCall("javan_map_remove", List.of(map, key))
        ));
        instructions.add(IrInstruction.label(endLabel));
        return resultLocal;
    }

    private static String lowerMapComputeIfAbsentCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final boolean materializedFunction,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression map,
        final IrExpression key,
        final IrExpression function
    ) {
        final String existingLocal = newObjectLocal(localDeclarations);
        final String resultLocal = newObjectLocal(localDeclarations);
        final String presentLabel = "label_map_compute_if_absent_present_" + instruction.offset() + "_" + localDeclarations.size();
        final String storeLabel = "label_map_compute_if_absent_store_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_map_compute_if_absent_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(
            existingLocal,
            IrExpression.objectCall("javan_map_get", List.of(map, key))
        ));
        instructions.add(IrInstruction.branchIf(
            presentLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(existingLocal), IrExpression.objectNull())
        ));
        final String computedLocal = newObjectLocal(localDeclarations);
        lowerFunctionApplyCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            materializedFunction,
            function,
            key,
            computedLocal
        );
        instructions.add(IrInstruction.branchIf(
            storeLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(computedLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(storeLabel));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_map_put",
            List.of(map, key, IrExpression.objectLocal(computedLocal))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(presentLabel));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(existingLocal)));
        instructions.add(IrInstruction.label(endLabel));
        return resultLocal;
    }

    private static String lowerMapMergeCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression map,
        final IrExpression key,
        final IrExpression value,
        final IrExpression biFunction
    ) {
        final String valueLocal = newObjectLocal(localDeclarations);
        final String existingLocal = newObjectLocal(localDeclarations);
        final String resultLocal = newObjectLocal(localDeclarations);
        final String existingLabel = "label_map_merge_existing_" + instruction.offset() + "_" + localDeclarations.size();
        final String storeComputedLabel = "label_map_merge_store_computed_" + instruction.offset() + "_" + localDeclarations.size();
        final String removeLabel = "label_map_merge_remove_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_map_merge_end_" + instruction.offset() + "_" + localDeclarations.size();
        instructions.add(IrInstruction.assignObject(valueLocal, value));
        instructions.add(IrInstruction.callStaticVoid("javan_objects_require_non_null", List.of(IrExpression.objectLocal(valueLocal))));
        instructions.add(IrInstruction.assignObject(
            existingLocal,
            IrExpression.objectCall("javan_map_get", List.of(map, key))
        ));
        instructions.add(IrInstruction.branchIf(
            existingLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(existingLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_map_put",
            List.of(map, key, IrExpression.objectLocal(valueLocal))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(valueLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(existingLabel));
        final String computedLocal = newObjectLocal(localDeclarations);
        final String removedLocal = newObjectLocal(localDeclarations);
        lowerBiFunctionApplyCall(
            classes,
            classFile,
            method,
            instruction,
            instructions,
            dispatches,
            materializedLambdaMethods,
            instantiatedTypes,
            biFunction,
            IrExpression.objectLocal(existingLocal),
            IrExpression.objectLocal(valueLocal),
            computedLocal
        );
        instructions.add(IrInstruction.branchIf(
            storeComputedLabel,
            IrExpression.objectComparison("!=", IrExpression.objectLocal(computedLocal), IrExpression.objectNull())
        ));
        instructions.add(IrInstruction.jump(removeLabel));
        instructions.add(IrInstruction.label(storeComputedLabel));
        instructions.add(IrInstruction.callStaticVoid(
            "javan_map_put",
            List.of(map, key, IrExpression.objectLocal(computedLocal))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectLocal(computedLocal)));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(removeLabel));
        instructions.add(IrInstruction.assignObject(
            removedLocal,
            IrExpression.objectCall("javan_map_remove", List.of(map, key))
        ));
        instructions.add(IrInstruction.assignObject(resultLocal, IrExpression.objectNull()));
        instructions.add(IrInstruction.label(endLabel));
        return resultLocal;
    }

    static void lowerConsumerAcceptCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final IrExpression consumer,
        final IrExpression argument
    ) {
        final MethodRef consumerAccept = new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V");
        final List<EntryPoint> targets = interfaceTargets(classes, consumerAccept, instantiatedTypes);
        if (targets.size() > 1) {
            final String dispatchSymbol = dispatchSymbol(consumerAccept);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(consumerAccept.descriptor()), targets));
            instructions.add(IrInstruction.callStaticVoid(dispatchSymbol, List.of(consumer, argument)));
            return;
        }
        if (!targets.isEmpty()) {
            instructions.add(IrInstruction.callStaticVoid(
                resolvedDispatchSymbol(classes, consumerAccept, targets.getFirst()),
                List.of(consumer, argument)
            ));
            return;
        }
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, consumerAccept, instantiatedTypes);
        if (defaultTarget.isPresent()) {
            instructions.add(IrInstruction.callStaticVoid(symbol(defaultTarget.orElseThrow()), List.of(consumer, argument)));
            return;
        }
        if (materializedLambdaMethods.get(materializedLambdaMethodKey(consumerAccept))
            == MaterializedLambdaDispatchKind.VOID) {
            instructions.add(IrInstruction.callStaticVoid(MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL, List.of(consumer, argument)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerBiConsumerAcceptCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final IrExpression biConsumer,
        final IrExpression firstArgument,
        final IrExpression secondArgument
    ) {
        final MethodRef biConsumerAccept = new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        final List<EntryPoint> targets = interfaceTargets(classes, biConsumerAccept, instantiatedTypes);
        if (targets.size() > 1) {
            final String dispatchSymbol = dispatchSymbol(biConsumerAccept);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(biConsumerAccept.descriptor()), targets));
            instructions.add(IrInstruction.callStaticVoid(dispatchSymbol, List.of(biConsumer, firstArgument, secondArgument)));
            return;
        }
        if (!targets.isEmpty()) {
            instructions.add(IrInstruction.callStaticVoid(
                resolvedDispatchSymbol(classes, biConsumerAccept, targets.getFirst()),
                List.of(biConsumer, firstArgument, secondArgument)
            ));
            return;
        }
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, biConsumerAccept, instantiatedTypes);
        if (defaultTarget.isPresent()) {
            instructions.add(IrInstruction.callStaticVoid(symbol(defaultTarget.orElseThrow()), List.of(biConsumer, firstArgument, secondArgument)));
            return;
        }
        if (materializedLambdaMethods.get(materializedLambdaMethodKey(biConsumerAccept))
            == MaterializedLambdaDispatchKind.VOID) {
            instructions.add(IrInstruction.callStaticVoid(MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL, List.of(biConsumer, firstArgument, secondArgument)));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerBiFunctionApplyCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final IrExpression biFunction,
        final IrExpression firstArgument,
        final IrExpression secondArgument,
        final String resultLocal
    ) {
        final MethodRef biFunctionApply = new MethodRef("java/util/function/BiFunction", "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        final List<EntryPoint> targets = interfaceTargets(classes, biFunctionApply, instantiatedTypes);
        if (targets.size() > 1) {
            final String dispatchSymbol = dispatchSymbol(biFunctionApply);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(biFunctionApply.descriptor()), targets));
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(dispatchSymbol, List.of(biFunction, firstArgument, secondArgument))
            ));
            return;
        }
        if (!targets.isEmpty()) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(
                    resolvedDispatchSymbol(classes, biFunctionApply, targets.getFirst()),
                    List.of(biFunction, firstArgument, secondArgument)
                )
            ));
            return;
        }
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, biFunctionApply, instantiatedTypes);
        if (defaultTarget.isPresent()) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(symbol(defaultTarget.orElseThrow()), List.of(biFunction, firstArgument, secondArgument))
            ));
            return;
        }
        if (materializedLambdaMethods.get(materializedLambdaMethodKey(biFunctionApply))
            == MaterializedLambdaDispatchKind.OBJECT) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL, List.of(biFunction, firstArgument, secondArgument))
            ));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    static void lowerFunctionApplyCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final boolean materializedFunction,
        final IrExpression function,
        final IrExpression argument,
        final String resultLocal
    ) {
        final MethodRef functionApply = new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        if (materializedFunction) {
            if (materializedLambdaMethods.get(materializedLambdaMethodKey(functionApply))
                != MaterializedLambdaDispatchKind.OBJECT) {
                throw unsupported(classFile, method, instruction);
            }
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL, List.of(function, argument))
            ));
            return;
        }
        final List<EntryPoint> targets = interfaceTargets(classes, functionApply, instantiatedTypes);
        if (targets.size() > 1) {
            final String dispatchSymbol = dispatchSymbol(functionApply);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(functionApply.descriptor()), targets));
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(dispatchSymbol, List.of(function, argument))
            ));
            return;
        }
        if (!targets.isEmpty()) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(
                    resolvedDispatchSymbol(classes, functionApply, targets.getFirst()),
                    List.of(function, argument)
                )
            ));
            return;
        }
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, functionApply, instantiatedTypes);
        if (defaultTarget.isPresent()) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(symbol(defaultTarget.orElseThrow()), List.of(function, argument))
            ));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    static void lowerSupplierGetCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final IrExpression supplier,
        final String resultLocal
    ) {
        final MethodRef supplierGet = new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;");
        final List<EntryPoint> targets = interfaceTargets(classes, supplierGet, instantiatedTypes);
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, supplierGet, instantiatedTypes);
        final boolean hasMaterializedTarget =
            materializedLambdaMethods.get(materializedLambdaMethodKey(supplierGet))
                == MaterializedLambdaDispatchKind.SUPPLIER;
        if (hasMaterializedTarget && (!targets.isEmpty() || defaultTarget.isPresent())) {
            final String materializedLabel =
                "label_supplier_get_materialized_" + instruction.offset() + "_" + resultLocal;
            final String endLabel = "label_supplier_get_end_" + instruction.offset() + "_" + resultLocal;
            instructions.add(IrInstruction.branchIf(
                materializedLabel,
                IrExpression.intCall(MATERIALIZED_LAMBDA_IS_INSTANCE_SYMBOL, List.of(supplier))
            ));
            if (!targets.isEmpty()) {
                final String dispatchSymbol = dispatchSymbol(supplierGet);
                dispatches.putIfAbsent(
                    dispatchSymbol,
                    dispatch(classes, dispatchSymbol, MethodDescriptor.parse(supplierGet.descriptor()), targets)
                );
                instructions.add(IrInstruction.assignObject(
                    resultLocal,
                    IrExpression.objectCall(dispatchSymbol, List.of(supplier))
                ));
            } else {
                instructions.add(IrInstruction.assignObject(
                    resultLocal,
                    IrExpression.objectCall(symbol(defaultTarget.orElseThrow()), List.of(supplier))
                ));
            }
            instructions.add(IrInstruction.jump(endLabel));
            instructions.add(IrInstruction.label(materializedLabel));
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL, List.of(supplier))
            ));
            instructions.add(IrInstruction.label(endLabel));
            return;
        }
        if (targets.size() > 1) {
            final String dispatchSymbol = dispatchSymbol(supplierGet);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(supplierGet.descriptor()), targets));
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(dispatchSymbol, List.of(supplier))
            ));
            return;
        }
        if (!targets.isEmpty()) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(resolvedDispatchSymbol(classes, supplierGet, targets.getFirst()), List.of(supplier))
            ));
            return;
        }
        if (defaultTarget.isPresent()) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(symbol(defaultTarget.orElseThrow()), List.of(supplier))
            ));
            return;
        }
        if (hasMaterializedTarget) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(MATERIALIZED_LAMBDA_SUPPLIER_APPLY_SYMBOL, List.of(supplier))
            ));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    static void lowerPredicateTestCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final Map<String, IrDispatch> dispatches,
        final Map<String, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final IrExpression predicate,
        final IrExpression argument,
        final String resultLocal
    ) {
        final MethodRef predicateTest = new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z");
        final List<EntryPoint> targets = interfaceTargets(classes, predicateTest, instantiatedTypes);
        if (targets.size() > 1) {
            final String dispatchSymbol = dispatchSymbol(predicateTest);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(classes, dispatchSymbol, MethodDescriptor.parse(predicateTest.descriptor()), targets));
            instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall(dispatchSymbol, List.of(predicate, argument))));
            return;
        }
        if (!targets.isEmpty()) {
            instructions.add(IrInstruction.assignInt(
                resultLocal,
                IrExpression.intCall(resolvedDispatchSymbol(classes, predicateTest, targets.getFirst()), List.of(predicate, argument))
            ));
            return;
        }
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, predicateTest, instantiatedTypes);
        if (defaultTarget.isPresent()) {
            instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall(symbol(defaultTarget.orElseThrow()), List.of(predicate, argument))));
            return;
        }
        if (materializedLambdaMethods.get(materializedLambdaMethodKey(predicateTest))
            == MaterializedLambdaDispatchKind.BOOLEAN) {
            instructions.add(IrInstruction.assignInt(resultLocal, IrExpression.intCall(MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL, List.of(predicate, argument))));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static boolean isInlineCollectionRemoveIfLambdaCall(final MethodRef methodRef, final List<StackValue> stack) {
        if (!hasTopStackKind(stack, StackKind.LAMBDA_PREDICATE)) {
            return false;
        }
        if (!"removeIf".equals(methodRef.name()) || !"(Ljava/util/function/Predicate;)Z".equals(methodRef.descriptor())) {
            return false;
        }
        final String owner = methodRef.owner();
        return "java/util/Collection".equals(owner)
            || "java/util/List".equals(owner)
            || "java/util/ArrayList".equals(owner)
            || "java/util/Set".equals(owner)
            || "java/util/HashSet".equals(owner)
            || "java/util/LinkedHashSet".equals(owner);
    }

    static DiagnosticException collectionLoweringRegistryMismatch(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef
    ) {
        return new DiagnosticException(Diagnostic.error(
            "JAVAN047",
            "declared supported collection call has no lowering",
            classFile.name(),
            method.name() + method.descriptor(),
            methodRef.display(),
            "The JDK support registry and bytecode lowering are out of sync.",
            "Add the missing lowering or remove the call from the support registry."
        ));
    }

    static boolean isJdkCollectionOwner(final String owner) {
        if (isJdkListOrCollection(owner)) {
            return true;
        }
        if ("java/util/AbstractList".equals(owner)) {
            return true;
        }
        if (isJdkSetOwner(owner)) {
            return true;
        }
        if ("java/util/Iterator".equals(owner)) {
            return true;
        }
        if ("java/util/ListIterator".equals(owner)) {
            return true;
        }
        if ("java/lang/Iterable".equals(owner)) {
            return true;
        }
        if ("java/util/Map$Entry".equals(owner)) {
            return true;
        }
        return isJdkMapOwner(owner);
    }

    static boolean isJdkListClass(final String owner) {
        if ("java/util/List".equals(owner)) {
            return true;
        }
        return "java/util/ArrayList".equals(owner);
    }

    static boolean isJdkListOrCollection(final String owner) {
        if (isJdkListClass(owner)) {
            return true;
        }
        return "java/util/Collection".equals(owner);
    }

    static boolean isJdkSetClass(final String owner) {
        return "java/util/HashSet".equals(owner) || "java/util/LinkedHashSet".equals(owner);
    }

    static boolean isJdkSetOwner(final String owner) {
        if ("java/util/Set".equals(owner)) {
            return true;
        }
        return isJdkSetClass(owner);
    }

    static boolean isJdkSetEqualsOwner(final String owner) {
        return "java/util/Set".equals(owner)
            || "java/util/AbstractSet".equals(owner)
            || "java/util/HashSet".equals(owner)
            || "java/util/LinkedHashSet".equals(owner);
    }

    static boolean isJdkSetEqualsInvocation(final Instruction instruction, final MethodRef methodRef) {
        if ("java/util/Set".equals(methodRef.owner())) {
            return instruction.opcode() == 185 && "invokeinterface".equals(instruction.mnemonic());
        }
        return instruction.opcode() == 182 && "invokevirtual".equals(instruction.mnemonic());
    }

    static boolean isJdkMapOwner(final String owner) {
        if ("java/util/Map".equals(owner)) {
            return true;
        }
        return isJdkMapClass(owner);
    }

    static boolean isJdkMapClass(final String owner) {
        if ("java/util/HashMap".equals(owner)) {
            return true;
        }
        if ("java/util/LinkedHashMap".equals(owner)) {
            return true;
        }
        if ("java/util/TreeMap".equals(owner)) {
            return true;
        }
        if ("java/util/EnumMap".equals(owner)) {
            return true;
        }
        return "java/util/concurrent/ConcurrentHashMap".equals(owner);
    }

    static boolean lowerArraysIntrinsic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines
    ) {
        if ("equals".equals(methodRef.name()) && "([B[B)Z".equals(methodRef.descriptor())) {
            final IrExpression right = popObject(classFile, method, stack);
            final IrExpression left = popObject(classFile, method, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_arrays_equals_byte",
                List.of(left, right)
            )));
            return true;
        }
        if ("fill".equals(methodRef.name()) && "([BB)V".equals(methodRef.descriptor())) {
            final IrExpression value = popInt(classFile, method, stack);
            final IrExpression array = popObject(classFile, method, stack);
            lowerByteArrayFill(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                "javan_arrays_fill_byte",
                List.of(array, value),
                false
            );
            return true;
        }
        if ("fill".equals(methodRef.name()) && "([BIIB)V".equals(methodRef.descriptor())) {
            final IrExpression value = popInt(classFile, method, stack);
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression array = popObject(classFile, method, stack);
            lowerByteArrayFill(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                "javan_arrays_fill_range_byte",
                List.of(array, begin, end, value),
                true
            );
            return true;
        }
        if ("copyOfRange".equals(methodRef.name()) && "([BII)[B".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression source = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_arrays_copy_of_range_byte",
                List.of(source, begin, end)
            )));
            return true;
        }
        if ("copyOfRange".equals(methodRef.name()) && "([Ljava/lang/Object;II)[Ljava/lang/Object;".equals(methodRef.descriptor())) {
            final IrExpression end = popInt(classFile, method, stack);
            final IrExpression begin = popInt(classFile, method, stack);
            final IrExpression source = popObject(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_arrays_copy_of_range_object",
                List.of(source, begin, end)
            )));
            return true;
        }
        if (!"copyOf".equals(methodRef.name())) {
            return false;
        }
        final Optional<String> symbol = arraysCopyOfSymbol(methodRef.descriptor());
        if (symbol.isEmpty()) {
            return false;
        }
        final IrExpression newLength = popInt(classFile, method, stack);
        final IrExpression source = popObject(classFile, method, stack);
        stack.add(StackValue.objectExpression(IrExpression.objectCall(symbol.orElseThrow(), List.of(source, newLength))));
        return true;
    }

    static void lowerByteArrayFill(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final String symbol,
        final List<IrExpression> arguments,
        final boolean ranged
    ) {
        final int resultLocalIndex = localDeclarations.size();
        final String resultLocalName = "int" + resultLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + resultLocalIndex, new IrLocal(IrType.INT, resultLocalName));
        instructions.add(IrInstruction.assignInt(resultLocalName, IrExpression.intCall(symbol, arguments)));

        final IrExpression result = IrExpression.intLocal(resultLocalName);
        final String labelSuffix = instruction.offset() + "_" + resultLocalIndex;
        final String successLabel = "label_arrays_fill_success_" + labelSuffix;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison("==", result, IrExpression.intLiteral(ARRAYS_FILL_STATUS_SUCCESS))
        ));
        if (!ranged) {
            routePendingPlatformException(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                pendingExceptionHandlerStacks,
                sourceLines,
                "java/lang/NullPointerException",
                IrExpression.stringLiteral("array")
            );
            instructions.add(IrInstruction.label(successLabel));
            return;
        }

        final int messageLocalIndex = localDeclarations.size();
        final String messageLocalName = "object" + messageLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + messageLocalIndex, new IrLocal(IrType.OBJECT, messageLocalName));
        final IrExpression message = IrExpression.objectLocal(messageLocalName);
        final String nonNullLabel = "label_arrays_fill_non_null_" + labelSuffix;
        instructions.add(IrInstruction.branchIf(
            nonNullLabel,
            IrExpression.intComparison("!=", result, IrExpression.intLiteral(ARRAYS_FILL_STATUS_NULL))
        ));
        instructions.add(IrInstruction.assignObject(messageLocalName, IrExpression.stringLiteral("array")));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/NullPointerException",
            message
        );
        instructions.add(IrInstruction.label(nonNullLabel));

        final String validOrderLabel = "label_arrays_fill_valid_order_" + labelSuffix;
        instructions.add(IrInstruction.branchIf(
            validOrderLabel,
            IrExpression.intComparison("!=", result, IrExpression.intLiteral(ARRAYS_FILL_STATUS_INVERTED_RANGE))
        ));
        instructions.add(IrInstruction.assignObject(messageLocalName, IrExpression.stringLiteral("fromIndex > toIndex")));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/IllegalArgumentException",
            message
        );
        instructions.add(IrInstruction.label(validOrderLabel));
        instructions.add(IrInstruction.assignObject(messageLocalName, IrExpression.stringLiteral("array index out of bounds")));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/ArrayIndexOutOfBoundsException",
            message
        );
        instructions.add(IrInstruction.label(successLabel));
    }

    static Optional<String> arraysCopyOfSymbol(final String descriptor) {
        if ("([II)[I".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_int");
        }
        if ("([ZI)[Z".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_boolean");
        }
        if ("([JI)[J".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_long");
        }
        if ("([BI)[B".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_byte");
        }
        if ("([SI)[S".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_short");
        }
        if ("([CI)[C".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_char");
        }
        if ("([FI)[F".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_float");
        }
        if ("([DI)[D".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_double");
        }
        if ("([Ljava/lang/Object;I)[Ljava/lang/Object;".equals(descriptor)) {
            return Optional.of("javan_arrays_copy_of_object");
        }
        return Optional.empty();
    }

    static void lowerCollectionRemoveIfLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_PREDICATE, "predicate lambda");
        final IrExpression receiver = popObject(classFile, method, instruction, stack);
        final String changedLocal = newIntLocal(localDeclarations);
        final String iteratorLocal = newObjectLocal(localDeclarations);
        final String loopLabel = "label_collection_remove_if_lambda_loop_" + instruction.offset() + "_" + localDeclarations.size();
        final String bodyLabel = "label_collection_remove_if_lambda_body_" + instruction.offset() + "_" + localDeclarations.size();
        final String removeLabel = "label_collection_remove_if_lambda_remove_" + instruction.offset() + "_" + localDeclarations.size();
        final String continueLabel = "label_collection_remove_if_lambda_continue_" + instruction.offset() + "_" + localDeclarations.size();
        final String endLabel = "label_collection_remove_if_lambda_end_" + instruction.offset() + "_" + localDeclarations.size();
        final String hasNextLocal = newIntLocal(localDeclarations);
        final String valueLocal = newObjectLocal(localDeclarations);
        final String predicateLocal = newIntLocal(localDeclarations);
        instructions.add(IrInstruction.assignInt(changedLocal, IrExpression.intLiteral(0)));
        instructions.add(IrInstruction.assignObject(
            iteratorLocal,
            IrExpression.objectCall("javan_list_iterator", List.of(receiver))
        ));
        instructions.add(IrInstruction.label(loopLabel));
        instructions.add(IrInstruction.assignInt(
            hasNextLocal,
            IrExpression.intCall("javan_iterator_has_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        instructions.add(IrInstruction.branchIf(
            bodyLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(hasNextLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(bodyLabel));
        instructions.add(IrInstruction.assignObject(
            valueLocal,
            IrExpression.objectCall("javan_iterator_next", List.of(IrExpression.objectLocal(iteratorLocal)))
        ));
        instructions.add(IrInstruction.assignInt(
            predicateLocal,
            invokePredicateLambdaExpression(lambda, IrExpression.objectLocal(valueLocal))
        ));
        instructions.add(IrInstruction.branchIf(
            removeLabel,
            IrExpression.intComparison("!=", IrExpression.intLocal(predicateLocal), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.jump(continueLabel));
        instructions.add(IrInstruction.label(removeLabel));
        instructions.add(IrInstruction.callStaticVoid("javan_list_iterator_remove", List.of(IrExpression.objectLocal(iteratorLocal))));
        instructions.add(IrInstruction.assignInt(changedLocal, IrExpression.intLiteral(1)));
        instructions.add(IrInstruction.label(continueLabel));
        instructions.add(IrInstruction.jump(loopLabel));
        instructions.add(IrInstruction.label(endLabel));
        stack.add(StackValue.intExpression(IrExpression.intLocal(changedLocal)));
    }
}

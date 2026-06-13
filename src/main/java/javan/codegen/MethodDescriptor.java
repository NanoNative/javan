package javan.codegen;

import javan.ir.IrParameter;
import javan.ir.IrType;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JVM method descriptor parser for the native backend.
 *
 * @param parameterTypes parameter types
 * @param returnType return type
 */
public record MethodDescriptor(List<IrType> parameterTypes, IrType returnType) {
    /**
     * Parses a descriptor containing only supported primitive/profile types.
     *
     * @param descriptor JVM method descriptor
     * @return parsed descriptor
     */
    public static MethodDescriptor parse(final String descriptor) {
        if (!descriptor.startsWith("(")) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
        final List<IrType> parameters = new ArrayList<>();
        int index = 1;
        while (descriptor.charAt(index) != ')') {
            final char type = descriptor.charAt(index);
            if ("BCISZ".indexOf(type) >= 0) {
                parameters.add(IrType.INT);
                index++;
            } else if (type == 'J') {
                parameters.add(IrType.LONG);
                index++;
            } else if (type == 'F') {
                parameters.add(IrType.FLOAT);
                index++;
            } else if (type == 'D') {
                parameters.add(IrType.DOUBLE);
                index++;
            } else if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
                }
                parameters.add(IrType.OBJECT);
                index = end + 1;
            } else if (type == '[') {
                parameters.add(IrType.OBJECT);
                index = skipArrayDescriptor(descriptor, index, "parameter");
            } else {
                throw new IllegalArgumentException("Unsupported method parameter descriptor: " + descriptor);
            }
        }
        final IrType parsedReturn = returnType(descriptor, index + 1);
        return new MethodDescriptor(List.copyOf(parameters), parsedReturn);
    }

    private static IrType returnType(final String descriptor, final int index) {
        final char type = descriptor.charAt(index);
        if (type == 'V') {
            return IrType.VOID;
        }
        if ("BCISZ".indexOf(type) >= 0) {
            return IrType.INT;
        }
        if (type == 'J') {
            return IrType.LONG;
        }
        if (type == 'F') {
            return IrType.FLOAT;
        }
        if (type == 'D') {
            return IrType.DOUBLE;
        }
        if (type == 'L' && descriptor.indexOf(';', index) > index) {
            return IrType.OBJECT;
        }
        if (type == '[') {
            skipArrayDescriptor(descriptor, index, "return");
            return IrType.OBJECT;
        }
        throw new IllegalArgumentException("Unsupported method return descriptor: " + descriptor);
    }

    private static int skipArrayDescriptor(final String descriptor, final int start, final String position) {
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index >= descriptor.length()) {
            throw new IllegalArgumentException("Unsupported method " + position + " descriptor: " + descriptor);
        }
        final char element = descriptor.charAt(index);
        if ("BCDFIJSZ".indexOf(element) >= 0) {
            return index + 1;
        }
        if (element == 'L') {
            final int end = descriptor.indexOf(';', index);
            if (end < 0) {
                throw new IllegalArgumentException("Unsupported method " + position + " descriptor: " + descriptor);
            }
            return end + 1;
        }
        throw new IllegalArgumentException("Unsupported method " + position + " descriptor: " + descriptor);
    }

    /**
     * Creates deterministic parameter names.
     *
     * @return IR parameters
     */
    public List<IrParameter> parameters() {
        final List<IrParameter> result = new ArrayList<>();
        for (int index = 0; index < parameterTypes.size(); index++) {
            result.add(new IrParameter(parameterTypes.get(index), "arg" + index));
        }
        return List.copyOf(result);
    }
}

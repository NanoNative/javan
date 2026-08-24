package javan.classfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Object-local and operand-stack types declared by one exact {@code StackMapTable} frame.
 *
 * @param offset bytecode offset of the frame
 * @param objectLocals JVM local slot to object verification type
 * @param stackTypes verification stack entries, with object types present and primitive values empty
 */
public record StackMapFrame(
    int offset,
    Map<Integer, String> objectLocals,
    List<Optional<String>> stackTypes
) {
    public StackMapFrame {
        objectLocals = Map.copyOf(objectLocals);
        stackTypes = List.copyOf(stackTypes);
    }

    /** Creates a frame without an operand stack. */
    public StackMapFrame(final int offset, final Map<Integer, String> objectLocals) {
        this(offset, objectLocals, List.of());
    }

    /**
     * Returns the object verification type declared for one JVM local slot.
     *
     * @param slot JVM local slot
     * @return internal class name or array descriptor when the slot is an object
     */
    public Optional<String> objectLocalType(final int slot) {
        return Optional.ofNullable(objectLocals.get(Integer.valueOf(slot)));
    }

    /**
     * Returns the object verification type when this frame has exactly one stack entry.
     *
     * @return the sole stack object's internal class name or array descriptor
     */
    public Optional<String> singleStackObjectType() {
        return stackTypes.size() == 1 ? stackTypes.getFirst() : Optional.empty();
    }
}

package javan.build;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit native inputs supplied to the host linker.
 *
 * @param sources C or Objective-C source files
 * @param objects prebuilt object files
 * @param librarySearchPaths library search directories
 * @param libraries library names
 * @param frameworks framework names
 */
public record NativeLinkInputs(
    List<Path> sources,
    List<Path> objects,
    List<Path> librarySearchPaths,
    List<String> libraries,
    List<String> frameworks
) {
    /**
     * Creates immutable link inputs.
     *
     * @throws NullPointerException when a list argument or list element is null
     * @throws IllegalArgumentException when an input repeats or a library/framework name is invalid
     */
    public NativeLinkInputs {
        sources = copyUnique(sources, "native source");
        objects = copyUnique(objects, "native object");
        librarySearchPaths = copyUnique(librarySearchPaths, "native library search path");
        libraries = copyNames(libraries, "native library");
        frameworks = copyNames(frameworks, "native framework");
    }

    /**
     * Returns an empty native-link input set.
     *
     * @return empty inputs
     */
    public static NativeLinkInputs empty() {
        return new NativeLinkInputs(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Reports whether no link inputs are configured.
     *
     * @return true when every input list is empty
     */
    public boolean emptyInputs() {
        return sources.isEmpty()
            && objects.isEmpty()
            && librarySearchPaths.isEmpty()
            && libraries.isEmpty()
            && frameworks.isEmpty();
    }

    static boolean validLinkName(final String value) {
        if (value.isEmpty() || !identifierPart(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (!identifierPart(character) && character != '+' && character != '.' && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static List<String> copyNames(final List<String> values, final String label) {
        final List<String> names = List.copyOf(Objects.requireNonNull(values, label));
        final Set<String> seen = new HashSet<>();
        for (final String name : names) {
            if (!validLinkName(name)) {
                throw new IllegalArgumentException("Invalid " + label + " name: " + name);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + name);
            }
        }
        return names;
    }

    private static <T> List<T> copyUnique(final List<T> values, final String label) {
        final List<T> inputs = List.copyOf(Objects.requireNonNull(values, label));
        final Set<T> seen = new HashSet<>();
        for (final T input : inputs) {
            if (!seen.add(input)) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + input);
            }
        }
        return inputs;
    }

    private static boolean identifierStart(final char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || value == '_';
    }

    private static boolean identifierPart(final char value) {
        return identifierStart(value) || (value >= '0' && value <= '9');
    }
}

package javan.build;

import javan.analysis.EntryPoint;
import javan.analysis.CMethodSymbols;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Declared native imports and explicit native link inputs.
 *
 * @param imports declared native method imports
 * @param linkInputs explicit native linker inputs
 */
public record NativeInteropConfig(List<ImportBinding> imports, NativeLinkInputs linkInputs) {
    /**
     * Creates an immutable configuration.
     *
     * @throws NullPointerException when an argument is null or an import element is null
     * @throws IllegalArgumentException when imports repeat a Java method, external symbol, or generated wrapper symbol
     */
    public NativeInteropConfig {
        imports = copyImports(imports);
        linkInputs = Objects.requireNonNull(linkInputs, "linkInputs");
    }

    /**
     * Returns an empty native interop configuration.
     *
     * @return empty configuration
     */
    public static NativeInteropConfig empty() {
        return new NativeInteropConfig(List.of(), NativeLinkInputs.empty());
    }

    /**
     * Finds the import binding declared for a Java method.
     *
     * @param entryPoint method identity
     * @return declared binding when present
     * @throws NullPointerException when {@code entryPoint} is null
     */
    public Optional<ImportBinding> importBinding(final EntryPoint entryPoint) {
        Objects.requireNonNull(entryPoint, "entryPoint");
        for (final ImportBinding binding : imports) {
            if (binding.entryPoint().equals(entryPoint)) {
                return Optional.of(binding);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns external symbol names in configuration order.
     *
     * @return immutable external symbol names
     */
    public List<String> externalSymbols() {
        final List<String> result = new ArrayList<>();
        for (int index = 0; index < imports.size(); index++) {
            result.add(imports.get(index).externalSymbol());
        }
        return List.copyOf(result);
    }

    /**
     * Returns declared native method identities in configuration order.
     *
     * @return immutable native method identities
     */
    public List<EntryPoint> nativeEntryPoints() {
        final List<EntryPoint> result = new ArrayList<>();
        for (int index = 0; index < imports.size(); index++) {
            result.add(imports.get(index).entryPoint());
        }
        return List.copyOf(result);
    }

    /**
     * Restricts imports to methods reachable from the selected application or library entry points.
     * Link inputs and import declaration order are preserved.
     *
     * @param reachableMethods reachable method identities
     * @return immutable configuration containing only reachable imports
     * @throws NullPointerException when {@code reachableMethods} or an element is null
     */
    public NativeInteropConfig forReachableMethods(final List<EntryPoint> reachableMethods) {
        final List<EntryPoint> reachable = List.copyOf(Objects.requireNonNull(reachableMethods, "reachableMethods"));
        final List<ImportBinding> reachableImports = new ArrayList<>();
        for (int index = 0; index < imports.size(); index++) {
            final ImportBinding binding = imports.get(index);
            if (containsEntryPoint(reachable, binding.entryPoint())) {
                reachableImports.add(binding);
            }
        }
        return new NativeInteropConfig(reachableImports, linkInputs);
    }

    /**
     * Binds one Java native method to one external C symbol.
     *
     * @param entryPoint Java method identity
     * @param externalSymbol C identifier
     */
    public record ImportBinding(EntryPoint entryPoint, String externalSymbol) {
        /**
         * Creates an immutable import binding.
         *
         * @throws NullPointerException when an argument is null
         * @throws IllegalArgumentException when {@code externalSymbol} is not an available portable C identifier
         */
        public ImportBinding {
            entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
            externalSymbol = Objects.requireNonNull(externalSymbol, "externalSymbol");
            if (!validExternalSymbol(externalSymbol)) {
                throw new IllegalArgumentException("Invalid native import symbol: " + externalSymbol);
            }
            if (reservedExternalSymbol(externalSymbol)) {
                throw new IllegalArgumentException("Reserved native import symbol: " + externalSymbol);
            }
        }
    }

    private static List<ImportBinding> copyImports(final List<ImportBinding> values) {
        final List<ImportBinding> imports = List.copyOf(Objects.requireNonNull(values, "imports"));
        final List<EntryPoint> entryPoints = new ArrayList<>();
        final Set<String> symbols = new HashSet<>();
        final Map<String, EntryPoint> wrappers = new HashMap<>();
        for (final ImportBinding binding : imports) {
            if (containsEntryPoint(entryPoints, binding.entryPoint())) {
                throw new IllegalArgumentException("Duplicate native import declaration: " + binding.entryPoint().display());
            }
            entryPoints.add(binding.entryPoint());
            if (!symbols.add(binding.externalSymbol())) {
                throw new IllegalArgumentException("Duplicate native import symbol: " + binding.externalSymbol());
            }
            final String wrapper = CMethodSymbols.symbol(binding.entryPoint());
            final EntryPoint previous = wrappers.get(wrapper);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "Native import wrapper symbol collision: "
                        + wrapper
                        + " for "
                        + previous.display()
                        + " and "
                        + binding.entryPoint().display()
                );
            }
            wrappers.put(wrapper, binding.entryPoint());
        }
        return imports;
    }

    private static boolean containsEntryPoint(final List<EntryPoint> entries, final EntryPoint target) {
        for (final EntryPoint entry : entries) {
            if (entry.equals(target)) {
                return true;
            }
        }
        return false;
    }

    static boolean validExternalSymbol(final String value) {
        if (value.isEmpty() || !identifierStart(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (!identifierPart(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean identifierStart(final char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || value == '_';
    }

    private static boolean identifierPart(final char value) {
        return identifierStart(value) || (value >= '0' && value <= '9');
    }

    private static boolean reservedExternalSymbol(final String value) {
        return "main".equals(value)
            || value.startsWith("javan_")
            || value.startsWith("Javan")
            || value.startsWith("JAVAN_")
            || value.charAt(0) == '_'
            || cKeyword(value);
    }

    private static boolean cKeyword(final String value) {
        return switch (value) {
            case "alignas", "alignof", "auto", "bool", "break", "case", "char", "const", "constexpr",
                "continue", "default", "do", "double", "else", "enum", "extern", "false", "float", "for",
                "goto", "if", "inline", "int", "long", "nullptr", "register", "restrict", "return", "short",
                "signed", "sizeof", "static", "static_assert", "struct", "switch", "thread_local", "true",
                "typedef", "typeof", "typeof_unqual", "union", "unsigned", "void", "volatile", "while" -> true;
            default -> false;
        };
    }
}

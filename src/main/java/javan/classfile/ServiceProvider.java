package javan.classfile;

/**
 * One closed-world service provider declaration.
 *
 * @param service JVM internal service type
 * @param provider JVM internal provider type
 * @param factoryMethod whether a module declaration may use the provider's static {@code provider()} method
 * @param implementation concrete type created by the declaration
 */
public record ServiceProvider(String service, String provider, boolean factoryMethod, String implementation) {
    /** Creates an unresolved declaration whose provider is also its implementation. */
    public ServiceProvider(final String service, final String provider, final boolean factoryMethod) {
        this(service, provider, factoryMethod, provider);
    }
}

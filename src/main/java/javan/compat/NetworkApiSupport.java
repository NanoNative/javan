package javan.compat;

import javan.classfile.MethodRef;

import java.util.List;

/**
 * Classifies reachable network APIs while native socket/http support is still planned.
 */
public final class NetworkApiSupport {
    private NetworkApiSupport() {
    }

    /**
     * Returns runtime modules implied by a network API method reference.
     *
     * @param methodRef method reference
     * @return ordered runtime modules
     */
    public static List<String> runtimeModules(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        if (isHttpOwner(owner)) {
            return List.of("network", "http");
        }
        if (isSocketOwner(owner)) {
            return List.of("network", "socket");
        }
        if (isCertificateOwner(owner)) {
            return List.of("network", "certificates");
        }
        if (isTlsOwner(owner)) {
            return List.of("network", "tls");
        }
        return List.of();
    }

    /**
     * Checks whether the method belongs to a planned network runtime family.
     *
     * @param methodRef method reference
     * @return true when the method belongs to network/http/socket/tls/certificate APIs
     */
    public static boolean isNetworkCall(final MethodRef methodRef) {
        return !runtimeModules(methodRef).isEmpty();
    }

    private static boolean isHttpOwner(final String owner) {
        if (owner.startsWith("java/net/http/")) {
            return true;
        }
        return owner.startsWith("com/sun/net/httpserver/");
    }

    private static boolean isSocketOwner(final String owner) {
        if ("java/net/Socket".equals(owner)) {
            return true;
        }
        if ("java/net/ServerSocket".equals(owner)) {
            return true;
        }
        if ("java/net/SocketAddress".equals(owner)) {
            return true;
        }
        if ("java/net/InetSocketAddress".equals(owner)) {
            return true;
        }
        if ("java/net/InetAddress".equals(owner)) {
            return true;
        }
        if ("java/net/DatagramSocket".equals(owner)) {
            return true;
        }
        return "java/net/DatagramPacket".equals(owner);
    }

    private static boolean isTlsOwner(final String owner) {
        return owner.startsWith("javax/net/ssl/");
    }

    private static boolean isCertificateOwner(final String owner) {
        return owner.startsWith("java/security/cert/");
    }
}

package org.nanonative.nano.services.http;

public final class HttpServer {
    private HttpServer() {
    }

    public static com.sun.net.httpserver.HttpServer create() throws java.io.IOException {
        return com.sun.net.httpserver.HttpServer.create(null, 0);
    }
}

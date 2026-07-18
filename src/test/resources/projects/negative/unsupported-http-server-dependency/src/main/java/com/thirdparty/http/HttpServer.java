package com.thirdparty.http;

import java.net.InetSocketAddress;

public final class HttpServer {
    private HttpServer() {
    }

    public static com.sun.net.httpserver.HttpServer create() throws java.io.IOException {
        return com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
    }
}

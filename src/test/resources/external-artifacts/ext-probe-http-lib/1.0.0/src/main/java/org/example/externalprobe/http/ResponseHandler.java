package org.example.externalprobe.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public final class ResponseHandler implements HttpHandler {
    @Override
    public void handle(final HttpExchange exchange) throws java.io.IOException {
        final byte[] body = new byte[] {101, 120, 116, 101, 114, 110, 97, 108, 45, 112, 111, 110, 103};
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}

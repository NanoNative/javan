package com.acme;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class Main {
    private static final int PORT = 18437;

    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/health", new HealthHandler(server));
        server.start();
        final Socket socket = new Socket("127.0.0.1", PORT);
        socket.getOutputStream().write(new byte[] {
            71, 69, 84, 32, 47, 104, 101, 97, 108, 116, 104, 32, 72, 84, 84, 80, 47, 49, 46, 49, 13, 10,
            72, 111, 115, 116, 58, 32, 108, 111, 99, 97, 108, 104, 111, 115, 116, 13, 10, 13, 10
        });
        socket.getOutputStream().flush();
        while (socket.getInputStream().read() != -1) {
        }
        socket.close();
        server = null;
    }

    private static final class HealthHandler implements HttpHandler {
        private final HttpServer server;

        private HealthHandler(final HttpServer server) {
            this.server = server;
        }

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final byte[] body = new byte[] {111, 107};
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            server.stop(0);
        }
    }

}

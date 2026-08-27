package com.acme;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.example.externalprobe.http.ResponseHandler;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", new ResponseHandler());
        server.start();
        final int port = server.getAddress().getPort();
        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
            .GET()
            .build();
        final HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
        server.stop(0);
    }
}

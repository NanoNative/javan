package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        org.nanonative.nano.services.http.HttpServer.create();
    }
}

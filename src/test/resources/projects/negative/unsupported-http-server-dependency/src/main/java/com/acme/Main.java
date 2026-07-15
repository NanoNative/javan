package com.acme;

import com.thirdparty.http.HttpServer;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        HttpServer.create();
    }
}

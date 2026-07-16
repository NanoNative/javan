package com.acme;

public final class Task implements Runnable {
    @Override
    public void run() {
        System.out.println("tick");
    }
}

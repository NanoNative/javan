package com.acme;

public final class EnglishGreeter implements Greeter {
    private final String name;

    public EnglishGreeter(final String name) {
        this.name = name;
    }

    public String greet() {
        return name;
    }
}

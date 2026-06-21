package com.acme.showcase;

public final class TextRenderer implements Renderer {
    public TextRenderer() {
    }

    public String render(final String name, final int score) {
        return "metric " + name + " -> " + score;
    }
}

package com.acme.showcase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Metric requests = new Metric("requests", 7, -2);
        final Renderer renderer = new TextRenderer();
        final List<String> names = new ArrayList<>();
        names.add("first request");
        names.add("second request");

        final int[] samples = new int[] {3, 5, 8};
        final int[] copy = Arrays.copyOf(samples, 4);
        System.arraycopy(samples, 1, copy, 2, 2);

        System.out.println("javan native showcase");
        System.out.println(renderer.render(requests.name(), requests.score()));
        System.out.println(names.get(0));
        System.out.println("samples " + samples[0]);
        System.out.println("copy " + copy[3]);
        System.out.println("name-length " + requests.name().length());
        System.out.println("char " + requests.name().charAt(4));
        System.out.println("same " + requests.name().equals("requests"));
        System.out.println("safe deterministic native build");
    }
}

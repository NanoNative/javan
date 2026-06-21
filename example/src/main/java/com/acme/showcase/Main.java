package com.acme.showcase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        final HashMap<String, Metric> metrics = new HashMap<>();
        metrics.put(requests.name(), requests);
        final Map<String, Metric> snapshot = Map.copyOf(metrics);
        final Optional<Request> request = Optional.of(new Request(names.get(1), copy[3]));
        final Iterator<String> iterator = names.iterator();
        final Status status = Status.READY;

        System.out.println("javan native showcase");
        System.out.println(renderer.render(requests.name(), requests.score()));
        System.out.println(names.get(0));
        System.out.println("iter " + iterator.next());
        System.out.println("request " + request.orElseThrow().name());
        System.out.println("map " + snapshot.get("requests").score());
        System.out.println("samples " + samples[0]);
        System.out.println("copy " + copy[3]);
        System.out.println("name-length " + requests.name().length());
        System.out.println("char " + requests.name().charAt(4));
        System.out.println("same " + requests.name().equals("requests"));
        System.out.println("enum " + status.name());
        System.out.println("static " + State.mode() + " " + State.bootCount());
        try {
            throw new IllegalStateException("boom");
        } catch (final IllegalStateException exception) {
            System.out.println("caught " + exception.getMessage());
        }
        System.out.println("safe deterministic native build");
    }
}

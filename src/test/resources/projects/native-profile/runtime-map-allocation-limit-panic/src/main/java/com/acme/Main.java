package com.acme;

import java.util.HashMap;
import java.util.Map;

public final class Main {
    public static void main(final String[] args) {
        final Map<String, String> values = new HashMap<>();
        values.put("k0", "v0");
        values.put("k1", "v1");
        values.put("k2", "v2");
        values.put("k3", "v3");
        values.put("k4", "v4");
        values.put("k5", "v5");
        values.put("k6", "v6");
        values.put("k7", "v7");
        values.put("k8", "v8");
        System.out.println(values.size());
    }
}

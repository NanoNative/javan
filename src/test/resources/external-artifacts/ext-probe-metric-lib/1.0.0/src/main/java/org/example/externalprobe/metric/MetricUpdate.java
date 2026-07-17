package org.example.externalprobe.metric;

public record MetricUpdate(String scope, String name, String unit, String description) {
}

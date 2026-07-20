package com.jruk8.jwarp;

public record TestWarpTarget(String worldName, double x, double y, double z, float yaw, float pitch) {
    public static TestWarpTarget defaultTarget() {
        return new TestWarpTarget("", 0.0, 100.0, 0.0, 0.0f, 0.0f);
    }
}
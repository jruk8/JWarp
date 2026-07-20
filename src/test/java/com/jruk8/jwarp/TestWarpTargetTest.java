package com.jruk8.jwarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestWarpTargetTest {
    @Test
    void defaultTargetUsesConfiguredDefaults() {
        TestWarpTarget target = TestWarpTarget.defaultTarget();

        assertEquals("", target.worldName());
        assertEquals(0.0, target.x());
        assertEquals(100.0, target.y());
        assertEquals(0.0, target.z());
        assertEquals(0.0f, target.yaw());
        assertEquals(0.0f, target.pitch());
    }
}
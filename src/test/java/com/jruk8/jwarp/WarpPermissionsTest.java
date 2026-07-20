package com.jruk8.jwarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarpPermissionsTest {
    @Test
    void normalizesWarpNames() {
        assertEquals("spawn", WarpPermissions.normalizeWarpName("  SpAwN  "));
    }

    @Test
    void resolvesDefaultPermissionNodes() {
        assertEquals("jwarp.warp.spawn", WarpPermissions.resolvePermissionNode("jwarp.warp.{warp}", "Spawn"));
    }

    @Test
    void resolvesCustomPermissionTemplates() {
        assertEquals(
            "myplugin.warps.spawn.use",
            WarpPermissions.resolvePermissionNode("myplugin.warps.{name}.use", "Spawn")
        );
    }
}

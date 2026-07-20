package com.jruk8.jwarp;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpCooldownTrackerTest {
    @Test
    void tracksCooldownsPerPlayer() {
        WarpCooldownTracker tracker = new WarpCooldownTracker();
        UUID playerId = UUID.randomUUID();

        tracker.markWarp(playerId, 1_000L);

        assertTrue(tracker.isOnCooldown(playerId, 2_000L, 3_000L));
        assertFalse(tracker.isOnCooldown(playerId, 5_000L, 3_000L));
    }

    @Test
    void clearAllRemovesTrackedPlayers() {
        WarpCooldownTracker tracker = new WarpCooldownTracker();
        UUID playerId = UUID.randomUUID();

        tracker.markWarp(playerId, 1_000L);
        tracker.clearAll();

        assertFalse(tracker.isOnCooldown(playerId, 2_000L, 3_000L));
    }
}

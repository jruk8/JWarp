package com.jruk8.jwarp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class WarpCooldownTracker {
    private final Map<UUID, Long> lastWarpTimes = new ConcurrentHashMap<>();

    boolean isOnCooldown(UUID playerId, long nowMillis, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return false;
        }

        Long lastWarpTime = lastWarpTimes.get(playerId);
        if (lastWarpTime == null) {
            return false;
        }

        return nowMillis - lastWarpTime < cooldownMillis;
    }

    long remainingMillis(UUID playerId, long nowMillis, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return 0L;
        }

        Long lastWarpTime = lastWarpTimes.get(playerId);
        if (lastWarpTime == null) {
            return 0L;
        }

        long remaining = cooldownMillis - (nowMillis - lastWarpTime);
        return Math.max(0L, remaining);
    }

    void markWarp(UUID playerId, long nowMillis) {
        lastWarpTimes.put(playerId, nowMillis);
    }

    void clear(UUID playerId) {
        lastWarpTimes.remove(playerId);
    }

    void clearAll() {
        lastWarpTimes.clear();
    }
}

package com.jruk8.jwarp;

import java.util.Locale;

final class WarpPermissions {
    private WarpPermissions() {
    }

    static String normalizeWarpName(String warpName) {
        if (warpName == null) {
            return "";
        }

        return warpName.trim().toLowerCase(Locale.ROOT);
    }

    static String resolvePermissionNode(String template, String warpName) {
        String resolvedTemplate = template == null || template.isBlank()
            ? "jwarp.warp.{warp}"
            : template;
        String normalizedWarpName = normalizeWarpName(warpName);
        return resolvedTemplate.replace("{warp}", normalizedWarpName).replace("{name}", normalizedWarpName);
    }
}

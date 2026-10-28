package com.hotel.backoffice.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
}

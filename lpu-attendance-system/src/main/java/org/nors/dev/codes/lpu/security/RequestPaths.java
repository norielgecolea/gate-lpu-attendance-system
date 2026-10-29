package org.nors.dev.codes.lpu.security;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestPaths {

    private RequestPaths() {
    }

    /** Path inside the webapp context, without the query string. */
    public static String withinContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        int query = uri.indexOf('?');
        if (query >= 0) {
            uri = uri.substring(0, query);
        }
        return uri.isEmpty() ? "/" : uri;
    }
}

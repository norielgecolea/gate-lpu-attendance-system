package org.nors.dev.codes.lpu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires a signature on picture, video, and tone downloads. */
@Component
public class MediaSignatureFilter extends OncePerRequestFilter {

    private final MediaUrlSigner signer;

    public MediaSignatureFilter(MediaUrlSigner signer) {
        this.signer = signer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            return true;
        }
        return !MediaPaths.isMediaPath(RequestPaths.withinContext(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = RequestPaths.withinContext(request);
        if (!signer.isValid(path, request.getParameter("exp"), request.getParameter("sig"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}

package com.chandan.randomchat.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JwtAuthFilter — runs ONCE per request before any controller.
 *
 * What it does:
 *   1. Reads "Authorization: Bearer <token>" header
 *   2. Validates JWT via JwtService (pure math — NO DB call)
 *   3. Extracts userId + appId from token payload
 *   4. Sets Spring Security context so controllers know who the caller is
 *   5. Stores userId + appId as request attributes for controller use
 *
 * Skipped for:
 *   - POST /api/user/init      (identity is established here, no token yet)
 *   - GET  /actuator/health    (load balancer health check)
 *
 * If token is missing or invalid on a protected endpoint → 401 returned
 * directly from this filter (never reaches controller).
 *
 * How controllers access userId after this filter runs:
 *   UUID userId = (UUID) request.getAttribute("userId");
 *   String appId = (String) request.getAttribute("appId");
 *
 * Or inject via @RequestAttribute in controller method parameters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    // Endpoints that do NOT require a JWT
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/user/init",
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token provided on a protected endpoint
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "TOKEN_MISSING",
                    "Authorization header with Bearer token is required");
            return;
        }

        String token = authHeader.substring(7); // strip "Bearer "

        try {
            // Validate and extract — throws TokenExpiredException or InvalidTokenException
            Claims claims = jwtService.validateAndExtract(token);
            UUID userId   = jwtService.extractUserId(claims);
            String appId  = jwtService.extractAppId(claims);

            // Store on request so controllers/services can use without re-parsing
            request.setAttribute("userId", userId);
            request.setAttribute("appId",  appId);

            // Tell Spring Security this request is authenticated
            // Authority "ROLE_USER" allows @PreAuthorize("hasRole('USER')") if needed later
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),          // principal = userId string
                            null,                       // no credentials needed
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("JWT auth OK — userId={} appId={} path={}",
                    userId, appId, request.getRequestURI());

            filterChain.doFilter(request, response);

        } catch (com.chandan.randomchat.exception.TokenExpiredException ex) {
            sendUnauthorized(response, "TOKEN_EXPIRED",
                    "JWT has expired. Call /api/user/init to get a new token.");
        } catch (com.chandan.randomchat.exception.InvalidTokenException ex) {
            sendUnauthorized(response, "INVALID_TOKEN", "JWT is invalid or malformed.");
        }
    }

    // Write JSON 401 response directly — bypasses Spring MVC
    private void sendUnauthorized(HttpServletResponse response,
                                  String error, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}"
        );
    }
}
package com.chandan.randomchat.security;

import com.chandan.randomchat.exception.AppNotFoundException;
import com.chandan.randomchat.repository.AppRegistryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AppIdInterceptor — validates X-App-ID header on every request.
 *
 * Runs AFTER JwtAuthFilter (filters run before interceptors in Spring).
 *
 * What it does:
 *   1. Reads X-App-ID header
 *   2. Checks it exists in app_registry and is_active = TRUE
 *   3. Stores validated appId as request attribute "appId"
 *      (overwrites whatever JwtAuthFilter set — both should match,
 *       but we trust the header for /init where no token exists yet)
 *
 * Why validate BOTH from JWT and from header?
 *   - /init: no JWT yet → only header available → validate here
 *   - Protected endpoints: JWT has appId → cross-check with header
 *     If they differ → something is wrong (token from different app)
 *
 * AppNotFoundException (400) returned if:
 *   - Header is missing entirely
 *   - app_id not found in app_registry
 *   - app is inactive (is_active = FALSE)
 *
 * Cache note: app_registry rarely changes — Phase 3+ can cache the
 * valid app IDs in a Set loaded at startup to avoid DB hit per request.
 * For Phase 1-2, DB lookup is fine (single indexed query, < 1ms).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppIdInterceptor implements HandlerInterceptor {

    private final AppRegistryRepository appRegistryRepository;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String appId = request.getHeader("X-App-ID");

        if (appId == null || appId.isBlank()) {
            throw new AppNotFoundException("(missing)");
        }

        // Validate against DB — throws 400 if not found or inactive
        boolean valid = appRegistryRepository.existsByAppIdAndIsActiveTrue(appId);
        if (!valid) {
            throw new AppNotFoundException(appId);
        }

        // Store validated appId on request — controllers read from here
        request.setAttribute("appId", appId);

        log.debug("AppId validated: {} for path: {}", appId, request.getRequestURI());
        return true; // continue to controller
    }
}
package com.chandan.randomchat.config;

import com.chandan.randomchat.security.AppIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * AppConfig — MVC configuration.
 *
 * Registers:
 *   1. AppIdInterceptor on all /api/** routes
 *   2. CORS rules for Android HTTP calls
 *
 * CORS note for Android:
 *   Android apps making HTTP calls are NOT browser CORS — native HTTP clients
 *   (OkHttp, Retrofit) don't do preflight OPTIONS requests.
 *   We still configure CORS for:
 *   - Admin web dashboard (browser-based)
 *   - Any future web client
 *   - Postman/curl testing during development
 */
@Configuration
@RequiredArgsConstructor
public class AppConfig implements WebMvcConfigurer {

    private final AppIdInterceptor appIdInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appIdInterceptor)
                .addPathPatterns("/api/**")       // validate X-App-ID on all API calls
                .excludePathPatterns(
                        "/actuator/**",              // health checks don't need app ID
                        "/ws/**"                     // WebSocket — handled separately
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")           // restrict to your domain in prod
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(false)              // we use Bearer token, not cookies
                .maxAge(3600);
    }
}
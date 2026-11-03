package com.bhavesh.urlshortner.config;

import com.bhavesh.urlshortner.exception.RateLimitExceededException;
import com.bhavesh.urlshortner.service.ClientRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ClientRateLimiter limiter;

    public RateLimitInterceptor(ClientRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;                       // only creation is limited
        }

        // The container decides how much to trust X-Forwarded-For (see application.yml).
        // We never read that header ourselves, because clients can fake it.
        ClientRateLimiter.Decision decision = limiter.tryAcquire(request.getRemoteAddr());

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
        return true;
    }
}
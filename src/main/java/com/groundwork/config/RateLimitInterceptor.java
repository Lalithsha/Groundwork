package com.groundwork.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.groundwork.application.CurrentUser;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final CurrentUser currentUser;

    public RateLimitInterceptor(StringRedisTemplate redis, CurrentUser currentUser) {
        this.redis = redis;
        this.currentUser = currentUser;
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(20)
            .refillIntervally(20, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String identity = currentUser.email().orElseGet(() -> request.getRemoteAddr());
        if (tryConsumeDistributed(identity)) {
            return true;
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Maximum 20 requests per minute allowed.\"}");
            return false;
        }
    }

    private boolean tryConsumeDistributed(String identity) {
        String key = "rate-limit:minute:" + Integer.toHexString(identity.hashCode());
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(1));
            return count != null && count <= 20;
        } catch (RuntimeException unavailable) {
            Bucket bucket = buckets.computeIfAbsent(identity, ignored -> createNewBucket());
            return bucket.tryConsume(1);
        }
    }
}

package com.gideon.finsafe.utils;

import com.gideon.finsafe.config.RateLimitPropertiesConfig;
import com.gideon.finsafe.exceptions.CircuitBreakerOpenException;
import com.gideon.finsafe.exceptions.RateLimitExceededException;
import com.gideon.finsafe.service.CircuitBreakerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final String HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RESET = "X-RateLimit-Reset";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitPropertiesConfig rateLimitProperties;
    private final CircuitBreakerService circuitBreakerService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler
    ) {


        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        String clientId = extractClientIdentifier(request);
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + clientId;

        try {

            Long currentCount = circuitBreakerService.execute(() ->
                redisTemplate.opsForValue().increment(rateLimitKey)
            );

            if (currentCount == null) {
                log.warn("Redis increment returned null for key={}, allowing request", rateLimitKey);
                return true;
            }

            if (currentCount == 1) {
                circuitBreakerService.execute(() ->
                redisTemplate.expire(rateLimitKey, Duration.ofSeconds(rateLimitProperties.getWindowSeconds()))
                );
            }


            int maxRequests = rateLimitProperties.getMaxRequests();
            long remaining = Math.max(0, maxRequests - currentCount);
            long resetTime = System.currentTimeMillis() / 1000 + rateLimitProperties.getWindowSeconds();

            response.setHeader(HEADER_LIMIT, String.valueOf(maxRequests));
            response.setHeader(HEADER_REMAINING, String.valueOf(remaining));
            response.setHeader(HEADER_RESET, String.valueOf(resetTime));


            if (currentCount > maxRequests) {
                log.warn("Rate limit exceeded: clientId={} count={}/{}",
                        clientId, currentCount, maxRequests);

                throw new RateLimitExceededException(clientId, rateLimitProperties.getWindowSeconds());
            }

            log.debug("Rate limit check passed: clientId={} count={}/{}",
                    clientId, currentCount, maxRequests);

            return true;

        } catch (RateLimitExceededException | CircuitBreakerOpenException ex) {
            throw ex;
        }catch (Exception ex) {
            log.error("Rate limit check failed due to Redis error, allowing request: clientId={}",
                    clientId, ex);
            return true;
        }
    }


    private String extractClientIdentifier(HttpServletRequest request) {
        if (rateLimitProperties.isUseIpAddress()) {
            return extractIpAddress(request);
        } else {
            String apiKey = request.getHeader(rateLimitProperties.getApiKeyHeader());
            return apiKey != null && !apiKey.isBlank() ? apiKey : "anonymous";
        }
    }


    private String extractIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first (client IP)
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        return request.getRemoteAddr();
    }
}
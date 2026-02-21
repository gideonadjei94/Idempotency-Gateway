package com.gideon.finsafe.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitPropertiesConfig {

    private boolean enabled = true;
    private int maxRequests = 100;
    private int windowSeconds = 60;
    private boolean useIpAddress = true;
    private String apiKeyHeader = "X-API-Key";
}

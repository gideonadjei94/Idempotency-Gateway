package com.gideon.finsafe.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyPropertiesConfig {
    private long keyTtl = 3600;
    private long lockTtl = 30;
    private long processingDelay = 2000;
}

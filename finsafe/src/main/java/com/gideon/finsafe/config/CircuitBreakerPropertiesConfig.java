package com.gideon.finsafe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Data
@Component
@ConfigurationProperties(prefix = "circuit-breaker")
public class CircuitBreakerPropertiesConfig {

    private boolean enabled = true;
    private int failureRateThreshold = 10;
    private int minimumNumberOfCalls = 1;
    private int waitDurationInOpenStateSeconds = 60;
    private int permittedNumberOfCallsInHalfOpenState = 3;
    private String failureStrategy = "fail-safe";
}

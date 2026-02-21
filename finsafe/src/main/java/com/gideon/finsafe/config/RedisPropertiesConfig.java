package com.gideon.finsafe.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisPropertiesConfig {
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private Duration timeout = Duration.ofMillis(2000);

    public long getTimeoutMillis() {
        return timeout != null ? timeout.toMillis() : 2000L;
    }
}

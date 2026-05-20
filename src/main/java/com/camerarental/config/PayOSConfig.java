package com.camerarental.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.payos")
@Getter
@Setter
public class PayOSConfig {

    private String apiKey;
    private String clientId;
    private String checksumKey;
    private String returnUrl;
    private String cancelUrl;
    private String webhookUrl;
}

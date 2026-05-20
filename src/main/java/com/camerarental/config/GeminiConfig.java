package com.camerarental.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.gemini")
@Slf4j
public class GeminiConfig {

    private String apiKey;
    private String model;

    public String getApiUrl() {
        String modelName = (model != null && !model.isBlank()) ? model : "gemini-2.0-flash";
        return "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";
    }

    public boolean isConfigured() {
        boolean configured = apiKey != null && !apiKey.isBlank();
        if (!configured) {
            log.warn("[Gemini] API key chưa được cấu hình. Vui lòng thiết lập GEMINI_API_KEY trong .env hoặc application.yml");
        }
        return configured;
    }
}

package com.camerarental.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Telegram Bot Polling Service - FIXED OFFSET BUG
 * 
 * Key fixes:
 * 1. URL ALWAYS includes offset parameter when offset > 0
 * 2. Offset updated AFTER each update is processed (updateId + 1)
 * 3. Comprehensive logging at every step
 */
@Service
@Slf4j
public class TelegramPollingService {

    private final TelegramCommandService telegramCommandService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // NEXT offset to acknowledge - starts at 0, increments after each processed update
    private final AtomicLong nextOffset = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${app.telegram.polling-enabled:true}")
    private boolean pollingEnabled;

    @Value("${app.telegram.poll-interval-ms:3000}")
    private long pollIntervalMs;

    @Value("${app.telegram.chat-id:}")
    private String adminChatId;

    private volatile boolean initialized = false;

    public TelegramPollingService(TelegramCommandService telegramCommandService) {
        this.telegramCommandService = telegramCommandService;
        
        // Create RestTemplate ONLY with String converter (no JSON auto-parse)
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().clear();
        this.restTemplate.getMessageConverters().add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @PostConstruct
    public void init() {
        log.info("");
        log.info("==========================================");
        log.info("  TELEGRAM POLLING SERVICE - INITIALIZING");
        log.info("==========================================");
        log.info("  telegram.enabled:         {}", telegramEnabled);
        log.info("  telegram.pollingEnabled:   {}", pollingEnabled);
        log.info("  telegram.pollIntervalMs:  {}", pollIntervalMs);
        log.info("  telegram.chatId:           {}", maskId(adminChatId));
        log.info("  telegram.botToken:         {}", maskToken(botToken));
        log.info("==========================================");

        if (!telegramEnabled) {
            log.warn("  => DISABLED: telegram.enabled=false");
            log.info("==========================================");
            return;
        }

        if (!pollingEnabled) {
            log.warn("  => DISABLED: telegram.polling-enabled=false");
            log.info("==========================================");
            return;
        }

        if (botToken == null || botToken.isBlank()) {
            log.error("  => FAILED: bot token not configured!");
            log.info("==========================================");
            return;
        }

        if (adminChatId == null || adminChatId.isBlank()) {
            log.error("  => FAILED: chatId not configured!");
            log.info("==========================================");
            return;
        }

        try {
            // Check webhook
            String webhookStatus = checkWebhookStatus();
            log.info("  Webhook status: {}", webhookStatus);
            
            initialized = true;
            
            log.info("");
            log.info("  => STATUS: STARTED");
            log.info("  => Initial nextOffset: {}", nextOffset.get());
            log.info("==========================================");
            log.info("");
            
        } catch (Exception e) {
            log.error("  => FAILED to initialize!");
            log.error("  => Error: {}", e.getMessage(), e);
            log.info("==========================================");
        }
    }

    // ==================== MAIN POLLING ====================

    @Scheduled(fixedRateString = "${app.telegram.poll-interval-ms:3000}")
    public void pollUpdates() {
        if (!initialized || !telegramEnabled || !pollingEnabled) {
            return;
        }

        try {
            executePoll();
        } catch (Exception e) {
            log.error("[TELEGRAM] pollUpdates() UNEXPECTED ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Execute single poll cycle
     */
    private void executePoll() {
        // Get current offset
        long currentOffset = nextOffset.get();
        
        // Log BEFORE polling
        log.info("[TELEGRAM] ============================================");
        log.info("[TELEGRAM] POLLING CYCLE START");
        log.info("[TELEGRAM] nextOffset: {}", currentOffset);
        
        try {
            // Build URL with offset
            String url = buildUrl(currentOffset);
            log.info("[TELEGRAM] URL: {}", maskUrl(url));
            
            // Execute GET request
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                URI.create(url), 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            String body = response.getBody();
            
            if (body == null || body.isBlank()) {
                log.info("[TELEGRAM] Empty response, no updates");
                return;
            }
            
            log.info("[TELEGRAM] Response received ({} chars)", body.length());
            
            // Process the response
            processResponse(body);
            
        } catch (RestClientException e) {
            handleException(e);
        } catch (Exception e) {
            log.error("[TELEGRAM] executePoll() ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Build getUpdates URL with offset parameter
     * CRITICAL: Offset tells Telegram which updates we've already received
     */
    private String buildUrl(long offset) {
        String base = "https://api.telegram.org/bot" + botToken + "/getUpdates";
        
        // Build URL with offset parameter
        // When offset=0: get ALL pending updates (first poll)
        // When offset>0: skip updates with id <= offset
        if (offset > 0) {
            // IMPORTANT: offset tells Telegram "I've received updates up to this ID"
            // Telegram will only return updates with update_id > offset
            return base + "?offset=" + offset + "&timeout=5&allowed_updates=message";
        } else {
            // First poll: get all pending updates
            return base + "?timeout=5&allowed_updates=message";
        }
    }

    /**
     * Process Telegram API response
     */
    private void processResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            
            // Check API response
            if (!root.has("ok") || !root.get("ok").asBoolean()) {
                log.error("[TELEGRAM] API returned error: {}", json);
                return;
            }
            
            JsonNode results = root.get("result");
            if (results == null || !results.isArray()) {
                log.debug("[TELEGRAM] No result array in response");
                return;
            }
            
            int updateCount = results.size();
            
            if (updateCount == 0) {
                log.info("[TELEGRAM] No updates in response (empty array)");
                return;
            }
            
            // Process each update
            log.info("[TELEGRAM] ============================================");
            log.info("[TELEGRAM] GOT {} UPDATE(S)", updateCount);
            
            int processed = 0;
            
            for (int i = 0; i < results.size(); i++) {
                JsonNode update = results.get(i);
                processUpdate(update, i);
                processed++;
            }
            
            log.info("[TELEGRAM] ============================================");
            log.info("[TELEGRAM] Processed {} updates", processed);
            log.info("[TELEGRAM] Current nextOffset: {}", nextOffset.get());
            log.info("[TELEGRAM] Total processed: {}", totalProcessed.get());
            log.info("[TELEGRAM] POLLING CYCLE END");
            log.info("[TELEGRAM] ===========================================");
            
        } catch (Exception e) {
            log.error("[TELEGRAM] processResponse() ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Process single update
     */
    private void processUpdate(JsonNode update, int index) {
        try {
            // Must have update_id
            if (!update.has("update_id")) {
                log.warn("[TELEGRAM] Update #{} missing update_id", index);
                return;
            }
            
            long updateId = update.get("update_id").asLong();
            log.info("[TELEGRAM] ---------");
            log.info("[TELEGRAM] Processing updateId: {}", updateId);
            
            // Must have message
            if (!update.has("message")) {
                log.info("[TELEGRAM] Update {} has no message (callback, etc.)", updateId);
                updateNextOffset(updateId);
                return;
            }
            
            JsonNode message = update.get("message");
            
            // Must have chat.id
            if (!message.has("chat") || !message.get("chat").has("id")) {
                log.warn("[TELEGRAM] Update {} missing chat.id", updateId);
                updateNextOffset(updateId);
                return;
            }
            
            long chatId = message.get("chat").get("id").asLong();
            String chatIdStr = String.valueOf(chatId);
            
            // Check authorization
            if (!isAdminChat(chatIdStr)) {
                log.warn("[TELEGRAM] Update {} from unauthorized chat: {}", updateId, chatId);
                updateNextOffset(updateId);
                return;
            }
            
            // Must have text
            if (!message.has("text")) {
                log.info("[TELEGRAM] Update {} has no text", updateId);
                updateNextOffset(updateId);
                return;
            }
            
            String text = message.get("text").asText();
            if (text == null || text.isBlank()) {
                log.info("[TELEGRAM] Update {} has empty text", updateId);
                updateNextOffset(updateId);
                return;
            }
            
            // Log command received
            log.info("[TELEGRAM] ============================================");
            log.info("[TELEGRAM] COMMAND RECEIVED!");
            log.info("[TELEGRAM] UpdateId: {}", updateId);
            log.info("[TELEGRAM] Command: '{}'", text);
            log.info("[TELEGRAM] ChatId: {}", chatId);
            log.info("[TELEGRAM] ============================================");
            
            // Update offset BEFORE processing command
            // This prevents duplicate processing if command throws exception
            updateNextOffset(updateId);
            
            // Process the command
            telegramCommandService.handleCommand(text, chatIdStr);
            
            // Increment counter
            totalProcessed.incrementAndGet();
            
            log.info("[TELEGRAM] Command '{}' processed successfully", text);
            
        } catch (Exception e) {
            log.error("[TELEGRAM] processUpdate() ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Update nextOffset to updateId + 1
     * This tells Telegram "I've received this update"
     */
    private void updateNextOffset(long updateId) {
        long newOffset = updateId + 1;
        long oldOffset = nextOffset.get();
        
        // Only update if new offset is greater
        if (newOffset > oldOffset) {
            nextOffset.set(newOffset);
            log.info("[TELEGRAM] Offset updated: {} -> {} (updateId={}+1)", oldOffset, newOffset, updateId);
        } else {
            log.debug("[TELEGRAM] Offset not updated: current={}, new={}", oldOffset, newOffset);
        }
    }

    /**
     * Check if chatId is admin
     */
    private boolean isAdminChat(String chatId) {
        if (chatId == null || adminChatId == null) {
            return false;
        }
        return chatId.equals(adminChatId);
    }

    /**
     * Handle RestClientException
     */
    private void handleException(Exception e) {
        String msg = e.getMessage();
        
        if (msg != null && (msg.contains("timeout") || msg.contains("Timeout") || 
                            msg.contains("Connection refused"))) {
            log.trace("[TELEGRAM] Timeout (no updates): {}", msg);
        } else {
            log.error("[TELEGRAM] RestClient ERROR: {}", msg);
        }
    }

    /**
     * Check webhook status
     */
    private String checkWebhookStatus() {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getWebhookInfo";
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isBlank()) {
                return "UNKNOWN (empty)";
            }
            
            JsonNode root = objectMapper.readTree(response);
            if (!root.has("ok") || !root.get("ok").asBoolean()) {
                return "API_ERROR";
            }
            
            JsonNode result = root.get("result");
            if (result == null || !result.has("url") || result.get("url").asText().isBlank()) {
                return "NOT_SET (OK)";
            }
            
            // Delete webhook
            log.warn("  Found webhook: {}", result.get("url").asText());
            String deleteUrl = "https://api.telegram.org/bot" + botToken + "/deleteWebhook?drop_pending_updates=false";
            restTemplate.getForObject(deleteUrl, String.class);
            return "DELETED";
            
        } catch (Exception e) {
            return "CHECK_FAILED: " + e.getMessage();
        }
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Reset offset - get latest update ID from Telegram
     */
    public void resetOffset() {
        log.info("[TELEGRAM] ========== RESET OFFSET ==========");
        log.info("[TELEGRAM] Current nextOffset: {}", nextOffset.get());
        
        try {
            // Get latest update
            String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?limit=1&timeout=0";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                URI.create(url), 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            String body = response.getBody();
            
            if (body != null && !body.isBlank()) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode results = root.get("result");
                
                if (results != null && results.isArray() && !results.isEmpty()) {
                    long latestId = results.get(0).get("update_id").asLong();
                    nextOffset.set(latestId + 1);
                    log.info("[TELEGRAM] Set nextOffset to {} (latest={}+1)", latestId + 1, latestId);
                }
            }
            
            log.info("[TELEGRAM] New nextOffset: {}", nextOffset.get());
            log.info("[TELEGRAM] ================================");
            
        } catch (Exception e) {
            log.error("[TELEGRAM] Reset offset FAILED: {}", e.getMessage());
            log.info("[TELEGRAM] ================================");
        }
    }

    /**
     * Trigger manual poll
     */
    public void triggerPoll() {
        log.info("[TELEGRAM] Manual poll triggered");
        executePoll();
    }

    /**
     * Check if polling is running
     */
    public boolean isRunning() {
        return initialized && telegramEnabled && pollingEnabled;
    }

    /**
     * Get current offset
     */
    public long getCurrentOffset() {
        return nextOffset.get();
    }

    /**
     * Get total processed count
     */
    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    /**
     * Get status summary
     */
    public String getStatus() {
        return String.format("TelegramPolling[running=%s, nextOffset=%d, processed=%d]",
            isRunning(), nextOffset.get(), totalProcessed.get());
    }

    // ==================== HELPERS ====================

    private String maskId(String id) {
        if (id == null || id.isBlank()) return "(null)";
        if (id.length() <= 4) return "****";
        return id.substring(0, 2) + "****" + id.substring(id.length() - 2);
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) return "(null)";
        if (token.length() <= 5) return "****";
        return token.substring(0, 5) + "****";
    }

    private String maskUrl(String url) {
        if (url == null) return "(null)";
        return url.replace(botToken, maskToken(botToken));
    }
}

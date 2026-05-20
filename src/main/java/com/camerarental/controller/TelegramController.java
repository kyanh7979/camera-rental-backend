package com.camerarental.controller;

import com.camerarental.dto.response.ApiResponse;
import com.camerarental.service.TelegramBotService;
import com.camerarental.service.TelegramCommandService;
import com.camerarental.service.TelegramPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Telegram Bot Controller for testing and debugging
 */
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramController {

    private final TelegramBotService telegramBotService;
    private final TelegramCommandService telegramCommandService;
    private final TelegramPollingService telegramPollingService;

    @Value("${app.telegram.chat-id:}")
    private String configuredChatId;

    @Value("${app.telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${app.telegram.polling-enabled:true}")
    private boolean pollingEnabled;

    // ==================== TEST ENDPOINTS ====================

    /**
     * Test /help command
     */
    @GetMapping("/test-help")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testHelp() {
        log.info("=== /api/telegram/test-help ===");
        
        Map<String, Object> result = new HashMap<>();
        result.put("configuredChatId", configuredChatId);
        result.put("telegramEnabled", telegramEnabled);
        result.put("pollingEnabled", pollingEnabled);
        result.put("pollingRunning", telegramPollingService.isRunning());
        result.put("currentOffset", telegramPollingService.getCurrentOffset());
        
        try {
            // Send test message
            String testMsg = "\n[TEST] Test endpoint working!\nTime: " + 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n";
            
            telegramBotService.sendMessage(testMsg);
            result.put("testMessageSent", true);
            
            // Test /help command
            log.info("Sending /help to chatId: {}", configuredChatId);
            telegramCommandService.handleCommand("/help", configuredChatId);
            result.put("helpCommandSent", true);
            
            log.info("=== /test-help SUCCESS ===");
            return ResponseEntity.ok(ApiResponse.success("Test completed", result));
            
        } catch (Exception e) {
            log.error("=== /test-help FAILED: {} ===", e.getMessage(), e);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error("Test failed: " + e.getMessage()));
        }
    }

    /**
     * Test send message
     */
    @GetMapping("/test-send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testSend() {
        log.info("=== /api/telegram/test-send ===");
        
        Map<String, Object> result = new HashMap<>();
        result.put("configuredChatId", configuredChatId);
        result.put("telegramConfigured", telegramBotService.isConfigured());
        
        try {
            String msg = "\n[TEST] Backend -> Telegram send working!\nTime: " + 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n";
            
            telegramBotService.sendMessage(msg);
            result.put("sent", true);
            
            log.info("=== /test-send SUCCESS ===");
            return ResponseEntity.ok(ApiResponse.success("Message sent", result));
            
        } catch (Exception e) {
            log.error("=== /test-send FAILED: {} ===", e.getMessage(), e);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error("Send failed: " + e.getMessage()));
        }
    }

    // ==================== STATUS ENDPOINTS ====================

    /**
     * Get Telegram status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("telegramEnabled", telegramEnabled);
        status.put("pollingEnabled", pollingEnabled);
        status.put("pollingRunning", telegramPollingService.isRunning());
        status.put("currentOffset", telegramPollingService.getCurrentOffset());
        status.put("totalProcessed", telegramPollingService.getTotalProcessed());
        status.put("configuredChatId", configuredChatId);
        status.put("botConfigured", telegramBotService.isConfigured());
        status.put("pollingStatus", telegramPollingService.getStatus());
        
        return ResponseEntity.ok(ApiResponse.success("Telegram status", status));
    }

    /**
     * Reset polling offset
     */
    @GetMapping("/reset-offset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetOffset() {
        log.info("=== /api/telegram/reset-offset ===");
        
        long oldOffset = telegramPollingService.getCurrentOffset();
        
        try {
            telegramPollingService.resetOffset();
            
            Map<String, Object> result = new HashMap<>();
            result.put("oldOffset", oldOffset);
            result.put("newOffset", telegramPollingService.getCurrentOffset());
            result.put("message", "Offset reset successfully");
            
            log.info("=== Offset reset: {} -> {} ===", oldOffset, telegramPollingService.getCurrentOffset());
            return ResponseEntity.ok(ApiResponse.success("Offset reset", result));
            
        } catch (Exception e) {
            log.error("=== /reset-offset FAILED: {} ===", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Reset failed: " + e.getMessage()));
        }
    }

    /**
     * Trigger manual poll
     */
    @GetMapping("/trigger-poll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerPoll() {
        log.info("=== /api/telegram/trigger-poll ===");
        
        try {
            long beforeOffset = telegramPollingService.getCurrentOffset();
            telegramPollingService.triggerPoll();
            long afterOffset = telegramPollingService.getCurrentOffset();
            
            Map<String, Object> result = new HashMap<>();
            result.put("offsetBefore", beforeOffset);
            result.put("offsetAfter", afterOffset);
            result.put("offsetChanged", beforeOffset != afterOffset);
            
            log.info("=== Poll triggered: offset {} -> {} ===", beforeOffset, afterOffset);
            return ResponseEntity.ok(ApiResponse.success("Poll triggered", result));
            
        } catch (Exception e) {
            log.error("=== /trigger-poll FAILED: {} ===", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Poll failed: " + e.getMessage()));
        }
    }
}

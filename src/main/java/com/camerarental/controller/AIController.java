package com.camerarental.controller;

import com.camerarental.dto.request.ChatRequest;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.ChatResponse;
import com.camerarental.exception.AIServiceException;
import com.camerarental.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Chat", description = "API tu van may anh voi AI (Gemini)")
public class AIController {

    private final AIService aiService;

    @PostMapping("/chat")
    @Operation(summary = "Gui tin nhan chat voi AI", description = "Nhan tu van ve may anh, lens, phu kien tu AI (Gemini)")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        log.info("[AI Controller] Chat request received: {}", request.getMessage());
        
        try {
            // Service returns reply string (may be fallback or real AI response)
            String reply = aiService.chat(request);
            
            // Build success response
            ChatResponse chatResponse = ChatResponse.success(reply);
            log.info("[AI Controller] Success - reply length: {}", reply.length());
            
            return ResponseEntity.ok(ApiResponse.success(chatResponse));
            
        } catch (AIServiceException ex) {
            HttpStatus status = ex.getStatus();
            log.error("[AI Controller] AIServiceException: status={}, code={}, message={}", 
                    status.value(), ex.getErrorCode(), ex.getMessage());
            
            // Return error response with HTTP status
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Kiem tra trang thai AI", description = "Kiem tra xem Gemini AI co dang hoat dong khong")
    public ResponseEntity<ApiResponse<ChatResponse>> getStatus() {
        try {
            boolean isConfigured = aiService.isConfigured();

            if (isConfigured) {
                return ResponseEntity.ok(ApiResponse.success(
                        ChatResponse.builder()
                                .reply("Gemini AI dang hoat dong")
                                .success(true)
                                .build()
                ));
            } else {
                return ResponseEntity.ok(ApiResponse.success(
                        ChatResponse.builder()
                                .reply("Gemini AI chua duoc cau hinh")
                                .success(false)
                                .build()
                ));
            }
        } catch (Exception e) {
            log.error("[AI Controller] Status check error: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(
                    ChatResponse.builder()
                            .reply("AI khong kha dung")
                            .success(false)
                            .build()
            ));
        }
    }
}

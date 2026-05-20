package com.camerarental.service.impl;

import com.camerarental.config.GeminiConfig;
import com.camerarental.dto.request.ChatRequest;
import com.camerarental.exception.AIServiceException;
import com.camerarental.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AIService {

    private final GeminiConfig geminiConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ========== CACHE ==========
    private final ConcurrentHashMap<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10000; // 10 seconds

    // ========== RETRY CONFIG ==========
    private static final int MAX_RETRIES = 2;
    private static final long[] RETRY_DELAYS_MS = {2000, 5000}; // 2s, 5s

    // ========== SYSTEM PROMPT - TIẾNG VIỆT CÓ DẤU ==========
    private static final String SYSTEM_PROMPT = 
        "Bạn là trợ lý tư vấn cho thuê máy ảnh chuyên nghiệp tại LensRent - website cho thuê máy ảnh cao cấp.\n\n" +
        "NHIỆM VỤ:\n" +
        "- Tư vấn lựa chọn máy ảnh, lens, phụ kiện phù hợp với nhu cầu khách hàng\n" +
        "- Giới thiệu các gói combo tiết kiệm (máy + lens, máy + phụ kiện)\n" +
        "- Tính toán chi phí thuê theo ngày/tuần/tháng\n" +
        "- Hướng dẫn bảo quản và sử dụng máy\n" +
        "- Giới thiệu các thương hiệu: Canon, Sony, Nikon, Fujifilm, Panasonic, GoPro, DJI, Blackmagic, Insta360\n\n" +
        "PHONG CÁCH TRẢ LỜI:\n" +
        "- LUÔN trả lời bằng tiếng Việt có dấu, tự nhiên như nhân viên tư vấn trực tiếp\n" +
        "- Ngắt câu hơm, dễ đọc\n" +
        "- Đưa ra gợi ý cụ thể với tên sản phẩm thật\n" +
        "- Nếu không biết chắc chắn, nói rõ là cần tra cứu thêm\n" +
        "- Không đi chệch lệnh (nếu hỏi ngoại lệ phạm pháp, tự nhiên chuyển hướng)\n" +
        "- Giới hạn độ dài tin nhắn (tối đa 3-4 dòng)\n" +
        "- LUÔN dùng TIẾNG VIỆT CÓ DẤU, không dùng tiếng Việt không dấu\n\n" +
        "LƯU Ý:\n" +
        "- Chỉ tư vấn về máy ảnh và các chủ đề liên quan\n" +
        "- Không có giá trị tra cứu thực tế, chỉ tư vấn chung\n" +
        "- Không cám tình huống khách hàng\n" +
        "- Nếu câu hỏi không rõ, hỏi lại để hiểu hơn\n" +
        "- Trả lời ngắn gọn, thân thiện, có dấu đầy đủ";

    // ========== FALLBACK RESPONSES - TIẾNG VIỆT CÓ DẤU ==========
    private static final Map<String, List<String>> FALLBACK_RESPONSES = new LinkedHashMap<>();
    
    @PostConstruct
    public void initFallbackResponses() {
        FALLBACK_RESPONSES.put("vlog", Arrays.asList(
            "Combo Sony ZV-E10 + Lens kit rất phù hợp cho bạn mới làm vlog. Chi phí khoảng 300-500k/ngày. Máy nhỏ gọn, dễ mang theo.",
            "GoPro Hero 12 là lựa chọn tuyệt vời nếu bạn thích quay đường phố, phượt và hành trình du lịch. Giá thuê khoảng 250.000đ/ngày.",
            "Nếu quay indoor, nên chọn Sony A6400 + Lens 18-105. Hình ảnh đẹp, âm thanh rõ."
        ));
        
        FALLBACK_RESPONSES.put("beginner", Arrays.asList(
            "Nếu bạn mới học chụp ảnh, nên chọn Canon EOS M50 Mark II. Dễ sử dụng, giá vừa phải.",
            "Sony A6100 là máy tuyệt vời cho người mới. AF nhanh, dễ mang theo.",
            "Nikon Z30 cũng là lựa chọn tốt cho người mới. Màn hình xoay, chất lượng ảnh tốt."
        ));
        
        FALLBACK_RESPONSES.put("sony", Arrays.asList(
            "Sony có nhiều model phổ biến: A7C (nhỏ gọn), A7IV (chuyên nghiệp), ZV-E1 (vlog). Bạn cần loại nào?",
            "Nếu thích Sony, gợi ý ZV-E10 cho người mới. Giá rẻ, chất lượng tốt.",
            "Sony Lens kit 16-50mm đi kèm ZV-E10 rất phù hợp để bạn làm quen."
        ));
        
        FALLBACK_RESPONSES.put("canon", Arrays.asList(
            "Canon EOS R10 là lựa chọn tốt cho người mới. Nhỏ gọn, AF tốt.",
            "Canon R7 cho người cần tốc độ cao, 30fps chụp liên tiếp.",
            "Nếu muốn quay video, Canon R6 Mark II hoặc R5 là tuyệt vời."
        ));
        
        FALLBACK_RESPONSES.put("lens", Arrays.asList(
            "Lens 50mm f/1.8 rất phù hợp cho chụp chân dung, background blur đẹp.",
            "Lens zoom 24-70mm đa dụng, phù hợp cho nhiều tình huống.",
            "Nếu quay vlog, lens góc rộng 10-18mm là lựa chọn tốt."
        ));
        
        FALLBACK_RESPONSES.put("wedding", Arrays.asList(
            "Chụp cưới ngoài trời nên mang Sony A7IV hoặc Canon R5 + Lens 24-70 f/2.8.",
            "Nên có 2 body phòng khi gặp sự cố kỹ thuật.",
            "Lens 85mm f/1.4 rất phù hợp cho chụp phong cảnh, background blur mượt mà."
        ));
        
        FALLBACK_RESPONSES.put("livestream", Arrays.asList(
            "Sony ZV-E1 được thiết kế riêng cho livestream. Âm thanh tốt, hình ảnh nhất.",
            "GoPro Hero 12 cũng tuyệt vời cho livestream di động.",
            "Nếu chỉ setup cố định, Canon EOS R5 + Lens 24-105 là lựa chọn chuyên nghiệp."
        ));
        
        FALLBACK_RESPONSES.put("dưới 1 triệu", Arrays.asList(
            "Dưới 1 triệu/ngày, bạn có thể thuê Sony A6000 hoặc Canon M50.",
            "Nikon D3500 cũng là lựa chọn tiết kiệm. Chất lượng ảnh tốt, dễ sử dụng.",
            "GoPro Hero 11 giá 200-300k/ngày, phù hợp với ngân sách hạn chế."
        ));
        
        FALLBACK_RESPONSES.put("chân dung", Arrays.asList(
            "Canon EOS R8 + Lens RF 50mm f/1.8 là combo tốt cho chụp chân dung.",
            "Sony A7C nhỏ gọn, dễ mang theo đi đâu. Chất lượng ảnh tuyệt vời.",
            "Lens 85mm f/1.8 cho người thích background blur mượt mà."
        ));
        
        FALLBACK_RESPONSES.put("video", Arrays.asList(
            "Sony A7S III là vua về quay video. Hình ảnh sắc nét trong điều kiện thiếu sáng.",
            "Canon R6 Mark II quay 4K 60fps, AF theo mắt cực kỳ nhanh.",
            "Nếu budget có hạn, Panasonic GH5 vẫn là lựa chọn tốt cho quay video."
        ));
    }

    // ========== CACHE HELPER ==========
    private static class CachedResponse {
        final String reply;
        final long timestamp;
        
        CachedResponse(String reply) {
            this.reply = reply;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    @Override
    public String chat(ChatRequest request) {
        String message = request.getMessage();
        log.info("[AI] Chat request received: {}", message);
        
        if (!geminiConfig.isConfigured()) {
            log.error("[AI] AI service not configured - using fallback");
            return getFallbackResponse(message);
        }

        // ========== CHECK CACHE ==========
        String cacheKey = message.toLowerCase().trim();
        CachedResponse cached = responseCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("[AI] Cache HIT for: {}", message);
            return cached.reply;
        }
        log.info("[AI] Cache MISS for: {}", message);

        // ========== CALL GEMINI WITH RETRY ==========
        try {
            String reply = callGeminiWithRetry(message);
            
            // ========== CACHE SUCCESSFUL RESPONSE ==========
            responseCache.put(cacheKey, new CachedResponse(reply));
            
            return reply;
            
        } catch (AIServiceException e) {
            log.warn("[AI] AIServiceException caught: {} - using fallback", e.getMessage());
            return getFallbackResponse(message);
        } catch (Exception e) {
            log.error("[AI] Unexpected error: {} - using fallback", e.getMessage());
            return getFallbackResponse(message);
        }
    }

    private String callGeminiWithRetry(String userMessage) throws AIServiceException {
        AIServiceException lastException = null;
        
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_DELAYS_MS[attempt - 1];
                    log.info("[AI] Retry attempt {} after {}ms", attempt, delay);
                    TimeUnit.MILLISECONDS.sleep(delay);
                }
                
                log.info("[AI] Calling Gemini (attempt {})", attempt + 1);
                return callGemini(userMessage);
                
            } catch (AIServiceException e) {
                lastException = e;
                
                // Don't retry for certain errors
                if (e.getErrorCode() != null && 
                    (e.getErrorCode().equals("INVALID_API_KEY") || 
                     e.getErrorCode().equals("BLOCKED_CONTENT"))) {
                    log.error("[AI] Non-retryable error: {}", e.getErrorCode());
                    throw e;
                }
                
                log.warn("[AI] Attempt {} failed: {}", attempt + 1, e.getMessage());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[AI] Retry interrupted");
                throw AIServiceException.serviceUnavailable("Request interrupted");
            }
        }
        
        // All retries exhausted
        log.error("[AI] All {} retries exhausted", MAX_RETRIES);
        if (lastException != null) {
            throw lastException;
        }
        throw AIServiceException.serviceUnavailable("Max retries exceeded");
    }

    @Override
    public boolean isConfigured() {
        return geminiConfig.isConfigured();
    }

    private String callGemini(String userMessage) throws AIServiceException {
        Map<String, Object> requestBody = buildRequestBody(userMessage);

        try {
            String apiUrl = geminiConfig.getApiUrl() + "?key=" + geminiConfig.getApiKey();
            log.debug("[AI] Calling Gemini API: {}", geminiConfig.getApiUrl());

            String response = restTemplate.postForObject(apiUrl, requestBody, String.class);
            log.debug("[AI] Gemini response received");

            return parseGeminiResponse(response);
            
        } catch (HttpClientErrorException e) {
            HttpStatusCode statusCode = e.getStatusCode();
            int status = statusCode.value();
            String responseBody = e.getResponseBodyAsString();
            
            log.error("[AI] HTTP Client Error: status={}, body={}", status, responseBody);
            
            if (status == 429) {
                log.warn("[AI] Rate limit hit (429)");
                throw AIServiceException.rateLimitExceeded();
            }
            
            if (status == 400) {
                log.warn("[AI] Bad request (400)");
                throw AIServiceException.serviceUnavailable("Invalid request format");
            }
            
            if (status == 403 || status == 401) {
                log.error("[AI] Authentication error ({}): {}", status, responseBody);
                throw AIServiceException.invalidApiKey();
            }
            
            log.error("[AI] Client error ({}): {}", status, responseBody);
            throw AIServiceException.serviceUnavailable("API client error: " + status);
            
        } catch (HttpServerErrorException e) {
            HttpStatusCode statusCode = e.getStatusCode();
            log.error("[AI] HTTP Server Error: status={}, message={}", statusCode.value(), e.getMessage());
            throw AIServiceException.serviceUnavailable("Gemini server error");
            
        } catch (Exception e) {
            log.error("[AI] Exception calling Gemini: {}", e.getMessage());
            throw AIServiceException.serviceUnavailable(e.getMessage());
        }
    }

    private Map<String, Object> buildRequestBody(String userMessage) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new LinkedHashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        
        Map<String, Object> systemPart = new LinkedHashMap<>();
        systemPart.put("text", SYSTEM_PROMPT);
        parts.add(systemPart);
        
        Map<String, Object> userPart = new LinkedHashMap<>();
        userPart.put("text", userMessage);
        parts.add(userPart);
        
        content.put("parts", parts);
        contents.add(content);
        requestBody.put("contents", contents);
        
        return requestBody;
    }

    private String parseGeminiResponse(String response) throws AIServiceException {
        try {
            JsonNode rootNode = objectMapper.readTree(response);

            JsonNode errorNode = rootNode.get("error");
            if (errorNode != null) {
                String errorMessage = errorNode.path("message").asText("Unknown error");
                log.error("[AI] Gemini API error: {}", errorMessage);
                
                String errorCode = errorNode.path("code").asText("");
                
                if (errorMessage.toLowerCase().contains("quota") || 
                    errorMessage.toLowerCase().contains("limit") ||
                    errorMessage.toLowerCase().contains("rate")) {
                    log.warn("[AI] Quota/rate limit detected in error");
                    throw AIServiceException.quotaExceeded();
                }
                
                if (errorMessage.toLowerCase().contains("blocked")) {
                    throw AIServiceException.blockedContent();
                }
                
                throw AIServiceException.invalidResponse(errorMessage);
            }

            JsonNode candidates = rootNode.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                log.warn("[AI] No candidates in Gemini response");
                throw AIServiceException.invalidResponse("Empty response from AI");
            }

            JsonNode firstCandidate = candidates.get(0);
            
            JsonNode promptFeedback = firstCandidate.get("promptFeedback");
            if (promptFeedback != null) {
                JsonNode blockReason = promptFeedback.get("blockReason");
                if (blockReason != null && !blockReason.isNull()) {
                    String reason = blockReason.asText();
                    log.warn("[AI] Prompt blocked: {}", reason);
                    throw AIServiceException.blockedContent();
                }
            }

            JsonNode content = firstCandidate.get("content");
            if (content == null) {
                log.warn("[AI] No content in Gemini response");
                throw AIServiceException.invalidResponse("Missing content");
            }

            JsonNode parts = content.get("parts");
            if (parts == null || !parts.isArray() || parts.isEmpty()) {
                log.warn("[AI] No parts in Gemini response");
                throw AIServiceException.invalidResponse("No response parts");
            }

            JsonNode firstPart = parts.get(0);
            JsonNode text = firstPart.get("text");
            
            if (text == null || text.isNull()) {
                log.warn("[AI] No text in first part");
                throw AIServiceException.invalidResponse("Empty text response");
            }

            String reply = text.asText().trim();
            
            if (reply.isEmpty()) {
                log.warn("[AI] Empty reply after trim");
                throw AIServiceException.invalidResponse("Empty reply");
            }
            
            if (reply.length() < 5) {
                log.warn("[AI] Suspiciously short reply: {}", reply);
                throw AIServiceException.invalidResponse("Response too short");
            }
            
            reply = cleanResponse(reply);
            
            log.info("[AI] Successfully generated reply, length={}", reply.length());
            return reply;
            
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] Error parsing Gemini response: {}", e.getMessage());
            throw AIServiceException.invalidResponse(e.getMessage());
        }
    }
    
    private String getFallbackResponse(String message) {
        String lowerMessage = message.toLowerCase();
        log.info("[AI] Getting fallback response for: {}", message);
        
        // Try to match keywords
        for (Map.Entry<String, List<String>> entry : FALLBACK_RESPONSES.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                List<String> responses = entry.getValue();
                String response = responses.get(new Random().nextInt(responses.size()));
                log.info("[AI] Using fallback for keyword: {}", entry.getKey());
                return response;
            }
        }
        
        // Default fallback
        String defaultResponse = getDefaultFallbackResponse(lowerMessage);
        log.info("[AI] Using default fallback response");
        return defaultResponse;
    }
    
    private String getDefaultFallbackResponse(String message) {
        List<String> defaults = Arrays.asList(
            "Cảm ơn bạn đã hỏi! Nên ghé qua LensRent để thuê máy ảnh phù hợp với nhu cầu của bạn nhé.",
            "Rất tiếc mình chưa thể trả lời lúc này. Bạn có thể tham khảo các sản phẩm tại LensRent nhé!",
            "Để được tư vấn chi tiết hơn, bạn nên thử thuê 2-3 model rồi so sánh nhé. LensRent có nhiều lựa chọn!",
            "Bạn có thể liên hệ LensRent để được nhân viên tư vấn trực tiếp nhé. Họ sẽ giúp bạn chọn được máy phù hợp."
        );
        return defaults.get(new Random().nextInt(defaults.size()));
    }
    
    private String cleanResponse(String reply) {
        if (reply == null) return "";
        
        // Remove excessive whitespace
        reply = reply.trim().replaceAll("\\s+", " ");
        
        // Remove common AI phrases (without changing diacritics)
        reply = reply.replaceAll("(?i)As an AI.*?\\.", "");
        reply = reply.replaceAll("(?i)I am an AI.*?\\.", "");
        reply = reply.replaceAll("(?i)sorry,?\\s*", "");
        reply = reply.replaceAll("(?i)I'm sorry", "Xin lỗi");
        
        // Clean punctuation
        reply = reply.replaceAll("\\.{3,}", "...");
        reply = reply.replaceAll(",{2,}", ",");
        reply = reply.replaceAll("!{2,}", "!");
        
        return reply.trim();
    }
}

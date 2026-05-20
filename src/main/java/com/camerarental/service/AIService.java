package com.camerarental.service;

import com.camerarental.dto.request.ChatRequest;

public interface AIService {

    /**
     * Gửi tin nhắn tới AI và nhận phản hồi
     *
     * @param request ChatRequest chứa tin nhắn của người dùng
     * @return Phản hồi từ AI
     */
    String chat(ChatRequest request);

    /**
     * Kiểm tra xem AI service có được cấu hình hay không
     *
     * @return true nếu đã được cấu hình, false nếu chưa
     */
    boolean isConfigured();
}

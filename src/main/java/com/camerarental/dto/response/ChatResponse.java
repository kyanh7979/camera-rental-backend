package com.camerarental.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String reply;
    private boolean success;
    private String error;
    private String errorCode;
    private Integer statusCode;

    public static ChatResponse success(String reply) {
        return ChatResponse.builder()
                .reply(reply)
                .success(true)
                .build();
    }

    public static ChatResponse error(String errorMessage) {
        return ChatResponse.builder()
                .error(errorMessage)
                .success(false)
                .build();
    }

    public static ChatResponse error(String errorMessage, String errorCode, int statusCode) {
        return ChatResponse.builder()
                .error(errorMessage)
                .errorCode(errorCode)
                .statusCode(statusCode)
                .success(false)
                .build();
    }
}

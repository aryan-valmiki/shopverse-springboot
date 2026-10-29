package com.shopverse.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationDto {
    private Integer userId;
    private String userName;
    private String userEmail;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer messageCount;
}

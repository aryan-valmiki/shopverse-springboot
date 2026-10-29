package com.shopverse.app.controllers;

import com.shopverse.app.dto.ApiResponse;
import com.shopverse.app.dto.ChatConversationDto;
import com.shopverse.app.dto.SendChatDto;
import com.shopverse.app.models.ChatMessage;
import com.shopverse.app.services.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // Send a message (Customer or Admin)
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(
            @RequestAttribute("userId") Integer senderId,
            @RequestAttribute(value = "isAdmin", required = false) Boolean isAdmin,
            @RequestBody SendChatDto dto
    ) {
        ChatMessage savedMessage = chatService.sendMessage(senderId, isAdmin, dto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Message sent", true, savedMessage));
    }

    // Customer: Get message history with Seller/Admin
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getCustomerMessages(
            @RequestAttribute("userId") Integer userId
    ) {
        List<ChatMessage> messages = chatService.getUserMessages(userId);
        return ResponseEntity.ok(new ApiResponse<>("Messages retrieved", true, messages));
    }

    // Admin: Get all customer conversation threads
    @GetMapping("/admin/conversations")
    public ResponseEntity<ApiResponse<List<ChatConversationDto>>> getAdminConversations() {
        List<ChatConversationDto> conversations = chatService.getAdminConversations();
        return ResponseEntity.ok(new ApiResponse<>("Conversations retrieved", true, conversations));
    }

    // Admin: Get messages for specific customer
    @GetMapping("/admin/messages/{userId}")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getAdminUserMessages(
            @PathVariable Integer userId
    ) {
        List<ChatMessage> messages = chatService.getAdminUserMessages(userId);
        return ResponseEntity.ok(new ApiResponse<>("Messages retrieved", true, messages));
    }
}

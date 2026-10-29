package com.shopverse.app.services;

import com.shopverse.app.dto.ChatConversationDto;
import com.shopverse.app.dto.SendChatDto;
import com.shopverse.app.exceptions.InvalidUserException;
import com.shopverse.app.models.ChatMessage;
import com.shopverse.app.models.User;
import com.shopverse.app.repositories.ChatMessageRepository;
import com.shopverse.app.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public ChatService(ChatMessageRepository chatMessageRepository, UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    public ChatMessage sendMessage(Integer senderId, Boolean isAdmin, SendChatDto dto) {
        if (dto.getMessage() == null || dto.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new InvalidUserException("Sender user not found"));

        Integer threadUserId;
        String role;
        String senderName;

        if (Boolean.TRUE.equals(isAdmin) && dto.getTargetUserId() != null) {
            // Admin replying to customer thread
            threadUserId = dto.getTargetUserId();
            role = "ADMIN";
            senderName = "ShopVerse Seller Support";
        } else {
            // Customer sending to seller/admin
            threadUserId = senderId;
            role = "USER";
            senderName = sender.getName();
        }

        ChatMessage message = ChatMessage.builder()
                .userId(threadUserId)
                .senderId(senderId)
                .senderName(senderName)
                .senderRole(role)
                .message(dto.getMessage().trim())
                .createdAt(LocalDateTime.now())
                .build();

        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getUserMessages(Integer userId) {
        return chatMessageRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public List<ChatMessage> getAdminUserMessages(Integer targetUserId) {
        return chatMessageRepository.findByUserIdOrderByCreatedAtAsc(targetUserId);
    }

    public List<ChatConversationDto> getAdminConversations() {
        List<Integer> userIds = chatMessageRepository.findDistinctConversationUserIds();
        List<ChatConversationDto> result = new ArrayList<>();

        for (Integer uid : userIds) {
            List<ChatMessage> msgs = chatMessageRepository.findByUserIdOrderByCreatedAtAsc(uid);
            if (!msgs.isEmpty()) {
                ChatMessage lastMsg = msgs.get(msgs.size() - 1);
                User user = userRepository.findById(uid).orElse(null);

                result.add(ChatConversationDto.builder()
                        .userId(uid)
                        .userName(user != null ? user.getName() : "Customer #" + uid)
                        .userEmail(user != null ? user.getEmail() : "")
                        .lastMessage(lastMsg.getMessage())
                        .lastMessageTime(lastMsg.getCreatedAt())
                        .messageCount(msgs.size())
                        .build());
            }
        }

        return result;
    }
}

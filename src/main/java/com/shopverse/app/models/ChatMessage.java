package com.shopverse.app.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // The customer conversation thread this message belongs to
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    // ID of the actual person sending the message (customer or admin)
    @Column(name = "sender_id")
    private Integer senderId;

    private String senderName;

    // Role of sender: "USER" or "ADMIN"
    @Column(length = 20)
    private String senderRole;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    private LocalDateTime createdAt;
}

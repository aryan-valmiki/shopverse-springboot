package com.shopverse.app.repositories;

import com.shopverse.app.models.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(Integer userId);

    @Query("SELECT DISTINCT m.userId FROM ChatMessage m ORDER BY m.userId DESC")
    List<Integer> findDistinctConversationUserIds();
}

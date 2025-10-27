package com.shopverse.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shopverse.app.enums.OrderStatus;
import com.shopverse.app.models.Cart;
import com.shopverse.app.models.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private Integer id;
    private Integer userId;
    private String userName;
    private double totalAmount;
    private Integer totalQuantity;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}

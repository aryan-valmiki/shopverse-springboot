package com.shopverse.app.dto;

import com.shopverse.app.models.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartDto {
    private Integer id;
    private Integer userId;
    private List<CartItem> items;
}
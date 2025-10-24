package com.shopverse.app.controllers;

import com.shopverse.app.dto.AddToCartDto;
import com.shopverse.app.dto.ApiResponse;
import com.shopverse.app.dto.CartDto;
import com.shopverse.app.dto.UpdateQuantityDto;
import com.shopverse.app.models.Cart;
import com.shopverse.app.services.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/")
    public ResponseEntity<ApiResponse<CartDto>> addToCart(
            @RequestBody AddToCartDto product,
            @RequestAttribute(name = "userId") Integer userId
            ){
        CartDto cart = cartService.addToCart(product, userId);

        ApiResponse<CartDto> response = new ApiResponse<>("Successfully added", true, cart);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/")
    public ResponseEntity<ApiResponse<CartDto>> getCart(@RequestAttribute("userId") Integer userId){
        CartDto cart = cartService.getCart(userId);

        ApiResponse<CartDto> response = new ApiResponse<>("cart", true, cart);

        return ResponseEntity
                .ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> removeItemInCart(
            @PathVariable Integer id,
            @RequestAttribute("userId") Integer userId
    ){
        Boolean deletedItem = cartService.removeItem(id, userId);

        ApiResponse<String> response = new ApiResponse<>("removed successfully", deletedItem, "No data");

        return ResponseEntity
                .ok(response);
    }

    @PatchMapping("/")
    public ResponseEntity<ApiResponse<String>> updateQuantity(
            @RequestBody UpdateQuantityDto cartItem,
            @RequestAttribute("userId") Integer userId
    ){
        Boolean updatedItem = cartService.updateQuantity(cartItem, userId);

        ApiResponse<String> response = new ApiResponse<>("Quantity updated successfully", updatedItem, "No data");

        return ResponseEntity
                .ok(response);
    }
}

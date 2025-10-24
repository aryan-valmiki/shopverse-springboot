package com.shopverse.app.services;

import com.shopverse.app.dto.AddToCartDto;
import com.shopverse.app.dto.CartDto;
import com.shopverse.app.dto.UpdateQuantityDto;
import com.shopverse.app.exceptions.InvalidCartException;
import com.shopverse.app.exceptions.InvalidProductException;
import com.shopverse.app.exceptions.InvalidUserException;
import com.shopverse.app.models.Cart;
import com.shopverse.app.models.CartItem;
import com.shopverse.app.models.Product;
import com.shopverse.app.models.User;
import com.shopverse.app.repositories.CartItemRepository;
import com.shopverse.app.repositories.CartRepository;
import com.shopverse.app.repositories.ProductRepository;
import com.shopverse.app.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository, UserRepository userRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public CartDto addToCart(AddToCartDto product, Integer userId) {
        if (product.getProductId() == null){
            throw new InvalidProductException("Product Id is missing");
        }
        if (product.getQuantity() == null || product.getQuantity() == 0){
            throw new InvalidProductException("Quantity cannot be 0");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));

        Product savedProduct = productRepository.findById(product.getProductId())
                .orElseThrow(() -> new InvalidProductException("Product not found"));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        Optional<CartItem> existingCartItem = cart.getCartItems()
                .stream()
                .filter((item) -> item.getProduct().getId().equals(savedProduct.getId()))
                .findFirst();

        if (existingCartItem.isPresent()){
            CartItem cartItem = existingCartItem.get();
            cartItem.setQuantity(product.getQuantity());
        } else {
            CartItem newCartItem = new CartItem();
            newCartItem.setCart(cart);
            newCartItem.setProduct(savedProduct);
            newCartItem.setQuantity(product.getQuantity());
            cart.getCartItems().add(newCartItem);
        }

        Cart savedCart = cartRepository.save(cart);

        return CartDto.builder()
                 .id(savedCart.getId())
                 .userId(savedCart.getUser().getId())
                 .items(savedCart.getCartItems())
                 .build();
    }

    public CartDto getCart(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        List<CartItem> cartItem = cart.getCartItems() == null
                ? List.of()
                : cart.getCartItems();

        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(cartItem)
                .build();
    }

    public Boolean removeItem(Integer cartItemId, Integer userId) {
        if (cartItemId == null){
            throw new InvalidProductException("Product Id is needed");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCartException("Cart not found"));

        if (cart.getCartItems().isEmpty()){
            throw new InvalidCartException("cart is empty");
        }

        CartItem cartItem = cart.getCartItems()
                .stream()
                .filter((item) -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new InvalidCartException("No cart item exists"));

        cart.getCartItems().remove(cartItem);
        cartRepository.save(cart);
        return true;
    }

    public Boolean updateQuantity(UpdateQuantityDto product, Integer userId) {
        if (product.getCartId() == null){
            throw new InvalidCartException("Cart item id is missing");
        }
        if (product.getQuantity() == 0){
            throw new InvalidCartException("Quantity should be greater than 0");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCartException("No cart"));

        if (cart.getCartItems().isEmpty()){
            throw new InvalidCartException("Cart is empty");
        }

        CartItem cartItem = cart.getCartItems()
                .stream()
                .filter((item) -> item.getId().equals(product.getCartId()))
                .findFirst()
                .orElseThrow(() -> new InvalidCartException("No item exists"));

        cartItem.setQuantity(product.getQuantity());
        cartRepository.save(cart);
        return true;
    }
}

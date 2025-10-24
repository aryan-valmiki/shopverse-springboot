package com.shopverse.app.services;

import com.shopverse.app.dto.OrderDto;
import com.shopverse.app.enums.OrderStatus;
import com.shopverse.app.exceptions.InvalidCartException;
import com.shopverse.app.exceptions.InvalidOrderException;
import com.shopverse.app.exceptions.InvalidUserException;
import com.shopverse.app.models.Cart;
import com.shopverse.app.models.CartItem;
import com.shopverse.app.models.Order;
import com.shopverse.app.models.User;
import com.shopverse.app.repositories.CartRepository;
import com.shopverse.app.repositories.OrderRepository;
import com.shopverse.app.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrderDto createOrder(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("No user found"));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCartException("No cart"));

        if (cart.getCartItems() == null || cart.getCartItems().isEmpty()){
            throw new InvalidCartException("Cart is empty");
        }

        int totalQuantity = cart.getCartItems()
                .stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        double totalAmount = cart.getCartItems()
                .stream()
                .mapToDouble((item) -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        Order order = Order.builder()
                .user(user)
                .cart(cart)
                .totalAmount(totalAmount)
                .totalQuantity(totalQuantity)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();


        Order createdOrder = orderRepository.save(order);

        cart.getCartItems().clear();
        cartRepository.save(cart);

        return OrderDto.builder()
                .id(createdOrder.getId())
                .userId(createdOrder.getUser().getId())
                .totalAmount(createdOrder.getTotalAmount())
                .totalQuantity(createdOrder.getTotalQuantity())
                .status(createdOrder.getStatus().name())
                .createdAt(createdOrder.getCreatedAt())
                .build();

    }

    public List<OrderDto> findOrderById(Integer id) {
        List<Order> orders = orderRepository.findByUserId(id);

        if (orders.isEmpty()) {
            throw new InvalidOrderException("No order found");
        }

        return orders.stream()
                .map(item -> OrderDto.builder()
                        .id(item.getId())
                        .userId(item.getUser().getId())
                        .totalAmount(item.getTotalAmount())
                        .totalQuantity(item.getTotalQuantity())
                        .status(item.getStatus().name())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    public List<OrderDto> getAllOrders() {
        List<Order> allOrders = orderRepository.findAll();

        if (allOrders.isEmpty()){
            throw new InvalidOrderException("No Orders");
        }

        return allOrders.stream()
                .map((item) -> {
                    return OrderDto.builder()
                            .id(item.getId())
                            .userId(item.getUser().getId())
                            .totalAmount(item.getTotalAmount())
                            .totalQuantity(item.getTotalQuantity())
                            .status(item.getStatus().name())
                            .createdAt(item.getCreatedAt())
                            .build();
                })
                .toList();
    }
}

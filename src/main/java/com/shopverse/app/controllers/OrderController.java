package com.shopverse.app.controllers;

import com.shopverse.app.dto.ApiResponse;
import com.shopverse.app.dto.OrderDto;
import com.shopverse.app.services.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }


    //public routes
    @PostMapping("/")
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(
            @RequestAttribute("userId") Integer id
    ){
        OrderDto createdOrder = orderService.createOrder(id);

        ApiResponse<OrderDto> response = new ApiResponse<>("Order created successfully", true, createdOrder);

        return ResponseEntity
                .ok(response);
    }

    @GetMapping("/")
    public ResponseEntity<ApiResponse<List<OrderDto>>> findOrderById(
            @RequestAttribute("userId") Integer id
    ){
        List<OrderDto> orders = orderService.findOrderById(id);

        ApiResponse<List<OrderDto>> response = new ApiResponse<>("orders", true, orders);

        return ResponseEntity
                .ok(response);

    }

    //admin route
    @GetMapping("/admin/")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getAllOrders(){
        List<OrderDto> orders = orderService.getAllOrders();

        ApiResponse<List<OrderDto>> response = new ApiResponse<>("orders", true, orders);

        return ResponseEntity
                .ok(response);
    }

}

package com.shopverse.app.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Integer id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Double price;
    private Integer stock;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    private String imagePublicId;

    // Specifications for AI recommendation (simple & clean for college project)
    private String category;
    private String brand;
    private String ram;
    private String processor;
    private String storage;

    // Product status: ACTIVE, INACTIVE, OUT_OF_STOCK
    @Builder.Default
    @Column(length = 50)
    private String status = "ACTIVE";

    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }
}

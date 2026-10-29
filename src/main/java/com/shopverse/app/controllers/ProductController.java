package com.shopverse.app.controllers;

import com.shopverse.app.dto.ApiResponse;
import com.shopverse.app.models.Product;
import com.shopverse.app.services.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/product")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService){
        this.productService = productService;
    }

    // admin protected route - Add Product
    @PostMapping("/admin/add")
    public ResponseEntity<ApiResponse<Product>> addProduct(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "ram", required = false) String ram,
            @RequestParam(value = "processor", required = false) String processor,
            @RequestParam(value = "storage", required = false) String storage
    ){
        Product createdProduct = productService.addProduct(
                name, description, price, stock, image, category, brand, ram, processor, storage
        );

        ApiResponse<Product> response = new ApiResponse<>("product created", true, createdProduct);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // admin protected route - Update Product Status (ACTIVE, INACTIVE, OUT_OF_STOCK)
    @PatchMapping("/admin/{id}/status")
    public ResponseEntity<ApiResponse<Product>> updateProductStatus(
            @PathVariable Integer id,
            @RequestParam("status") String status
    ){
        Product updatedProduct = productService.updateProductStatus(id, status);

        ApiResponse<Product> response = new ApiResponse<>("Product status updated to " + updatedProduct.getStatus(), true, updatedProduct);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    // admin protected route - Delete Product
    @DeleteMapping("/admin/delete/{id}")
    public ResponseEntity<ApiResponse<String>> deleteProduct(@PathVariable Integer id){
        Boolean isDeleted = productService.deleteProduct(id);

        ApiResponse<String> response = new ApiResponse<>("product deleted", isDeleted, "nothing");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    // public route - Get All Products (with optional sorting)
    @GetMapping("/")
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts(
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false, defaultValue = "asc") String sortDir
    ){
        List<Product> products = productService.getAllProducts(sortBy, sortDir);

        ApiResponse<List<Product>> response = new ApiResponse<>("All products", true, products);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    // public route - Get Product by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Integer id){
        Product product = productService.getProductById(id);

        ApiResponse<Product> response = new ApiResponse<>("product", true, product);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}

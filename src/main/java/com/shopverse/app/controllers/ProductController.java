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


    // admin protected route
    @PostMapping("/admin/add")
    public ResponseEntity<ApiResponse<Product>> addProduct(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("stock") Integer stock,
            @RequestParam("image")MultipartFile image
            ){
        Product createdProduct = productService.addProduct(name, description, price, stock, image);

        ApiResponse<Product> response = new ApiResponse<>("product created", true, createdProduct);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/admin/delete/{id}")
    public ResponseEntity<ApiResponse<String>> deleteProduct(@PathVariable Integer id){
        Boolean isDeleted = productService.deleteProduct(id);

        ApiResponse<String> response = new ApiResponse<>("product deleted", isDeleted, "nothing");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }


    //public routes
    @GetMapping("/")
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts(){
        List<Product> products = productService.getAllProducts();

        ApiResponse<List<Product>> response = new ApiResponse<>("All products", true, products);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Integer id){
        System.out.println(id);
        Product product = productService.getProductById(id);

        ApiResponse<Product> response = new ApiResponse<>("product", true, product);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}

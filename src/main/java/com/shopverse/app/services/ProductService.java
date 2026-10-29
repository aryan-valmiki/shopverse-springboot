package com.shopverse.app.services;

import com.shopverse.app.exceptions.InvalidIoException;
import com.shopverse.app.exceptions.InvalidProductException;
import com.shopverse.app.models.Product;
import com.shopverse.app.repositories.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CloudinaryService cloudinaryService;

    public ProductService(ProductRepository productRepository, CloudinaryService cloudinaryService){
        this.productRepository = productRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public Product addProduct(String name, String description, Double price, Integer stock, MultipartFile image){
        return addProduct(name, description, price, stock, image, null, null, null, null, null);
    }

    public Product addProduct(String name, String description, Double price, Integer stock, MultipartFile image,
                              String category, String brand, String ram, String processor, String storage){
        if (name == null || name.trim().isEmpty()){
            throw new InvalidProductException("name is required");
        }
        if (description == null || description.trim().isEmpty()){
            throw new InvalidProductException("description is required");
        }
        if (price == null || price == 0){
            throw new InvalidProductException("price is required");
        }
        if (stock == null || stock == 0){
            throw new InvalidProductException("stock is required");
        }
        if (image == null || image.isEmpty()){
            throw new InvalidProductException("image is required");
        }

        Map<String, Object> uploadResponse = cloudinaryService.uploadImage(image);
        String imageUrlFromCloudinary = uploadResponse.get("secure_url").toString();
        String publicId = uploadResponse.get("public_id").toString();

        Product product = Product.builder()
                .name(name.trim())
                .description(description.trim())
                .price(price)
                .stock(stock)
                .imageUrl(imageUrlFromCloudinary)
                .imagePublicId(publicId)
                .category(category != null ? category.trim() : null)
                .brand(brand != null ? brand.trim() : null)
                .ram(ram != null ? ram.trim() : null)
                .processor(processor != null ? processor.trim() : null)
                .storage(storage != null ? storage.trim() : null)
                .status("ACTIVE")
                .build();

        return productRepository.save(product);
    }

    public Boolean deleteProduct(Integer id){
        if (id == null) {
            throw new InvalidProductException("Id not found");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new InvalidProductException("Product not exists"));

        cloudinaryService.deleteImage(product.getImagePublicId());

        productRepository.deleteById(id);
        return true;
    }

    public List<Product> getAllProducts(){
        return productRepository.findAll();
    }

    public List<Product> getAllProducts(String sortBy, String sortDir){
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return productRepository.findAll();
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return productRepository.findAll(Sort.by(direction, sortBy));
    }

    public Product getProductById(Integer id){
        if (id == null){
            throw new InvalidProductException("Id not found");
        }

        Optional<Product> product = productRepository.findById(id);

        return product
                .orElseThrow(() -> new InvalidProductException("Product not exists"));
    }

    /**
     * Admin method to update product status (ACTIVE, INACTIVE, OUT_OF_STOCK)
     */
    public Product updateProductStatus(Integer id, String status) {
        if (id == null) {
            throw new InvalidProductException("Product ID cannot be null");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new InvalidProductException("Product not found with ID: " + id));

        String normalizedStatus = (status != null && !status.trim().isEmpty())
                ? status.trim().toUpperCase()
                : "ACTIVE";

        product.setStatus(normalizedStatus);
        return productRepository.save(product);
    }
}

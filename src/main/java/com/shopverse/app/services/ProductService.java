package com.shopverse.app.services;

import com.shopverse.app.exceptions.InvalidIoException;
import com.shopverse.app.exceptions.InvalidProductException;
import com.shopverse.app.models.Product;
import com.shopverse.app.repositories.ProductRepository;
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
        if (name.trim().isEmpty()){
            throw new InvalidProductException("name is required");
        }
        if (description.trim().isEmpty()){
            throw new InvalidProductException("description is required");
        }
        if (price == 0){
            throw new InvalidProductException("price is required");
        }
        if (stock == 0){
            throw new InvalidProductException("stock is required");
        }
        if (image.isEmpty()){
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

    public Product getProductById(Integer id){
        if (id == null){
            throw new InvalidProductException("Id not found");
        }

        Optional<Product> product = productRepository.findById(id);

        return product
                .orElseThrow(() -> new InvalidProductException("Product not exists"));

    }


}

package com.shopverse.app.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.shopverse.app.exceptions.InvalidIoException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public Map<String, Object> uploadImage(MultipartFile image) {
        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    image.getBytes(),
                    ObjectUtils.asMap("folder", "shopverse2")
            );
            return uploadResult;
        } catch (IOException e) {
            throw new InvalidIoException(e.getMessage());
        }
    }


    public void deleteImage(String publicId){
        try {
            cloudinary.uploader()
                    .destroy(
                            publicId, ObjectUtils.emptyMap()
                    );

        } catch (IOException e) {
            throw new InvalidIoException(e.getMessage());
        }

    }

}

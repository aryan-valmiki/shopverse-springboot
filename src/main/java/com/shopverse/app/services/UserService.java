package com.shopverse.app.services;

import com.shopverse.app.dto.UserDto;
import com.shopverse.app.exceptions.InvalidUserException;
import com.shopverse.app.models.User;
import com.shopverse.app.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    public UserDto registerUser(User user){
        if (user.getName() == null || user.getName().trim().isEmpty()){
            throw new InvalidUserException("name field is required");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()){
            throw new InvalidUserException("email field is required");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()){
            throw new InvalidUserException("password field is required");
        }

        Optional<User> existingUser = userRepository.findByEmail(user.getEmail().trim());

        if (existingUser.isPresent()){
            throw new InvalidUserException("user already exists");
        }

        user.setName(user.getName().trim());
        user.setEmail(user.getEmail().trim());
        user.setPassword(user.getPassword().trim());
        user.setIsAdmin(false);

        User createdUser = userRepository.save(user);

        return UserDto
                .builder()
                .id(createdUser.getId())
                .name(createdUser.getName())
                .email(createdUser.getEmail())
                .isAdmin(createdUser.getIsAdmin())
                .build();
    }

    public UserDto loginUser(User user){
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()){
            throw new InvalidUserException("email field is required");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()){
            throw new InvalidUserException("password field is required");
        }

        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());

        User alreadyExists = existingUser
                .orElseThrow(() -> new InvalidUserException("User not exists"));

        if (!alreadyExists.getPassword().equals(user.getPassword())){
            throw new InvalidUserException("Invalid password");
        }

        return UserDto.builder()
                .id(alreadyExists.getId())
                .name(alreadyExists.getName())
                .email(alreadyExists.getEmail())
                .isAdmin(alreadyExists.getIsAdmin())
                .build();
    }
}

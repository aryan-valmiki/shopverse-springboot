package com.shopverse.app.controllers;

import com.shopverse.app.dto.ApiResponse;
import com.shopverse.app.dto.UserDto;
import com.shopverse.app.models.User;
import com.shopverse.app.services.UserService;
import com.shopverse.app.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil){
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDto>> register(@RequestBody User user){
        UserDto createdUser = userService.registerUser(user);
        ApiResponse<UserDto> response = ApiResponse.<UserDto>builder()
                .message("User created successfully")
                .success(true)
                .data(createdUser)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDto>> login(@RequestBody User user){
        UserDto existUser = userService.loginUser(user);
        String token = jwtUtil.generateToken(existUser.getId(), existUser.getEmail(), existUser.getIsAdmin());

        ResponseCookie cookie = ResponseCookie.from("jwtToken", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(3600)
                .build();

        ApiResponse<UserDto> response = new ApiResponse<>("Login successfully", true, existUser);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header("Set-Cookie", cookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(){
        ResponseCookie cookie = ResponseCookie.from("jwtToken", null)
                .httpOnly(true)
                .secure(true)           
                .path("/")
                .sameSite("None")       
                .maxAge(0)              
                .build();

        ApiResponse<String> response = new ApiResponse<>("logout successfully", true, "no data");

        return ResponseEntity
                .ok()
                .header("Set-Cookie", cookie.toString())
                .body(response);
    }
}

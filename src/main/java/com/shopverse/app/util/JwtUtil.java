package com.shopverse.app.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    private final String secret;
    private final long expirationTime;

    public JwtUtil(
            @Value("${jwt.expiration}") long expirationTime,
            @Value("${jwt.secret}") String secret
    ){
        this.expirationTime = expirationTime;
        this.secret = secret;
    }

    public String generateToken(Integer userId, String email, Boolean isAdmin){
        return Jwts.builder()
                .setSubject(email)
                .claim("isAdmin", isAdmin)
                .claim("userId", userId)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(expirationTime)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                    .build()
                    .parseClaimsJws(token);

            return true;
        } catch (Exception e){
            System.out.println(e.getMessage());
            return false;
        }
    }

    public Claims extractPayload(String token){
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

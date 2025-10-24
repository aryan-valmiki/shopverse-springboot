package com.shopverse.app.interceptors;

import com.shopverse.app.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil){
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        try {
            String token = null;
            if (request.getCookies() == null || request.getCookies().length == 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("No cookies");
                return false;
            }

            for (Cookie c : request.getCookies()) {
                if ("jwtToken".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }

            if (token == null){
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Missing Jwt token");
                return false;
            }

            if (!jwtUtil.validateToken(token)){
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized request");
                return false;
            }

            Claims payload = jwtUtil.extractPayload(token);

            request.setAttribute("userId", payload.get("userId", Integer.class));
            request.setAttribute("isAdmin", payload.get("isAdmin", Boolean.class));
            request.setAttribute("email", payload.get("email", String.class));

            return true;

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired token");
            return false;
        }
    }
}

package com.shopverse.app.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        Boolean isAdmin = (Boolean) request.getAttribute("isAdmin");

        if (!Boolean.TRUE.equals(isAdmin)){
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Admin route");
            return false;
        }

        return true;
    }
}

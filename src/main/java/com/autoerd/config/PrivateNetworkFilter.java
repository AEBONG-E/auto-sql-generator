package com.autoerd.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Chrome 130+ Private Network Access (PNA) 정책 대응 필터
 * — localhost fetch() 요청에 대해 OPTIONS 사전 확인(preflight)을 정상 처리하고
 *   Access-Control-Allow-Private-Network: true 헤더를 모든 응답에 포함
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivateNetworkFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String origin = req.getHeader("Origin");
        String requestedMethod = req.getHeader("Access-Control-Request-Method");
        String requestedHeaders = req.getHeader("Access-Control-Request-Headers");

        addVaryHeaders(res);
        if (origin != null && !origin.isBlank()) {
            res.setHeader("Access-Control-Allow-Origin", origin);
        }
        res.setHeader("Access-Control-Allow-Private-Network", "true");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            res.setHeader("Access-Control-Allow-Methods", buildAllowedMethods(requestedMethod));
            res.setHeader("Access-Control-Allow-Headers", buildAllowedHeaders(requestedHeaders));
            res.setHeader("Access-Control-Max-Age", "600");
            res.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }

    private void addVaryHeaders(HttpServletResponse res) {
        res.addHeader("Vary", "Origin");
        res.addHeader("Vary", "Access-Control-Request-Method");
        res.addHeader("Vary", "Access-Control-Request-Headers");
        res.addHeader("Vary", "Access-Control-Request-Private-Network");
    }

    private String buildAllowedMethods(String requestedMethod) {
        if (requestedMethod == null || requestedMethod.isBlank()) {
            return "GET, POST, OPTIONS";
        }
        return requestedMethod + ", OPTIONS";
    }

    private String buildAllowedHeaders(String requestedHeaders) {
        if (requestedHeaders == null || requestedHeaders.isBlank()) {
            return "Content-Type, Accept";
        }
        return requestedHeaders;
    }
}

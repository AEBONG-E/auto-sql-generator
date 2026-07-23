package com.autoerd.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    /**
     * React(Vite) 빌드 산출물이 classpath:/static/index.html로 배치된다.
     * "/"만 매핑하고 "/index.html"은 매핑하지 않아야 forward 시
     * 정적 리소스 핸들러가 그대로 서빙한다 (자기 자신으로 forward하면 무한 루프).
     */
    @GetMapping("/")
    public String index(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        return "forward:/index.html";
    }
}

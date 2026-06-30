package com.autoerd.controller;

import com.autoerd.service.OllamaSqlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/sql")
@RequiredArgsConstructor
public class SqlGenerationController {

    private final OllamaSqlService ollamaSqlService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generate(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "").trim();
        if (query.isEmpty()) {
            return Flux.just("-- 질문을 입력해 주세요.");
        }
        log.info("SQL generation request: {}", query);
        return ollamaSqlService.generateSqlStream(query);
    }
}

package com.autoerd.controller.v1;

import com.autoerd.application.sql.usecase.GenerateProjectSqlCommand;
import com.autoerd.application.sql.usecase.GenerateProjectSqlUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects/{projectId}/sql")
@RequiredArgsConstructor
public class ProjectSqlGenerationController {

    private final GenerateProjectSqlUseCase generateProjectSqlUseCase;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateSql(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> body) {

        String query = body.getOrDefault("query", "").trim();
        if (query.isEmpty()) {
            return Flux.just("-- 질문을 입력해 주세요.");
        }

        log.info("SQL 생성 요청: projectId={}, query={}", projectId, query);
        return generateProjectSqlUseCase.generateSql(new GenerateProjectSqlCommand(projectId, query));
    }
}

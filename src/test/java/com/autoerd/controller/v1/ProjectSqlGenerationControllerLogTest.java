package com.autoerd.controller.v1;

import com.autoerd.application.sql.usecase.GenerateProjectSqlUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ProjectSqlGenerationControllerLogTest {

    private static final String QUERY = "LOGCHECK_질의원문_최근30일_취소주문";

    @Test
    @DisplayName("SQL 생성 요청 로그에 질의 원문 대신 projectId 와 질의 길이만 남긴다")
    void generateSql_doesNotLogQueryText(CapturedOutput output) {
        GenerateProjectSqlUseCase useCase = mock(GenerateProjectSqlUseCase.class);
        when(useCase.generateSql(any())).thenReturn(Flux.just("SELECT 1;"));
        ProjectSqlGenerationController controller = new ProjectSqlGenerationController(useCase);

        controller.generateSql(7L, Map.of("query", QUERY)).blockLast();

        assertThat(output).contains("SQL 생성 요청: projectId=7, queryLength=" + QUERY.length());
        assertThat(output).doesNotContain(QUERY);
    }
}

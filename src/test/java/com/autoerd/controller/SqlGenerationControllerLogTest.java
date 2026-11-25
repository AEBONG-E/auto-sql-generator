package com.autoerd.controller;

import com.autoerd.service.OllamaSqlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SqlGenerationControllerLogTest {

    private static final String QUERY = "LOGCHECK_질의원문_회원별_매출합계";

    @Test
    @DisplayName("세션 기반 SQL 생성 요청 로그에 질의 원문을 남기지 않는다")
    void generate_doesNotLogQueryText(CapturedOutput output) {
        OllamaSqlService service = mock(OllamaSqlService.class);
        when(service.generateSqlStream(anyString())).thenReturn(Flux.just("SELECT 1;"));
        SqlGenerationController controller = new SqlGenerationController(service);

        controller.generate(Map.of("query", QUERY)).blockLast();

        assertThat(output).contains("SQL generation request: queryLength=" + QUERY.length());
        assertThat(output).doesNotContain(QUERY);
    }
}

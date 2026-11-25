package com.autoerd.application.sql.service;

import com.autoerd.application.sql.usecase.GenerateProjectSqlCommand;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.domain.sql.port.SqlGenerationPort;
import com.autoerd.domain.sql.port.SqlMetadataProvider;
import com.autoerd.domain.sql.port.SqlTableResolutionException;
import com.autoerd.domain.sql.port.SqlTableResolverPort;
import com.autoerd.model.TableSchema;
import com.autoerd.service.TableCandidatePrefilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class GenerateProjectSqlServiceTest {

    private final SqlMetadataProvider metadataProvider = mock(SqlMetadataProvider.class);
    private final SqlTableResolverPort tableResolver = mock(SqlTableResolverPort.class);
    private final SqlGenerationPort sqlGenerationPort = mock(SqlGenerationPort.class);
    private final TableCandidatePrefilter prefilter = mock(TableCandidatePrefilter.class);
    private final GenerateProjectSqlService service =
            new GenerateProjectSqlService(metadataProvider, tableResolver, sqlGenerationPort, prefilter);

    private final GenerateProjectSqlCommand command = new GenerateProjectSqlCommand(1L, "주문 목록");

    @BeforeEach
    void setUp() {
        List<TableSchema> tables = List.of(TableSchema.builder().tableName("orders").columns(List.of()).build());
        when(metadataProvider.getCurrentSnapshot(1L)).thenReturn(new SqlMetadataSnapshot(tables, List.of()));
        when(prefilter.prefilter(anyString(), anyList(), anyList())).thenReturn(tables);
    }

    @Test
    void Step1_LLM_호출이_실패하면_llm_error를_응답하고_insufficient_schema로_보이지_않는다() {
        when(tableResolver.resolveRelevantTableNames(any()))
                .thenThrow(new SqlTableResolutionException("fail", new RuntimeException("timeout")));

        String body = String.join("", service.generateSql(command).collectList().block());

        assertThat(body).startsWith("-- llm_error:");
        assertThat(body).doesNotContain("insufficient_schema");
        verifyNoInteractions(sqlGenerationPort);
    }

    @Test
    void 관련_테이블이_없으면_기존대로_insufficient_schema를_응답한다() {
        when(tableResolver.resolveRelevantTableNames(any())).thenReturn(List.of());

        String body = String.join("", service.generateSql(command).collectList().block());

        assertThat(body).startsWith("-- insufficient_schema:");
        verifyNoInteractions(sqlGenerationPort);
    }

    @Test
    @DisplayName("Step1 요청에 후보 테이블의 설명이 테이블명 기준으로 담긴다 (설명 없는 테이블은 제외)")
    void generateSql_passesCandidateDescriptionsToTableResolver() {
        SqlMetadataProvider metadataProvider = mock(SqlMetadataProvider.class);
        SqlTableResolverPort tableResolver = mock(SqlTableResolverPort.class);
        SqlGenerationPort sqlGenerationPort = mock(SqlGenerationPort.class);
        TableCandidatePrefilter prefilter = mock(TableCandidatePrefilter.class);

        List<TableSchema> tables = List.of(
                TableSchema.builder().tableName("tb_order").tableDescription("주문").columns(List.of()).build(),
                TableSchema.builder().tableName("tb_log").columns(List.of()).build());
        when(metadataProvider.getCurrentSnapshot(1L)).thenReturn(new SqlMetadataSnapshot(tables, List.of()));
        when(prefilter.prefilter(anyString(), anyList(), anyList())).thenReturn(tables);
        when(tableResolver.resolveRelevantTableNames(any())).thenReturn(List.of());

        GenerateProjectSqlService service =
                new GenerateProjectSqlService(metadataProvider, tableResolver, sqlGenerationPort, prefilter);

        service.generateSql(new GenerateProjectSqlCommand(1L, "주문 목록")).blockLast();

        ArgumentCaptor<SqlTableResolutionRequest> captor = ArgumentCaptor.forClass(SqlTableResolutionRequest.class);
        verify(tableResolver).resolveRelevantTableNames(captor.capture());
        SqlTableResolutionRequest request = captor.getValue();
        assertThat(request.candidateTableNames()).containsExactly("tb_order", "tb_log");
        assertThat(request.candidateTableDescriptions())
                .containsEntry("tb_order", "주문")
                .doesNotContainKey("tb_log");
    }

    @Test
    @DisplayName("Step1 성공 로그는 projectId, 선별 테이블, 소요 시간만 남기고 질의 원문은 남기지 않는다")
    void generateSql_success_doesNotLogQueryText(CapturedOutput output) {
        when(tableResolver.resolveRelevantTableNames(any())).thenReturn(List.of("orders"));
        when(sqlGenerationPort.generate(any())).thenReturn(Flux.just("SELECT 1;"));

        service.generateSql(command).blockLast();

        assertThat(output).containsPattern("관련 테이블 \\[orders\\] 해결됨: projectId=1, step1ElapsedMs=\\d+");
        assertThat(output).doesNotContain("주문 목록");
    }

    @Test
    @DisplayName("Step1 실패 로그는 projectId, 소요 시간, 원인 종류를 남기고 질의 원문은 남기지 않는다")
    void generateSql_step1Failure_logsCauseWithoutQueryText(CapturedOutput output) {
        when(tableResolver.resolveRelevantTableNames(any()))
                .thenThrow(new SqlTableResolutionException("fail", new IllegalStateException("timeout")));

        service.generateSql(command).blockLast();

        assertThat(output).containsPattern(
                "Step1 테이블 판별 실패로 SQL 생성 중단: projectId=1, elapsedMs=\\d+, cause=java.lang.IllegalStateException");
        assertThat(output).doesNotContain("주문 목록");
    }
}

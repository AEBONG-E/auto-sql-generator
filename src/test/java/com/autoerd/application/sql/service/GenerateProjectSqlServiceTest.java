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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
}

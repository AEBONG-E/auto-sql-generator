package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.infrastructure.assembler.MetadataDomainAssembler;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataColumnRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataRelationRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step2 까지 프로젝트 방언을 넘기기 위해 메타데이터 스냅샷에 방언을 담는다 (이슈 #18).
 */
class JpaSqlMetadataProviderAdapterDialectTest {

    private final MetadataTableRepository tableRepository = mock(MetadataTableRepository.class);
    private final MetadataProjectRepository projectRepository = mock(MetadataProjectRepository.class);
    private final JpaSqlMetadataProviderAdapter adapter = new JpaSqlMetadataProviderAdapter(
            tableRepository, mock(MetadataColumnRepository.class), mock(MetadataRelationRepository.class),
            projectRepository, new MetadataDomainAssembler());

    @Test
    @DisplayName("프로젝트에 저장된 방언을 스냅샷에 담는다")
    void snapshot_carriesProjectDialect() {
        when(tableRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(9L))
                .thenReturn(List.of());
        when(projectRepository.findById(9L)).thenReturn(Optional.of(
                MetadataProjectEntity.builder().projectKey("k").projectName("n").sqlDialect(SqlDialect.MSSQL).build()));

        SqlMetadataSnapshot snapshot = adapter.getCurrentSnapshot(9L);

        assertThat(snapshot.dialect()).isEqualTo(SqlDialect.MSSQL);
    }

    @Test
    @DisplayName("프로젝트를 찾지 못하면 1단계 기본값 MYSQL 을 쓴다")
    void snapshot_missingProject_defaultsToMysql() {
        when(tableRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(9L))
                .thenReturn(List.of());
        when(projectRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(adapter.getCurrentSnapshot(9L).dialect()).isEqualTo(SqlDialect.MYSQL);
    }
}

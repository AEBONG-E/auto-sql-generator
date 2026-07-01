package com.autoerd.infrastructure.persistence.jpa.adapter;

import com.autoerd.domain.metadata.repository.MetadataTableRepositoryPort;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaMetadataTableRepositoryAdapter implements MetadataTableRepositoryPort {

    private final MetadataTableRepository tableRepository;

    @Override
    public List<Long> findCurrentTableIdsByProjectId(Long projectId) {
        return tableRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(projectId)
                .stream().map(e -> e.getId()).toList();
    }

    @Override
    public Optional<Long> findCurrentTableId(Long projectId, String schemaName, String tableName) {
        return tableRepository.findByProjectIdAndSchemaNameAndTableNameAndIsCurrentTrueAndIsDeletedFalse(
                        projectId, schemaName, tableName)
                .map(e -> e.getId());
    }

    @Override
    public boolean existsCurrentTable(Long projectId, String schemaName, String tableName) {
        return tableRepository.findByProjectIdAndSchemaNameAndTableNameAndIsCurrentTrueAndIsDeletedFalse(
                projectId, schemaName, tableName).isPresent();
    }
}

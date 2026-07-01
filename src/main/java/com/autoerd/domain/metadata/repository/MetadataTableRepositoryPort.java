package com.autoerd.domain.metadata.repository;

import java.util.List;
import java.util.Optional;

public interface MetadataTableRepositoryPort {

    List<Long> findCurrentTableIdsByProjectId(Long projectId);

    Optional<Long> findCurrentTableId(Long projectId, String schemaName, String tableName);

    boolean existsCurrentTable(Long projectId, String schemaName, String tableName);
}

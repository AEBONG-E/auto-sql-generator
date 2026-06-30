package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataTableRepository extends JpaRepository<MetadataTableEntity, Long> {

    List<MetadataTableEntity> findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(
            Long projectId
    );

    Optional<MetadataTableEntity> findByProjectIdAndSchemaNameAndTableNameAndIsCurrentTrueAndIsDeletedFalse(
            Long projectId,
            String schemaName,
            String tableName
    );

    List<MetadataTableEntity> findAllBySourceFileIdAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(Long sourceFileId);

    List<MetadataTableEntity> findAllByProjectIdAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(Long projectId);
}

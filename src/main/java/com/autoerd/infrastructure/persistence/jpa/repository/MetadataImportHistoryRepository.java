package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataImportHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataImportHistoryRepository extends JpaRepository<MetadataImportHistoryEntity, Long> {

    List<MetadataImportHistoryEntity> findAllByProjectIdOrderByStartedAtDesc(Long projectId);

    List<MetadataImportHistoryEntity> findAllBySourceFileIdOrderByStartedAtDesc(Long sourceFileId);
}

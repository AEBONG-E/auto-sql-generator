package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataSourceFileRepository extends JpaRepository<MetadataSourceFileEntity, Long> {

    List<MetadataSourceFileEntity> findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(Long projectId);
}

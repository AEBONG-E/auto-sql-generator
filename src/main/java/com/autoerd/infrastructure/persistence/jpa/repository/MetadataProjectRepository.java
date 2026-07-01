package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataProjectRepository extends JpaRepository<MetadataProjectEntity, Long> {

    Optional<MetadataProjectEntity> findByProjectKeyAndIsDeletedFalse(String projectKey);
}

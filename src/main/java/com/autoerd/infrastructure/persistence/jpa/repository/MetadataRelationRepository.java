package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataRelationRepository extends JpaRepository<MetadataRelationEntity, Long> {

    List<MetadataRelationEntity> findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderByIdAsc(Long projectId);

    List<MetadataRelationEntity> findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(Long projectId);
}

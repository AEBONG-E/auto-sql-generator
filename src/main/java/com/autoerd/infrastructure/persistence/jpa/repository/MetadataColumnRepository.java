package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataColumnRepository extends JpaRepository<MetadataColumnEntity, Long> {

    List<MetadataColumnEntity> findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(Long tableId);
}

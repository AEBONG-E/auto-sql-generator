package com.autoerd.infrastructure.persistence.jpa.repository;

import com.autoerd.infrastructure.persistence.jpa.entity.ProjectBusinessRuleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBusinessRuleRepository extends JpaRepository<ProjectBusinessRuleEntity, Long> {

    List<ProjectBusinessRuleEntity> findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(Long projectId);

    Optional<ProjectBusinessRuleEntity> findByIdAndProjectIdAndIsDeletedFalse(Long id, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}

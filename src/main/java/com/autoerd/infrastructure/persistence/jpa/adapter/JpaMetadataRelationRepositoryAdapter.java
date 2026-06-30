package com.autoerd.infrastructure.persistence.jpa.adapter;

import com.autoerd.domain.metadata.repository.MetadataRelationRepositoryPort;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaMetadataRelationRepositoryAdapter implements MetadataRelationRepositoryPort {

    private final MetadataRelationRepository relationRepository;

    @Override
    public List<Long> findCurrentRelationIdsByProjectId(Long projectId) {
        return relationRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderByIdAsc(projectId)
                .stream().map(e -> e.getId()).toList();
    }
}

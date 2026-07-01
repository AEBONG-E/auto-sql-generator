package com.autoerd.infrastructure.persistence.jpa.adapter;

import com.autoerd.domain.metadata.repository.MetadataProjectRepositoryPort;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaMetadataProjectRepositoryAdapter implements MetadataProjectRepositoryPort {

    private final MetadataProjectRepository projectRepository;

    @Override
    public Optional<Long> findIdByProjectKey(String projectKey) {
        return projectRepository.findByProjectKeyAndIsDeletedFalse(projectKey)
                .map(e -> e.getId());
    }

    @Override
    public boolean existsActiveProject(Long projectId) {
        return projectRepository.findById(projectId)
                .filter(e -> !e.getIsDeleted())
                .isPresent();
    }
}

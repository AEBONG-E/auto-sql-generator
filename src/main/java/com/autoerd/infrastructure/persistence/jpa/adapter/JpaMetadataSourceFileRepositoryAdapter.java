package com.autoerd.infrastructure.persistence.jpa.adapter;

import com.autoerd.domain.metadata.repository.MetadataSourceFileRepositoryPort;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaMetadataSourceFileRepositoryAdapter implements MetadataSourceFileRepositoryPort {

    private final MetadataSourceFileRepository sourceFileRepository;

    @Override
    public List<Long> findActiveSourceFileIdsByProjectId(Long projectId) {
        return sourceFileRepository.findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(projectId)
                .stream().map(e -> e.getId()).toList();
    }
}

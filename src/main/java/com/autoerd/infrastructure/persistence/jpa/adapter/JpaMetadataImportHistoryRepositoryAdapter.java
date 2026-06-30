package com.autoerd.infrastructure.persistence.jpa.adapter;

import com.autoerd.domain.metadata.repository.MetadataImportHistoryRepositoryPort;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataImportHistoryEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataImportHistoryRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JpaMetadataImportHistoryRepositoryAdapter implements MetadataImportHistoryRepositoryPort {

    private final MetadataImportHistoryRepository importHistoryRepository;
    private final MetadataProjectRepository projectRepository;
    private final MetadataSourceFileRepository sourceFileRepository;

    @Override
    public void appendHistory(Long projectId, Long sourceFileId, String importStatus, String errorMessage) {
        MetadataProjectEntity project = projectRepository.getReferenceById(projectId);
        MetadataSourceFileEntity sourceFile = (sourceFileId != null)
                ? sourceFileRepository.getReferenceById(sourceFileId)
                : null;

        LocalDateTime now = LocalDateTime.now();
        MetadataImportHistoryEntity history = MetadataImportHistoryEntity.builder()
                .project(project)
                .sourceFile(sourceFile)
                .importStatus(importStatus)
                .startedAt(now)
                .finishedAt(now)
                .errorMessage(errorMessage)
                .build();

        importHistoryRepository.save(history);
    }
}

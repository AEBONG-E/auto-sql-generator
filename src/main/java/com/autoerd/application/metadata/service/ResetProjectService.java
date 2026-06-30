package com.autoerd.application.metadata.service;

import com.autoerd.application.metadata.usecase.ResetProjectCommand;
import com.autoerd.application.metadata.usecase.ResetProjectUseCase;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataColumnRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataRelationRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetProjectService implements ResetProjectUseCase {

    private final MetadataTableRepository tableRepository;
    private final MetadataColumnRepository columnRepository;
    private final MetadataRelationRepository relationRepository;
    private final MetadataSourceFileRepository sourceFileRepository;

    @Override
    @Transactional
    public void resetProject(ResetProjectCommand command) {
        Long projectId = command.projectId();
        LocalDateTime now = LocalDateTime.now();

        // 1. 관계 전체 소프트 삭제
        List<MetadataRelationEntity> relations =
                relationRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(projectId);
        for (MetadataRelationEntity rel : relations) {
            rel.setIsDeleted(true);
            rel.setDeletedAt(now);
            rel.setIsCurrent(false);
        }
        relationRepository.saveAll(relations);

        // 2. 테이블 + 컬럼 전체 소프트 삭제
        List<MetadataTableEntity> tables =
                tableRepository.findAllByProjectIdAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(projectId);
        for (MetadataTableEntity table : tables) {
            List<MetadataColumnEntity> columns =
                    columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(table.getId());
            for (MetadataColumnEntity col : columns) {
                col.setIsDeleted(true);
                col.setDeletedAt(now);
            }
            columnRepository.saveAll(columns);

            table.setIsDeleted(true);
            table.setDeletedAt(now);
            table.setIsCurrent(false);
        }
        tableRepository.saveAll(tables);

        // 3. 소스 파일 전체 소프트 삭제
        List<MetadataSourceFileEntity> sourceFiles =
                sourceFileRepository.findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(projectId);
        for (MetadataSourceFileEntity sf : sourceFiles) {
            sf.setIsDeleted(true);
            sf.setDeletedAt(now);
        }
        sourceFileRepository.saveAll(sourceFiles);

        log.info("프로젝트 초기화 완료: projectId={}", projectId);
    }
}

package com.autoerd.application.metadata.service;

import com.autoerd.application.metadata.usecase.DeleteSourceFileCommand;
import com.autoerd.application.metadata.usecase.DeleteSourceFileUseCase;
import com.autoerd.exception.AppException;
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
public class DeleteSourceFileService implements DeleteSourceFileUseCase {

    private final MetadataSourceFileRepository sourceFileRepository;
    private final MetadataTableRepository tableRepository;
    private final MetadataColumnRepository columnRepository;
    private final MetadataRelationRepository relationRepository;

    @Override
    @Transactional
    public void deleteSourceFile(DeleteSourceFileCommand command) {
        MetadataSourceFileEntity sourceFile = sourceFileRepository.findById(command.sourceFileId())
                .filter(sf -> sf.getProject().getId().equals(command.projectId()) && !sf.getIsDeleted())
                .orElseThrow(() -> AppException.badRequest(
                        "파일을 찾을 수 없습니다. projectId=" + command.projectId() + ", fileId=" + command.sourceFileId()));

        LocalDateTime now = LocalDateTime.now();

        List<MetadataTableEntity> tables =
                tableRepository.findAllBySourceFileIdAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(command.sourceFileId());

        for (MetadataTableEntity table : tables) {
            // 이 테이블이 관여된 활성 관계 비활성화 + 소프트 삭제
            List<MetadataRelationEntity> relatedRelations =
                    relationRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(command.projectId())
                            .stream()
                            .filter(r -> r.getFromTable().getId().equals(table.getId())
                                      || r.getToTable().getId().equals(table.getId()))
                            .toList();

            for (MetadataRelationEntity rel : relatedRelations) {
                rel.setIsDeleted(true);
                rel.setDeletedAt(now);
                rel.setIsCurrent(false);
                relationRepository.save(rel);
            }

            // 컬럼 소프트 삭제
            List<MetadataColumnEntity> columns =
                    columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(table.getId());
            for (MetadataColumnEntity col : columns) {
                col.setIsDeleted(true);
                col.setDeletedAt(now);
                columnRepository.save(col);
            }

            table.setIsDeleted(true);
            table.setDeletedAt(now);
            table.setIsCurrent(false);
            tableRepository.save(table);
        }

        sourceFile.setIsDeleted(true);
        sourceFile.setDeletedAt(now);
        sourceFile.setImportStatus("ROLLED_BACK");
        sourceFileRepository.save(sourceFile);

        log.info("소스 파일 삭제 완료: fileId={}, filename={}", command.sourceFileId(), sourceFile.getOriginalFilename());
    }
}

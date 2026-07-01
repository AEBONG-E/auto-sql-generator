package com.autoerd.application.metadata.service;

import com.autoerd.application.metadata.usecase.ImportMetadataCommand;
import com.autoerd.application.metadata.usecase.ImportMetadataUseCase;
import com.autoerd.application.metadata.usecase.UploadSchemaFilePayload;
import com.autoerd.domain.metadata.policy.MetadataConflictPolicy;
import com.autoerd.domain.metadata.policy.RelationInferencePolicy;
import com.autoerd.domain.metadata.repository.MetadataImportHistoryRepositoryPort;
import com.autoerd.domain.metadata.repository.MetadataProjectRepositoryPort;
import com.autoerd.exception.AppException;
import com.autoerd.infrastructure.excel.ExtractedSchemaFile;
import com.autoerd.infrastructure.excel.ExcelSchemaExtractor;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataColumnRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataRelationRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportMetadataService implements ImportMetadataUseCase {

    private final MetadataProjectRepositoryPort projectRepositoryPort;
    private final MetadataImportHistoryRepositoryPort importHistoryRepositoryPort;
    private final ExcelSchemaExtractor excelSchemaExtractor;
    private final RelationInferencePolicy relationInferencePolicy;
    private final MetadataConflictPolicy conflictPolicy;

    private final MetadataProjectRepository projectRepository;
    private final MetadataSourceFileRepository sourceFileRepository;
    private final MetadataTableRepository tableRepository;
    private final MetadataColumnRepository columnRepository;
    private final MetadataRelationRepository relationRepository;

    @Override
    @Transactional
    public void importMetadata(ImportMetadataCommand command) {
        if (!projectRepositoryPort.existsActiveProject(command.projectId())) {
            throw AppException.badRequest("존재하지 않는 프로젝트: " + command.projectId());
        }

        MetadataProjectEntity project = projectRepository.findById(command.projectId())
                .orElseThrow(() -> AppException.badRequest("프로젝트를 찾을 수 없습니다: " + command.projectId()));

        for (UploadSchemaFilePayload payload : command.files()) {
            importSingleFile(project, payload);
        }
    }

    @Transactional
    public void importSingleFile(MetadataProjectEntity project, UploadSchemaFilePayload payload) {
        LocalDateTime now = LocalDateTime.now();

        MetadataSourceFileEntity sourceFile = MetadataSourceFileEntity.builder()
                .project(project)
                .originalFilename(payload.originalFilename())
                .sourceType("XLSX")
                .importStatus("IMPORTED")
                .uploadedAt(now)
                .build();
        sourceFileRepository.save(sourceFile);

        List<TableSchema> schemas;
        try {
            schemas = excelSchemaExtractor.extract(
                    new ExtractedSchemaFile(payload.originalFilename(), payload.size(), payload.content()));
        } catch (Exception e) {
            sourceFile.setImportStatus("FAILED");
            sourceFileRepository.save(sourceFile);
            importHistoryRepositoryPort.appendHistory(project.getId(), sourceFile.getId(), "FAILED", e.getMessage());
            throw AppException.unprocessable("파일 파싱 실패 [" + payload.originalFilename() + "]: " + e.getMessage());
        }

        List<String> existingTableNames = tableRepository
                .findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(project.getId())
                .stream().map(MetadataTableEntity::getTableName).toList();
        List<String> incomingTableNames = schemas.stream().map(TableSchema::getTableName).toList();
        List<String> conflictNames = conflictPolicy.resolveOverwrittenTableNames(existingTableNames, incomingTableNames);

        // 충돌 테이블의 기존 엔티티를 비활성화
        for (String conflictName : conflictNames) {
            Optional<MetadataTableEntity> oldTable = tableRepository
                    .findByProjectIdAndSchemaNameAndTableNameAndIsCurrentTrueAndIsDeletedFalse(
                            project.getId(), "default", conflictName);

            oldTable.ifPresent(old -> {
                // 기존 테이블과 연관된 활성 관계를 비활성화
                relationRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderByIdAsc(project.getId())
                        .stream()
                        .filter(r -> r.getFromTable().getId().equals(old.getId())
                                  || r.getToTable().getId().equals(old.getId()))
                        .forEach(r -> {
                            r.setIsCurrent(false);
                            relationRepository.save(r);
                        });

                old.setIsCurrent(false);
                tableRepository.save(old);
                log.debug("기존 테이블 비활성화: {}", conflictName);
            });
        }

        // 신규 테이블/컬럼 저장
        List<MetadataTableEntity> savedTables = new ArrayList<>();
        int totalColumns = 0;

        for (TableSchema schema : schemas) {
            MetadataTableEntity tableEntity = MetadataTableEntity.builder()
                    .project(project)
                    .sourceFile(sourceFile)
                    .schemaName("default")
                    .tableName(schema.getTableName())
                    .tableDescription(schema.getTableDescription())
                    .isCurrent(true)
                    .currentVersionNo(1)
                    .build();
            tableRepository.save(tableEntity);
            savedTables.add(tableEntity);

            int ordinalFallback = 1;
            for (ColumnDef col : schema.getColumns()) {
                MetadataColumnEntity colEntity = MetadataColumnEntity.builder()
                        .table(tableEntity)
                        .ordinalPosition(col.getOrdinal() > 0 ? col.getOrdinal() : ordinalFallback)
                        .columnName(col.getColumnName())
                        .columnComment(col.getColumnComment())
                        .dataType(col.getDataType())
                        .columnType(col.getColumnType())
                        .keyType(col.getKeyType().name())
                        .nullableYn(col.isNullable() ? "Y" : "N")
                        .autoIncrementYn(col.isAutoIncrement() ? "Y" : "N")
                        .defaultValue(col.getDefaultValue())
                        .build();
                columnRepository.save(colEntity);
                ordinalFallback++;
                totalColumns++;
            }
        }

        // 관계 추론 및 저장
        List<TableRelation> relations = relationInferencePolicy.inferRelations(schemas);
        Map<String, MetadataTableEntity> tableMap = new HashMap<>();
        for (MetadataTableEntity t : savedTables) {
            tableMap.put(t.getTableName(), t);
        }

        // 컬럼 조회 캐시 (table ID → column list)
        Map<Long, List<MetadataColumnEntity>> columnCache = new HashMap<>();
        for (MetadataTableEntity t : savedTables) {
            columnCache.put(t.getId(),
                    columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(t.getId()));
        }

        int relationCount = 0;
        for (TableRelation rel : relations) {
            MetadataTableEntity fromTable = tableMap.get(rel.getFromTable());
            MetadataTableEntity toTable = tableMap.get(rel.getToTable());
            if (fromTable == null || toTable == null) continue;

            Optional<MetadataColumnEntity> fromCol = columnCache.getOrDefault(fromTable.getId(), List.of())
                    .stream().filter(c -> c.getColumnName().equals(rel.getFromColumn())).findFirst();
            Optional<MetadataColumnEntity> toCol = columnCache.getOrDefault(toTable.getId(), List.of())
                    .stream().filter(c -> c.getColumnName().equals(rel.getToColumn())).findFirst();

            if (fromCol.isEmpty() || toCol.isEmpty()) continue;

            MetadataRelationEntity relEntity = MetadataRelationEntity.builder()
                    .project(project)
                    .sourceFile(sourceFile)
                    .fromTable(fromTable)
                    .fromColumn(fromCol.get())
                    .toTable(toTable)
                    .toColumn(toCol.get())
                    .relationType(rel.getType().name())
                    .isCurrent(true)
                    .build();
            relationRepository.save(relEntity);
            relationCount++;
        }

        sourceFile.setTableCount(schemas.size());
        sourceFile.setColumnCount(totalColumns);
        sourceFile.setRelationCount(relationCount);
        sourceFileRepository.save(sourceFile);

        importHistoryRepositoryPort.appendHistory(project.getId(), sourceFile.getId(), "SUCCEEDED", null);
        log.info("파일 임포트 완료: {} — 테이블={}, 컬럼={}, 관계={}",
                payload.originalFilename(), schemas.size(), totalColumns, relationCount);
    }
}

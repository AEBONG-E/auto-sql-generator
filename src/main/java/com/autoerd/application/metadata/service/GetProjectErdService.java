package com.autoerd.application.metadata.service;

import com.autoerd.application.metadata.usecase.GetProjectErdQuery;
import com.autoerd.application.metadata.usecase.GetProjectErdUseCase;
import com.autoerd.dto.ErdResponse;
import com.autoerd.infrastructure.assembler.MetadataDomainAssembler;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataColumnRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataRelationRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetProjectErdService implements GetProjectErdUseCase {

    private final MetadataTableRepository tableRepository;
    private final MetadataColumnRepository columnRepository;
    private final MetadataRelationRepository relationRepository;
    private final MetadataSourceFileRepository sourceFileRepository;
    private final MetadataDomainAssembler assembler;

    @Override
    @Transactional(readOnly = true)
    public ErdResponse getProjectErd(GetProjectErdQuery query) {
        Long projectId = query.projectId();

        List<MetadataTableEntity> tableEntities =
                tableRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(projectId);

        Set<String> tableFilter = query.tableNames();
        if (tableFilter != null && !tableFilter.isEmpty()) {
            tableEntities = tableEntities.stream()
                    .filter(t -> tableFilter.contains(t.getTableName()))
                    .toList();
        }

        List<TableSchema> schemas = tableEntities.stream()
                .map(t -> {
                    List<MetadataColumnEntity> cols =
                            columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(t.getId());
                    return assembler.toTableSchema(t, cols);
                })
                .toList();

        Set<Long> activeTableIds = tableEntities.stream()
                .map(MetadataTableEntity::getId)
                .collect(Collectors.toSet());

        List<MetadataRelationEntity> relEntities =
                relationRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderByIdAsc(projectId);

        List<TableRelation> relations = relEntities.stream()
                .filter(r -> activeTableIds.contains(r.getFromTable().getId())
                          && activeTableIds.contains(r.getToTable().getId()))
                .map(assembler::toTableRelation)
                .toList();

        // 활성 소스 파일별 테이블 수 요약
        Map<Long, String> tableIdToSourceFilename = tableEntities.stream()
                .collect(Collectors.toMap(
                        MetadataTableEntity::getId,
                        t -> t.getSourceFile().getOriginalFilename()));

        Map<String, Long> filenameToTableCount = tableIdToSourceFilename.values().stream()
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

        List<MetadataSourceFileEntity> sourceFiles =
                sourceFileRepository.findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(projectId);

        Map<String, Integer> fileSummary = new LinkedHashMap<>();
        for (MetadataSourceFileEntity sf : sourceFiles) {
            Long count = filenameToTableCount.get(sf.getOriginalFilename());
            if (count != null && count > 0) {
                fileSummary.put(sf.getOriginalFilename(), count.intValue());
            }
        }

        return ErdResponse.from(schemas, relations, fileSummary);
    }
}

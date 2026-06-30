package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.domain.sql.port.SqlMetadataProvider;
import com.autoerd.infrastructure.assembler.MetadataDomainAssembler;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataColumnRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataRelationRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JpaSqlMetadataProviderAdapter implements SqlMetadataProvider {

    private final MetadataTableRepository tableRepository;
    private final MetadataColumnRepository columnRepository;
    private final MetadataRelationRepository relationRepository;
    private final MetadataDomainAssembler assembler;

    @Override
    @Transactional(readOnly = true)
    public SqlMetadataSnapshot getCurrentSnapshot(Long projectId) {
        List<MetadataTableEntity> tableEntities =
                tableRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(projectId);

        List<TableSchema> tables = tableEntities.stream()
                .map(t -> {
                    List<MetadataColumnEntity> cols =
                            columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(t.getId());
                    return assembler.toTableSchema(t, cols);
                })
                .toList();

        List<MetadataRelationEntity> relEntities =
                relationRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderByIdAsc(projectId);

        Set<Long> tableIds = tableEntities.stream().map(MetadataTableEntity::getId).collect(Collectors.toSet());
        List<TableRelation> relations = relEntities.stream()
                .filter(r -> tableIds.contains(r.getFromTable().getId()) && tableIds.contains(r.getToTable().getId()))
                .map(assembler::toTableRelation)
                .toList();

        return new SqlMetadataSnapshot(tables, relations);
    }
}

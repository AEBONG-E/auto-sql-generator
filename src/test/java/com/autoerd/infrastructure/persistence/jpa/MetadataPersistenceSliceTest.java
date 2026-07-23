package com.autoerd.infrastructure.persistence.jpa;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Project / SourceFile / Table / Column / Relation 저장·조회 슬라이스 테스트.
 * schema-h2.sql(spring.sql.init.mode=always)로 실제 스키마를 사용하므로 replace=NONE으로
 * 내장 테스트 DB 자동 치환을 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MetadataPersistenceSliceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MetadataProjectRepository projectRepository;
    @Autowired
    private MetadataSourceFileRepository sourceFileRepository;
    @Autowired
    private MetadataTableRepository tableRepository;
    @Autowired
    private MetadataColumnRepository columnRepository;
    @Autowired
    private MetadataRelationRepository relationRepository;

    private MetadataProjectEntity persistProject(String key) {
        return projectRepository.saveAndFlush(MetadataProjectEntity.builder()
                .projectKey(key)
                .projectName(key + "-name")
                .projectStatus("ACTIVE")
                .build());
    }

    private MetadataSourceFileEntity persistSourceFile(MetadataProjectEntity project, String filename) {
        return sourceFileRepository.saveAndFlush(MetadataSourceFileEntity.builder()
                .project(project)
                .originalFilename(filename)
                .sourceType("XLSX")
                .importStatus("IMPORTED")
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    @Test
    void projectShouldPersistAndBeFindableById() {
        MetadataProjectEntity saved = persistProject("proj-a");

        Optional<MetadataProjectEntity> found = projectRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProjectKey()).isEqualTo("proj-a");
        assertThat(found.get().getIsDeleted()).isFalse();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void sourceFileShouldPersistWithProjectAssociationAndBeQueryableByProject() {
        MetadataProjectEntity project = persistProject("proj-b");
        persistSourceFile(project, "schema.xlsx");

        List<MetadataSourceFileEntity> files =
                sourceFileRepository.findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(project.getId());

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getOriginalFilename()).isEqualTo("schema.xlsx");
        assertThat(files.get(0).getProject().getId()).isEqualTo(project.getId());
    }

    @Test
    void tableShouldPersistAndBeFindableByProjectSchemaAndName() {
        MetadataProjectEntity project = persistProject("proj-c");
        MetadataSourceFileEntity sourceFile = persistSourceFile(project, "orders.xlsx");

        tableRepository.saveAndFlush(MetadataTableEntity.builder()
                .project(project)
                .sourceFile(sourceFile)
                .schemaName("default")
                .tableName("orders")
                .tableDescription("주문")
                .isCurrent(true)
                .currentVersionNo(1)
                .build());
        em.clear();

        List<MetadataTableEntity> active = tableRepository
                .findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(project.getId());
        Optional<MetadataTableEntity> byName = tableRepository
                .findByProjectIdAndSchemaNameAndTableNameAndIsCurrentTrueAndIsDeletedFalse(
                        project.getId(), "default", "orders");
        List<MetadataTableEntity> bySourceFile = tableRepository
                .findAllBySourceFileIdAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(sourceFile.getId());

        assertThat(active).hasSize(1);
        assertThat(byName).isPresent();
        assertThat(byName.get().getTableDescription()).isEqualTo("주문");
        assertThat(bySourceFile).hasSize(1);
    }

    @Test
    void columnShouldPersistAndBeOrderedByOrdinalPosition() {
        MetadataProjectEntity project = persistProject("proj-d");
        MetadataSourceFileEntity sourceFile = persistSourceFile(project, "customer.xlsx");
        MetadataTableEntity table = tableRepository.saveAndFlush(MetadataTableEntity.builder()
                .project(project)
                .sourceFile(sourceFile)
                .schemaName("default")
                .tableName("customer")
                .isCurrent(true)
                .currentVersionNo(1)
                .build());

        // 의도적으로 순번 역순으로 저장 — 조회는 ordinal 오름차순이어야 함
        columnRepository.saveAndFlush(MetadataColumnEntity.builder()
                .table(table).ordinalPosition(2).columnName("name").dataType("varchar").build());
        columnRepository.saveAndFlush(MetadataColumnEntity.builder()
                .table(table).ordinalPosition(1).columnName("id").dataType("bigint").keyType("PRI").build());
        em.clear();

        List<MetadataColumnEntity> columns =
                columnRepository.findAllByTableIdAndIsDeletedFalseOrderByOrdinalPositionAsc(table.getId());

        assertThat(columns).extracting(MetadataColumnEntity::getColumnName)
                .containsExactly("id", "name");
    }

    @Test
    void relationShouldPersistWithFromToTableAndColumnAssociations() {
        MetadataProjectEntity project = persistProject("proj-e");
        MetadataSourceFileEntity sourceFile = persistSourceFile(project, "orders.xlsx");

        MetadataTableEntity orders = tableRepository.saveAndFlush(MetadataTableEntity.builder()
                .project(project).sourceFile(sourceFile).schemaName("default")
                .tableName("orders").isCurrent(true).currentVersionNo(1).build());
        MetadataTableEntity customer = tableRepository.saveAndFlush(MetadataTableEntity.builder()
                .project(project).sourceFile(sourceFile).schemaName("default")
                .tableName("customer").isCurrent(true).currentVersionNo(1).build());

        MetadataColumnEntity customerId = columnRepository.saveAndFlush(MetadataColumnEntity.builder()
                .table(orders).ordinalPosition(1).columnName("customer_id").dataType("bigint").build());
        MetadataColumnEntity id = columnRepository.saveAndFlush(MetadataColumnEntity.builder()
                .table(customer).ordinalPosition(1).columnName("id").dataType("bigint").keyType("PRI").build());

        relationRepository.saveAndFlush(MetadataRelationEntity.builder()
                .project(project).sourceFile(sourceFile)
                .fromTable(orders).fromColumn(customerId)
                .toTable(customer).toColumn(id)
                .relationType("ONE_TO_MANY")
                .isCurrent(true)
                .build());
        em.clear();

        List<MetadataRelationEntity> current =
                relationRepository.findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderByIdAsc(project.getId());
        List<MetadataRelationEntity> all =
                relationRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(project.getId());

        assertThat(current).hasSize(1);
        assertThat(all).hasSize(1);
        assertThat(current.get(0).getFromTable().getTableName()).isEqualTo("orders");
        assertThat(current.get(0).getToTable().getTableName()).isEqualTo("customer");
        assertThat(current.get(0).getFromColumn().getColumnName()).isEqualTo("customer_id");
        assertThat(current.get(0).getRelationType()).isEqualTo("ONE_TO_MANY");
    }
}

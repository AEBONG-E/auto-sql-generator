package com.autoerd.application.metadata;

import com.autoerd.application.project.ProjectService;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataTableRepository;
import com.autoerd.testsupport.ExcelFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD §12(테스트 전략) DoD: 업로드 API → DB 저장 → ERD 조회 전체 흐름,
 * 동일 테이블명 재업로드 시 최신본 우선(MetadataConflictPolicy) 일관 적용,
 * 파일 삭제/프로젝트 초기화 후 ERD 재집계 검증.
 *
 * 클래스 단위 @Transactional로 각 테스트를 롤백해 격리한다(MockMvc 호출은 동일 스레드에서
 * 동기 실행되어 테스트 트랜잭션에 참여한다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetadataImportFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private MetadataTableRepository tableRepository;
    @Autowired
    private MetadataSourceFileRepository sourceFileRepository;

    private Long createProject(String key) {
        MetadataProjectEntity project = projectService.createProject(key, key + "-name", null);
        return project.getId();
    }

    private MockMultipartFile xlsxFile(String filename, byte[] content) {
        return new MockMultipartFile("files", filename, "application/octet-stream", content);
    }

    @Test
    void uploadShouldPersistTablesColumnsAndRelationsQueryableViaErd() throws Exception {
        Long projectId = createProject("flow-upload");

        byte[] xlsx = ExcelFixtures.standardKorean(
                new Object[]{"orders", "주문", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""},
                new Object[]{"orders", "주문", 2, "customer_id", "", "bigint", "20", "MUL", "NO", "", ""},
                new Object[]{"customer", "고객", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""}
        );

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(xlsxFile("orders.xlsx", xlsx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/erd", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.tableCount").value(2))
                .andExpect(jsonPath("$.stats.columnCount").value(3))
                .andExpect(jsonPath("$.stats.relationCount").value(1))
                .andExpect(jsonPath("$.tables[*].tableName").isArray())
                .andExpect(jsonPath("$.relations[0].fromTable").value("orders"))
                .andExpect(jsonPath("$.relations[0].toTable").value("customer"))
                .andExpect(jsonPath("$.files[0].filename").value("orders.xlsx"));
    }

    @Test
    void reuploadingSameTableNameShouldKeepOnlyLatestVersionActive() throws Exception {
        Long projectId = createProject("flow-conflict");

        byte[] v1 = ExcelFixtures.standardKorean(
                new Object[]{"orders", "주문 v1", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""}
        );
        byte[] v2 = ExcelFixtures.standardKorean(
                new Object[]{"orders", "주문 v2", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""},
                new Object[]{"orders", "주문 v2", 2, "note", "비고", "varchar", "255", "", "YES", "", ""}
        );

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(xlsxFile("orders-v1.xlsx", v1)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(xlsxFile("orders-v2.xlsx", v2)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{projectId}/erd", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.tableCount").value(1))
                .andExpect(jsonPath("$.tables[0].tableDescription").value("주문 v2"))
                .andExpect(jsonPath("$.tables[0].columnCount").value(2));

        // 최신본 우선 정책 확인: 활성(is_current=true)은 1건, 소프트 삭제 전 전체(비활성 포함)는 2건이어야 함
        List<MetadataTableEntity> active = tableRepository
                .findAllByProjectIdAndIsCurrentTrueAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(projectId);
        List<MetadataTableEntity> all = tableRepository
                .findAllByProjectIdAndIsDeletedFalseOrderBySchemaNameAscTableNameAsc(projectId);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getTableDescription()).isEqualTo("주문 v2");
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(t -> !t.getIsCurrent()).hasSize(1)
                .allSatisfy(t -> assertThat(t.getTableDescription()).isEqualTo("주문 v1"));
    }

    @Test
    void deletingSourceFileShouldRemoveItsTablesFromErdReaggregation() throws Exception {
        Long projectId = createProject("flow-delete");

        byte[] ordersFile = ExcelFixtures.standardKorean(
                new Object[]{"orders", "주문", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""}
        );
        byte[] customerFile = ExcelFixtures.standardKorean(
                new Object[]{"customer", "고객", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""}
        );

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(xlsxFile("orders.xlsx", ordersFile)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(xlsxFile("customer.xlsx", customerFile)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{projectId}/erd", projectId))
                .andExpect(jsonPath("$.stats.tableCount").value(2));

        MetadataSourceFileEntity customerSourceFile = sourceFileRepository
                .findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(projectId).stream()
                .filter(sf -> sf.getOriginalFilename().equals("customer.xlsx"))
                .findFirst().orElseThrow();

        mockMvc.perform(delete("/api/v1/projects/{projectId}/files/{fileId}", projectId, customerSourceFile.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{projectId}/erd", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.tableCount").value(1))
                .andExpect(jsonPath("$.tables[0].tableName").value("orders"));
    }

    @Test
    void resettingProjectShouldClearAllMetadataFromErdReaggregation() throws Exception {
        Long projectId = createProject("flow-reset");

        byte[] xlsx = ExcelFixtures.standardKorean(
                new Object[]{"orders", "주문", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""},
                new Object[]{"orders", "주문", 2, "customer_id", "", "bigint", "20", "MUL", "NO", "", ""}
        );

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(xlsxFile("orders.xlsx", xlsx)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{projectId}/erd", projectId))
                .andExpect(jsonPath("$.stats.tableCount").value(1));

        mockMvc.perform(post("/api/v1/projects/{projectId}/reset", projectId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{projectId}/erd", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.tableCount").value(0))
                .andExpect(jsonPath("$.stats.columnCount").value(0))
                .andExpect(jsonPath("$.stats.relationCount").value(0))
                .andExpect(jsonPath("$.tables").isArray())
                .andExpect(jsonPath("$.tables").isEmpty());
    }
}

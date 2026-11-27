package com.autoerd.controller.v1;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이슈 #18: 프로젝트별 SQL 방언 저장과 API.
 * 클래스 단위 @Transactional 로 각 테스트를 롤백해 격리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectSqlDialectApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MetadataProjectRepository projectRepository;

    private Long createProject(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return Long.valueOf(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("방언 없이 생성하면 MYSQL 로 저장하고 응답에 sqlDialect 를 담는다")
    void create_withoutDialect_defaultsToMysql() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"d-none\",\"projectName\":\"n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlDialect").value("MYSQL"));
    }

    @Test
    @DisplayName("빈 문자열 방언은 MYSQL 로 저장한다")
    void create_withBlankDialect_defaultsToMysql() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"d-blank\",\"projectName\":\"n\",\"sqlDialect\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlDialect").value("MYSQL"));
    }

    @Test
    @DisplayName("방언을 지정하면 대소문자를 무시하고 저장한다")
    void create_withDialect_isCaseInsensitive() throws Exception {
        Long id = createProject("{\"projectKey\":\"d-mssql\",\"projectName\":\"n\",\"sqlDialect\":\"msSql\"}");

        mockMvc.perform(get("/api/v1/projects/{projectId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlDialect").value("MSSQL"));
    }

    @Test
    @DisplayName("지원하지 않는 방언으로 생성하면 400 이고 프로젝트를 만들지 않는다")
    void create_withInvalidDialect_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"d-bad\",\"projectName\":\"n\",\"sqlDialect\":\"ORACLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        assertThat(projectRepository.findByProjectKeyAndIsDeletedFalse("d-bad")).isEmpty();
    }

    @Test
    @DisplayName("목록 조회 응답에 sqlDialect 를 담는다")
    void list_containsSqlDialect() throws Exception {
        createProject("{\"projectKey\":\"d-list\",\"projectName\":\"n\",\"sqlDialect\":\"POSTGRESQL\"}");

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectKey == 'd-list')].sqlDialect").value("POSTGRESQL"));
    }

    @Test
    @DisplayName("PATCH 로 방언만 바꾸고 조회 형식으로 응답한다")
    void patch_updatesDialect() throws Exception {
        Long id = createProject("{\"projectKey\":\"d-patch\",\"projectName\":\"n\"}");

        mockMvc.perform(patch("/api/v1/projects/{projectId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sqlDialect\":\"postgresql\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.projectKey").value("d-patch"))
                .andExpect(jsonPath("$.projectName").value("n"))
                .andExpect(jsonPath("$.sqlDialect").value("POSTGRESQL"));

        mockMvc.perform(get("/api/v1/projects/{projectId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlDialect").value("POSTGRESQL"));
    }

    @Test
    @DisplayName("PATCH 에 지원하지 않는 방언이나 빈 본문을 주면 400 이고 기존 값을 유지한다")
    void patch_withInvalidDialect_returns400() throws Exception {
        Long id = createProject("{\"projectKey\":\"d-patch-bad\",\"projectName\":\"n\",\"sqlDialect\":\"MSSQL\"}");

        mockMvc.perform(patch("/api/v1/projects/{projectId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sqlDialect\":\"ORACLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        mockMvc.perform(patch("/api/v1/projects/{projectId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/api/v1/projects/{projectId}", id))
                .andExpect(jsonPath("$.sqlDialect").value("MSSQL"));
    }

    @Test
    @DisplayName("삭제된 프로젝트에 PATCH 하면 단건 조회와 같이 400 을 준다")
    void patch_deletedProject_returns400() throws Exception {
        Long id = createProject("{\"projectKey\":\"d-deleted\",\"projectName\":\"n\"}");
        MetadataProjectEntity project = projectRepository.findById(id).orElseThrow();
        project.setIsDeleted(true);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.saveAndFlush(project);

        mockMvc.perform(patch("/api/v1/projects/{projectId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sqlDialect\":\"MSSQL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("프로젝트를 찾을 수 없습니다: " + id));
    }
}

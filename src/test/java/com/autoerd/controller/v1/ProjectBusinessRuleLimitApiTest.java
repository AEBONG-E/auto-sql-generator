package com.autoerd.controller.v1;

import com.autoerd.application.project.ProjectService;
import com.autoerd.testsupport.ExcelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이슈 #24: 프로젝트당 업무 규칙 상한 (app.business-rules.max-per-project). 상한을 2 로 낮춰 확인한다.
 */
@SpringBootTest(properties = "app.business-rules.max-per-project=2")
@AutoConfigureMockMvc
@Transactional
class ProjectBusinessRuleLimitApiTest {

    private static final String RULE = """
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"deleted_yn = 'N'"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;

    @Test
    @DisplayName("상한에 도달하면 등록은 400 이고, 삭제한 규칙은 상한 계산에서 빠진다")
    void create_overLimit_returns400() throws Exception {
        Long projectId = projectService.createProject("br-limit", "limit", null).getId();
        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(new MockMultipartFile("files", "o.xlsx", "application/octet-stream",
                                ExcelFixtures.standardKorean(
                                        new Object[]{"shop_order", "주문", 1, "deleted_yn", "", "char", "1", "", "NO", "", ""}))))
                .andExpect(status().isOk());

        String first = mockMvc.perform(post("/api/v1/projects/{projectId}/business-rules", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/projects/{projectId}/business-rules", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects/{projectId}/business-rules", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("업무 규칙은 프로젝트당 2개까지 등록할 수 있습니다"));

        Long firstId = Long.valueOf(first.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/business-rules/{ruleId}", projectId, firstId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/projects/{projectId}/business-rules", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isCreated());
    }
}

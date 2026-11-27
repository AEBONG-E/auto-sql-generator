package com.autoerd.controller.v1;

import com.autoerd.application.project.ProjectService;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProject(@RequestBody Map<String, String> body) {
        String projectKey = body.get("projectKey");
        String projectName = body.get("projectName");
        String description = body.get("description");

        if (projectKey == null || projectKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectKey는 필수입니다"));
        }
        if (projectName == null || projectName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectName은 필수입니다"));
        }

        MetadataProjectEntity project = projectService.createProject(
                projectKey, projectName, description, body.get("sqlDialect"));
        log.info("프로젝트 생성: id={}, key={}, sqlDialect={}", project.getId(), projectKey, project.getSqlDialect());

        return ResponseEntity.ok(toProjectResponse(project));
    }

    /**
     * 프로젝트의 SQL 방언만 바꾼다. 본문은 {"sqlDialect": "..."} 만 사용하고 다른 필드는 반영하지 않는다.
     */
    @PatchMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> updateProject(@PathVariable Long projectId,
                                                             @RequestBody Map<String, String> body) {
        MetadataProjectEntity project = projectService.updateSqlDialect(projectId, body.get("sqlDialect"));
        log.info("프로젝트 SQL 방언 변경: id={}, sqlDialect={}", project.getId(), project.getSqlDialect());
        return ResponseEntity.ok(toProjectResponse(project));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> getProject(@PathVariable Long projectId) {
        MetadataProjectEntity project = projectService.getProject(projectId);
        return ResponseEntity.ok(toProjectResponse(project));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listProjects() {
        List<Map<String, Object>> projects = projectService.listProjects().stream()
                .map(this::toProjectResponse)
                .toList();
        return ResponseEntity.ok(projects);
    }

    private Map<String, Object> toProjectResponse(MetadataProjectEntity project) {
        return Map.of(
                "id", project.getId(),
                "projectKey", project.getProjectKey(),
                "projectName", project.getProjectName(),
                "projectStatus", project.getProjectStatus(),
                "description", project.getDescription() != null ? project.getDescription() : "",
                "sqlDialect", project.getSqlDialect().name()
        );
    }
}

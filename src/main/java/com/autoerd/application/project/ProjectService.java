package com.autoerd.application.project;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.exception.AppException;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final MetadataProjectRepository projectRepository;

    @Transactional
    public MetadataProjectEntity createProject(String projectKey, String projectName, String description) {
        return createProject(projectKey, projectName, description, null);
    }

    /**
     * sqlDialect 는 대소문자를 무시한다. null 또는 빈 값이면 MYSQL, 지원하지 않는 값이면 400.
     */
    @Transactional
    public MetadataProjectEntity createProject(String projectKey, String projectName, String description,
                                               String sqlDialect) {
        SqlDialect dialect = isBlank(sqlDialect) ? SqlDialect.MYSQL : parseSqlDialect(sqlDialect);

        if (projectRepository.findByProjectKeyAndIsDeletedFalse(projectKey).isPresent()) {
            throw AppException.badRequest("이미 존재하는 프로젝트 키: " + projectKey);
        }

        MetadataProjectEntity entity = MetadataProjectEntity.builder()
                .projectKey(projectKey)
                .projectName(projectName)
                .description(description)
                .projectStatus("ACTIVE")
                .sqlDialect(dialect)
                .build();

        return projectRepository.save(entity);
    }

    /**
     * 프로젝트의 SQL 방언만 바꾼다. 빈 값이나 지원하지 않는 값이면 400, 삭제된 프로젝트는 getProject 와 같이 400.
     */
    @Transactional
    public MetadataProjectEntity updateSqlDialect(Long projectId, String sqlDialect) {
        if (isBlank(sqlDialect)) {
            throw AppException.badRequest("sqlDialect는 필수입니다");
        }
        SqlDialect dialect = parseSqlDialect(sqlDialect);
        MetadataProjectEntity project = getProject(projectId);
        project.setSqlDialect(dialect);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public MetadataProjectEntity getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> !p.getIsDeleted())
                .orElseThrow(() -> AppException.badRequest("프로젝트를 찾을 수 없습니다: " + projectId));
    }

    @Transactional(readOnly = true)
    public List<MetadataProjectEntity> listProjects() {
        return projectRepository.findAll().stream()
                .filter(p -> !p.getIsDeleted())
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static SqlDialect parseSqlDialect(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(SqlDialect.values())
                .filter(d -> d.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> AppException.badRequest(
                        "지원하지 않는 sqlDialect입니다. 허용 값: " + Arrays.toString(SqlDialect.values())));
    }
}

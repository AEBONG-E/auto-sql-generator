package com.autoerd.application.project;

import com.autoerd.exception.AppException;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final MetadataProjectRepository projectRepository;

    @Transactional
    public MetadataProjectEntity createProject(String projectKey, String projectName, String description) {
        if (projectRepository.findByProjectKeyAndIsDeletedFalse(projectKey).isPresent()) {
            throw AppException.badRequest("이미 존재하는 프로젝트 키: " + projectKey);
        }

        MetadataProjectEntity entity = MetadataProjectEntity.builder()
                .projectKey(projectKey)
                .projectName(projectName)
                .description(description)
                .projectStatus("ACTIVE")
                .build();

        return projectRepository.save(entity);
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
}

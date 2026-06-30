package com.autoerd.domain.metadata.repository;

import java.util.Optional;

public interface MetadataProjectRepositoryPort {

    Optional<Long> findIdByProjectKey(String projectKey);

    boolean existsActiveProject(Long projectId);
}

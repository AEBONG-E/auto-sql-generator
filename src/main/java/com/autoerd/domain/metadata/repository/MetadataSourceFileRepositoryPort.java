package com.autoerd.domain.metadata.repository;

import java.util.List;

public interface MetadataSourceFileRepositoryPort {

    List<Long> findActiveSourceFileIdsByProjectId(Long projectId);
}

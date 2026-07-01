package com.autoerd.domain.metadata.repository;

import java.util.List;

public interface MetadataRelationRepositoryPort {

    List<Long> findCurrentRelationIdsByProjectId(Long projectId);
}

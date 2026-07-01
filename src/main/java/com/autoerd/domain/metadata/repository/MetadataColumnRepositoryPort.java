package com.autoerd.domain.metadata.repository;

import java.util.List;

public interface MetadataColumnRepositoryPort {

    List<Long> findActiveColumnIdsByTableId(Long tableId);
}

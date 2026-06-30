package com.autoerd.domain.metadata.repository;

public interface MetadataImportHistoryRepositoryPort {

    void appendHistory(Long projectId, Long sourceFileId, String importStatus, String errorMessage);
}

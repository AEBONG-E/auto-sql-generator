package com.autoerd.domain.sql.model;

public record SqlGenerationRequest(
        Long projectId,
        String query,
        SqlMetadataSnapshot metadataSnapshot
) {
}

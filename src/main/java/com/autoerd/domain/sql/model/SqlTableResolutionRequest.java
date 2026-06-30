package com.autoerd.domain.sql.model;

import java.util.List;

public record SqlTableResolutionRequest(
        Long projectId,
        String query,
        List<String> candidateTableNames
) {
}

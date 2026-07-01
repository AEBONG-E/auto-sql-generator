package com.autoerd.application.metadata.usecase;

import java.util.Set;

public record GetProjectErdQuery(
        Long projectId,
        Set<String> tableNames
) {
}

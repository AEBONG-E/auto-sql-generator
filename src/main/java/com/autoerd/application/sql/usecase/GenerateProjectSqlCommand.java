package com.autoerd.application.sql.usecase;

public record GenerateProjectSqlCommand(
        Long projectId,
        String query
) {
}

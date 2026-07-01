package com.autoerd.infrastructure.excel;

public record ExtractedSchemaFile(
        String originalFilename,
        long size,
        byte[] content
) {
}

package com.autoerd.application.metadata.usecase;

public record UploadSchemaFilePayload(
        String originalFilename,
        long size,
        byte[] content
) {
}

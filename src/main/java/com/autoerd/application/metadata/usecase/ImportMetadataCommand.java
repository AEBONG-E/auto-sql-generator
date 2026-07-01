package com.autoerd.application.metadata.usecase;

import java.util.List;

public record ImportMetadataCommand(
        Long projectId,
        List<UploadSchemaFilePayload> files
) {
}

package com.autoerd.application.metadata.usecase;

public record DeleteSourceFileCommand(
        Long projectId,
        Long sourceFileId
) {
}

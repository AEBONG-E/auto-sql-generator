package com.autoerd.controller.v1;

import com.autoerd.application.metadata.usecase.DeleteSourceFileCommand;
import com.autoerd.application.metadata.usecase.DeleteSourceFileUseCase;
import com.autoerd.application.metadata.usecase.ImportMetadataCommand;
import com.autoerd.application.metadata.usecase.ImportMetadataUseCase;
import com.autoerd.application.metadata.usecase.ResetProjectCommand;
import com.autoerd.application.metadata.usecase.ResetProjectUseCase;
import com.autoerd.application.metadata.usecase.UploadSchemaFilePayload;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import com.autoerd.validator.ExcelSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class MetadataImportController {

    private final ImportMetadataUseCase importMetadataUseCase;
    private final DeleteSourceFileUseCase deleteSourceFileUseCase;
    private final ResetProjectUseCase resetProjectUseCase;
    private final MetadataSourceFileRepository sourceFileRepository;
    private final ExcelSecurityValidator securityValidator;

    @PostMapping("/schemas:import")
    public ResponseEntity<Map<String, Object>> importSchemas(
            @PathVariable Long projectId,
            @RequestPart("files") List<MultipartFile> files) throws Exception {

        log.info("스키마 임포트 요청: projectId={}, 파일 수={}", projectId, files.size());

        List<UploadSchemaFilePayload> payloads = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            securityValidator.validate(file);
            payloads.add(new UploadSchemaFilePayload(
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getBytes()));
        }

        importMetadataUseCase.importMetadata(new ImportMetadataCommand(projectId, payloads));

        return ResponseEntity.ok(Map.of("message", "임포트 완료", "fileCount", files.size()));
    }

    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> listFiles(@PathVariable Long projectId) {
        List<MetadataSourceFileEntity> files =
                sourceFileRepository.findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(projectId);

        List<Map<String, Object>> result = files.stream()
                .map(sf -> Map.<String, Object>of(
                        "id", sf.getId(),
                        "originalFilename", sf.getOriginalFilename(),
                        "sourceType", sf.getSourceType(),
                        "importStatus", sf.getImportStatus(),
                        "tableCount", sf.getTableCount(),
                        "columnCount", sf.getColumnCount(),
                        "relationCount", sf.getRelationCount(),
                        "uploadedAt", sf.getUploadedAt().toString()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long projectId, @PathVariable Long fileId) {
        log.info("파일 삭제 요청: projectId={}, fileId={}", projectId, fileId);
        deleteSourceFileUseCase.deleteSourceFile(new DeleteSourceFileCommand(projectId, fileId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetProject(@PathVariable Long projectId) {
        log.info("프로젝트 초기화 요청: projectId={}", projectId);
        resetProjectUseCase.resetProject(new ResetProjectCommand(projectId));
        return ResponseEntity.noContent().build();
    }
}

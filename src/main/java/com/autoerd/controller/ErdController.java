package com.autoerd.controller;

import com.autoerd.dto.ErdResponse;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import com.autoerd.service.ExcelParserService;
import com.autoerd.service.SchemaAnalysisService;
import com.autoerd.service.SchemaSessionStore;
import com.autoerd.validator.ExcelSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/erd")
@RequiredArgsConstructor
public class ErdController {

    private final ExcelSecurityValidator securityValidator;
    private final ExcelParserService parserService;
    private final SchemaAnalysisService analysisService;
    private final SchemaSessionStore schemaSessionStore;

    @PostMapping("/generate")
    public ResponseEntity<ErdResponse> generate(@RequestPart("files") List<MultipartFile> files) throws Exception {
        log.info("ERD generation request: {} file(s)", files.size());

        List<String> overlappingTables = new ArrayList<>();

        for (MultipartFile file : files) {
            log.info("Processing file: {}, size={}", file.getOriginalFilename(), file.getSize());
            securityValidator.validate(file);
            List<TableSchema> schemas = parserService.parse(file);
            List<String> overwritten = schemaSessionStore.addFile(file.getOriginalFilename(), schemas);
            overlappingTables.addAll(overwritten);
        }

        List<TableSchema> allSchemas = schemaSessionStore.getAll();
        List<TableRelation> relations = analysisService.inferRelations(allSchemas);
        ErdResponse response = ErdResponse.from(allSchemas, relations, schemaSessionStore.getFileSummary());

        log.info("ERD generated: tables={}, columns={}, relations={}",
                response.getStats().getTableCount(),
                response.getStats().getColumnCount(),
                response.getStats().getRelationCount());

        if (!overlappingTables.isEmpty()) {
            String overwrittenHeader = String.join(",", overlappingTables);
            return ResponseEntity.ok()
                    .header("X-Overwritten-Tables", overwrittenHeader)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/schema")
    public ResponseEntity<ErdResponse> removeSchema(@RequestParam String filename) throws Exception {
        log.info("Remove schema request: filename={}", filename);
        schemaSessionStore.removeFile(filename);

        List<TableSchema> allSchemas = schemaSessionStore.getAll();
        if (allSchemas.isEmpty()) {
            ErdResponse emptyResponse = ErdResponse.builder()
                    .stats(ErdResponse.Stats.builder()
                            .tableCount(0)
                            .columnCount(0)
                            .relationCount(0)
                            .build())
                    .tables(List.of())
                    .relations(List.of())
                    .files(List.of())
                    .build();
            return ResponseEntity.ok(emptyResponse);
        }

        List<TableRelation> relations = analysisService.inferRelations(allSchemas);
        ErdResponse response = ErdResponse.from(allSchemas, relations, schemaSessionStore.getFileSummary());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        log.info("Reset schema session");
        schemaSessionStore.clear();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auto-ERD Generator is running on port 8880");
    }
}

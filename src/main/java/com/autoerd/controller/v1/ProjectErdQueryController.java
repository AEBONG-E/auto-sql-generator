package com.autoerd.controller.v1;

import com.autoerd.application.metadata.usecase.GetProjectErdQuery;
import com.autoerd.application.metadata.usecase.GetProjectErdUseCase;
import com.autoerd.dto.ErdResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectErdQueryController {

    private final GetProjectErdUseCase getProjectErdUseCase;

    @GetMapping("/erd")
    public ResponseEntity<ErdResponse> getErd(
            @PathVariable Long projectId,
            @RequestParam(required = false) String tableNames) {

        Set<String> tableFilter = (tableNames != null && !tableNames.isBlank())
                ? Arrays.stream(tableNames.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet())
                : null;

        log.info("ERD 조회 요청: projectId={}, tableFilter={}", projectId, tableFilter);

        ErdResponse response = getProjectErdUseCase.getProjectErd(new GetProjectErdQuery(projectId, tableFilter));
        return ResponseEntity.ok(response);
    }
}

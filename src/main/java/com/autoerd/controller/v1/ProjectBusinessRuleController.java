package com.autoerd.controller.v1;

import com.autoerd.application.businessrule.BusinessRulePreviewRequest;
import com.autoerd.application.businessrule.BusinessRulePreviewResponse;
import com.autoerd.application.businessrule.BusinessRuleRequest;
import com.autoerd.application.businessrule.BusinessRuleResponse;
import com.autoerd.application.businessrule.BusinessRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * 프로젝트 업무 규칙 API (이슈 #24). 오류 응답은 기존과 같은 {"error": "..."} 형식이다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects/{projectId}/business-rules")
@RequiredArgsConstructor
public class ProjectBusinessRuleController {

    private final BusinessRuleService businessRuleService;

    @GetMapping
    public List<BusinessRuleResponse> list(@PathVariable Long projectId,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String table) {
        return businessRuleService.list(projectId, type, table);
    }

    @GetMapping("/{ruleId}")
    public BusinessRuleResponse get(@PathVariable Long projectId, @PathVariable Long ruleId) {
        return businessRuleService.get(projectId, ruleId);
    }

    @PostMapping
    public ResponseEntity<BusinessRuleResponse> create(@PathVariable Long projectId,
                                                       @RequestBody BusinessRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessRuleService.create(projectId, request));
    }

    @PutMapping("/{ruleId}")
    public BusinessRuleResponse update(@PathVariable Long projectId, @PathVariable Long ruleId,
                                       @RequestBody BusinessRuleRequest request) {
        return businessRuleService.update(projectId, ruleId, request);
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long ruleId) {
        businessRuleService.delete(projectId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    public BusinessRulePreviewResponse preview(@PathVariable Long projectId,
                                               @RequestBody BusinessRulePreviewRequest request) {
        return businessRuleService.preview(projectId, request);
    }

    /** 본문 JSON 형식 오류(필드 타입 불일치 포함)는 이 API 에 한해 500 이 아닌 400 으로 돌려준다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("업무 규칙 요청 본문 형식 오류");
        return ResponseEntity.badRequest().body(Map.of("error", "요청 본문 형식이 올바르지 않습니다"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "경로 값 형식이 올바르지 않습니다: " + e.getName()));
    }
}

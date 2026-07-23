package com.autoerd.service;

import com.autoerd.model.TableSchema;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 레거시 세션 fallback — DB 기반 v1 파이프라인(ImportMetadataService 등)으로 대체됨.
// 유지 범위/제거 조건: docs/session-fallback-removal-plan.md
@Component
@SessionScope
public class SchemaSessionStore {

    // filename → list of TableSchema for that file
    private final LinkedHashMap<String, List<TableSchema>> fileSchemas = new LinkedHashMap<>();

    /**
     * filename 기준으로 추가/교체.
     * 반환값: 다른 파일에 이미 존재하는 테이블명 목록 (중복 경고용, 없으면 빈 리스트)
     */
    public List<String> addFile(String filename, List<TableSchema> schemas) {
        // 신규 스키마의 테이블명 중 다른 파일에 이미 존재하는 것 찾기
        List<String> overwritten = new ArrayList<>();
        if (schemas != null) {
            // 다른 파일들의 테이블명 집합 수집
            for (Map.Entry<String, List<TableSchema>> entry : fileSchemas.entrySet()) {
                if (entry.getKey().equals(filename)) continue; // 같은 파일은 제외
                for (TableSchema existing : entry.getValue()) {
                    for (TableSchema incoming : schemas) {
                        if (existing.getTableName().equals(incoming.getTableName())) {
                            if (!overwritten.contains(incoming.getTableName())) {
                                overwritten.add(incoming.getTableName());
                            }
                        }
                    }
                }
            }
        }

        // 해당 파일 슬롯 교체
        fileSchemas.put(filename, schemas != null ? schemas : List.of());
        return overwritten;
    }

    /**
     * 특정 파일의 스키마 제거
     */
    public void removeFile(String filename) {
        fileSchemas.remove(filename);
    }

    /**
     * 모든 파일의 스키마를 병합한 단일 리스트 반환.
     * 같은 테이블명은 마지막 파일이 우선 (LinkedHashMap 순서 기준).
     */
    public List<TableSchema> getAll() {
        LinkedHashMap<String, TableSchema> mergedMap = new LinkedHashMap<>();
        for (List<TableSchema> schemas : fileSchemas.values()) {
            for (TableSchema schema : schemas) {
                mergedMap.put(schema.getTableName(), schema);
            }
        }
        return new ArrayList<>(mergedMap.values());
    }

    /**
     * 파일별 테이블 수 요약: {filename → tableCount}
     */
    public Map<String, Integer> getFileSummary() {
        return fileSchemas.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size(),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    /**
     * 전체 초기화
     */
    public void clear() {
        fileSchemas.clear();
    }

    /**
     * 비어있는지 확인
     */
    public boolean isEmpty() {
        return fileSchemas.isEmpty() || fileSchemas.values().stream().allMatch(List::isEmpty);
    }

    /**
     * 하위 호환: 기존 get() 유지 (getAll() 위임)
     */
    public List<TableSchema> get() {
        return getAll();
    }
}

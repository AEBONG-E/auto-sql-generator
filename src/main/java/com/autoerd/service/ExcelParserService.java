package com.autoerd.service;

import com.autoerd.exception.AppException;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.TableSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class ExcelParserService {

    // 지원 헤더 alias 매핑
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
        Map.entry("테이블명", "tableName"),
        Map.entry("table_name", "tableName"),
        Map.entry("테이블 설명", "tableDesc"),
        Map.entry("table_comment", "tableDesc"),
        Map.entry("순번", "ordinal"),
        Map.entry("ordinal_position", "ordinal"),
        Map.entry("컬럼명", "columnName"),
        Map.entry("column_name", "columnName"),
        Map.entry("컬럼설명", "columnComment"),
        Map.entry("column_comment", "columnComment"),
        Map.entry("data type", "dataType"),
        Map.entry("data_type", "dataType"),
        Map.entry("데이터길이", "columnType"),
        Map.entry("column_type", "columnType"),
        Map.entry("key", "keyType"),
        Map.entry("column_key", "keyType"),
        Map.entry("null값여부", "nullable"),
        Map.entry("is_nullable", "nullable"),
        Map.entry("자동순번", "extra"),
        Map.entry("extra", "extra"),
        Map.entry("기본값", "defaultValue"),
        Map.entry("column_default", "defaultValue")
    );

    static {
        // Apache POI 최대 레코드 수 제한 해제 (대용량 파일 대응)
        IOUtils.setByteArrayMaxOverride(100_000_000);
    }

    public List<TableSchema> parse(MultipartFile file) throws IOException {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = detectDataSheet(wb);
            Map<String, Integer> headerIndex = buildHeaderIndex(sheet);
            validateRequiredHeaders(headerIndex);
            return buildSchemas(sheet, headerIndex);
        }
    }

    private Sheet detectDataSheet(Workbook wb) {
        Sheet best = null;
        int bestScore = 0;
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            Row header = s.getRow(0);
            if (header == null) continue;
            int score = countMatchingHeaders(header);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null || bestScore < 3) {
            throw AppException.unprocessable("유효한 테이블 정의서 시트를 찾을 수 없습니다. 헤더를 확인해주세요.");
        }
        log.debug("Selected sheet: '{}' (score={})", best.getSheetName(), bestScore);
        return best;
    }

    private int countMatchingHeaders(Row header) {
        int count = 0;
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String val = getCellString(header.getCell(i)).toLowerCase().trim();
            if (HEADER_ALIASES.containsKey(val)) count++;
        }
        return count;
    }

    private Map<String, Integer> buildHeaderIndex(Sheet sheet) {
        Map<String, Integer> index = new HashMap<>();
        Row header = sheet.getRow(0);
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String raw = getCellString(header.getCell(i)).toLowerCase().trim();
            String mapped = HEADER_ALIASES.get(raw);
            if (mapped != null) index.put(mapped, i);
        }
        return index;
    }

    private void validateRequiredHeaders(Map<String, Integer> index) {
        List<String> required = List.of("tableName", "columnName", "dataType");
        List<String> missing = required.stream().filter(r -> !index.containsKey(r)).toList();
        if (!missing.isEmpty()) {
            throw AppException.unprocessable("필수 헤더 누락: " + String.join(", ", missing));
        }
    }

    private List<TableSchema> buildSchemas(Sheet sheet, Map<String, Integer> idx) {
        Map<String, TableSchema.TableSchemaBuilder> builders = new LinkedHashMap<>();
        Map<String, List<ColumnDef>> columns = new LinkedHashMap<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String tableName = getString(row, idx, "tableName");
            if (tableName.isBlank()) continue;

            String tableDesc = getString(row, idx, "tableDesc");
            String columnName = getString(row, idx, "columnName");
            if (columnName.isBlank()) continue;

            int ordinal = getInt(row, idx, "ordinal", r);
            String dataType = getString(row, idx, "dataType");
            String columnType = getString(row, idx, "columnType");
            KeyType keyType = KeyType.from(getString(row, idx, "keyType"));
            boolean nullable = "YES".equalsIgnoreCase(getString(row, idx, "nullable"));
            boolean autoIncrement = getString(row, idx, "extra").toLowerCase().contains("auto_increment");
            String defaultValue = getString(row, idx, "defaultValue");

            builders.putIfAbsent(tableName, TableSchema.builder()
                    .tableName(tableName)
                    .tableDescription(tableDesc));
            columns.computeIfAbsent(tableName, k -> new ArrayList<>())
                    .add(ColumnDef.builder()
                            .ordinal(ordinal)
                            .columnName(columnName)
                            .columnComment(getString(row, idx, "columnComment"))
                            .dataType(dataType.isEmpty() ? "varchar" : dataType)
                            .columnType(columnType)
                            .keyType(keyType)
                            .nullable(nullable)
                            .autoIncrement(autoIncrement)
                            .defaultValue(defaultValue)
                            .build());
        }

        List<TableSchema> result = new ArrayList<>();
        for (String tableName : builders.keySet()) {
            result.add(builders.get(tableName)
                    .columns(columns.getOrDefault(tableName, List.of()))
                    .build());
        }
        log.debug("Parsed {} tables", result.size());
        return result;
    }

    private String getString(Row row, Map<String, Integer> idx, String key) {
        if (!idx.containsKey(key)) return "";
        return getCellString(row.getCell(idx.get(key)));
    }

    private int getInt(Row row, Map<String, Integer> idx, String key, int fallback) {
        String val = getString(row, idx, key);
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return fallback; }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}

package com.autoerd.testsupport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

/**
 * 테스트용 xlsx 바이트 생성 헬퍼.
 * ExcelSchemaExtractorImpl.HEADER_ALIASES와 동일한 헤더 별칭을 사용해 실제 업로드 파일을 흉내낸다.
 */
public final class ExcelFixtures {

    public static final String[] KOREAN_HEADERS = {
            "테이블명", "테이블 설명", "순번", "컬럼명", "컬럼설명",
            "data type", "데이터길이", "key", "null값여부", "자동순번", "기본값"
    };

    public static final String[] ENGLISH_HEADERS = {
            "table_name", "table_comment", "ordinal_position", "column_name", "column_comment",
            "data_type", "column_type", "column_key", "is_nullable", "extra", "column_default"
    };

    private ExcelFixtures() {
    }

    public static byte[] workbook(String[] headers, Object[]... rows) {
        return workbook(headers, Arrays.asList(rows));
    }

    private static byte[] workbook(String[] headers, List<Object[]> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (Object[] row : rows) {
                Row dataRow = sheet.createRow(r++);
                for (int i = 0; i < row.length; i++) {
                    setCell(dataRow.createCell(i), row[i]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 표준 한글 헤더 + 표준 컬럼 순서(테이블명, 설명, 순번, 컬럼명, 컬럼설명, dataType, columnType, key, nullable, extra, default)로 워크북 생성 */
    public static byte[] standardKorean(Object[]... rows) {
        return workbook(KOREAN_HEADERS, rows);
    }

    public static byte[] standardEnglish(Object[]... rows) {
        return workbook(ENGLISH_HEADERS, rows);
    }

    private static void setCell(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}

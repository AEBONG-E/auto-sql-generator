package com.autoerd.validator;

import com.autoerd.exception.AppException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class ExcelSecurityValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    static {
        // ZIP Bomb 방어 — 앱 시작 시 한 번만 설정
        ZipSecureFile.setMinInflateRatio(0.001);
        ZipSecureFile.setMaxEntrySize(50 * 1024 * 1024L);
    }

    /**
     * 파일 업로드 보안 검증
     * ※ Magic bytes 검사는 스트림을 직접 열지 않고 파서(Apache POI)에 위임한다.
     *    — try-with-resources로 getInputStream()을 close()하면 Tomcat이
     *      이후 파서의 getInputStream() 호출에서 닫힌 스트림을 반환해
     *      HTTP 응답 없이 연결을 끊는 net::ERR 오류가 발생하기 때문.
     */
    public void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("파일이 비어 있습니다");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw AppException.badRequest("파일 크기는 10MB를 초과할 수 없습니다");
        }

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw AppException.badRequest(".xlsx 파일만 업로드 가능합니다");
        }
        // 잘못된 파일 형식은 Apache POI 파싱 단계에서 예외로 감지됨
    }
}

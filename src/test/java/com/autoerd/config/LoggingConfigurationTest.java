package com.autoerd.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationTest {

    private static final String PROFILE_KEY = "spring.config.activate.on-profile";

    @Test
    @DisplayName("공통 문서와 prod 문서에는 DEBUG/TRACE 로그 레벨이 없고, DEBUG 는 local 전용 문서에만 있다")
    void debugLoggingIsLocalOnly() throws Exception {
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        for (PropertySource<?> document : documents) {
            EnumerablePropertySource<?> source = (EnumerablePropertySource<?>) document;
            String profile = String.valueOf(source.getProperty(PROFILE_KEY));
            for (String name : source.getPropertyNames()) {
                if (!name.startsWith("logging.level.")) {
                    continue;
                }
                String level = String.valueOf(source.getProperty(name)).trim().toUpperCase();
                if (level.equals("DEBUG") || level.equals("TRACE")) {
                    assertThat(profile).as("%s=%s 는 local 프로파일 문서에만 둔다", name, level).isEqualTo("local");
                }
            }
        }

        assertThat(documents)
                .noneMatch(d -> d.containsProperty("logging.level.org.springframework.web")
                        && !"local".equals(String.valueOf(d.getProperty(PROFILE_KEY))));
    }
}

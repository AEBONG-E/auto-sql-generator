package com.autoerd.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOllamaIdleConfigurationTest {

    private static final String PROFILE_KEY = "spring.config.activate.on-profile";
    private static final String WARMUP_KEY = "app.llm.warmup.enabled";
    private static final String KEEP_ALIVE_KEY = "spring.ai.ollama.chat.options.keep-alive";

    @Test
    @DisplayName("local 프로파일 문서만 warm-up 을 끄고 keep-alive 를 1m 으로 두며, 공통 문서 값은 유지한다")
    void localProfileTurnsOffWarmupAndShortensKeepAlive() throws Exception {
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        PropertySource<?> common = documents.get(0);
        assertThat(common.getProperty(PROFILE_KEY)).isNull();
        assertThat(String.valueOf(common.getProperty(WARMUP_KEY))).isEqualTo("true");
        assertThat(String.valueOf(common.getProperty(KEEP_ALIVE_KEY))).isEqualTo("5m");

        PropertySource<?> local = documents.stream()
                .filter(d -> "local".equals(String.valueOf(d.getProperty(PROFILE_KEY))))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(local.getProperty(WARMUP_KEY))).isEqualTo("false");
        assertThat(String.valueOf(local.getProperty(KEEP_ALIVE_KEY))).isEqualTo("1m");

        assertThat(documents)
                .filteredOn(d -> d != common && d != local)
                .noneMatch(d -> d.containsProperty(WARMUP_KEY) || d.containsProperty(KEEP_ALIVE_KEY));
    }
}

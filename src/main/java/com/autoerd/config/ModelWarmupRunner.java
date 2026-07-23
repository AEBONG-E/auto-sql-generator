package com.autoerd.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 완료(ApplicationReadyEvent) 시점에 Ollama gemma4 모델을 백그라운드로 프리로드(warm-up)한다.
 *
 * <p>목적: {@code keep-alive: "5m"}로 재로드 병목은 해소됐으나, 애플리케이션 기동 직후 첫 SQL 생성
 * 요청은 여전히 콜드 스타트(모델 로딩 ~3초)를 겪는다. 기동 완료 후 경량 프롬프트 1회를 미리 발송해
 * 모델을 메모리에 적재해 두면 첫 사용자 요청이 이미 웜 상태가 된다.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>비블로킹 — 별도 데몬 스레드에서 실행하여 앱 기동/이벤트 루프를 지연시키지 않는다.</li>
 *   <li>실패 격리 — Ollama 미기동/타임아웃 시 경고 로그만 남기고 앱은 정상 동작한다.</li>
 *   <li>부가 기능 — 기존 2단계 LLM 경로({@code OllamaSqlService})와 {@code validNames} 필터를 건드리지 않는다.</li>
 *   <li>설정화 — {@code app.llm.warmup.enabled=false}로 끌 수 있다(로컬/CI에서 Ollama 없을 때).</li>
 * </ul>
 *
 * <p>워밍업 호출은 {@code application.yml}의 기존 Ollama 옵션(특히 {@code keep-alive: "5m"})을 그대로
 * 상속한다. 옵션을 오버라이드하지 않으므로 워밍업으로 적재된 모델이 곧바로 언로드되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelWarmupRunner {

    private final ChatClient chatClient;

    /** 워밍업 on/off. 로컬/CI 등 Ollama가 없는 환경에서는 false로 끌 수 있다. 기본값 true. */
    @Value("${app.llm.warmup.enabled:true}")
    private boolean warmupEnabled;

    /** 모델 적재만을 위한 경량 프롬프트. 응답 내용은 사용하지 않는다. */
    @Value("${app.llm.warmup.prompt:ping}")
    private String warmupPrompt;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!warmupEnabled) {
            log.info("LLM warm-up disabled (app.llm.warmup.enabled=false) — 모델 프리로드를 건너뜁니다.");
            return;
        }
        // 앱 기동을 블로킹하지 않도록 데몬 스레드로 분리. 실패해도 JVM 종료를 막지 않는다.
        Thread warmupThread = new Thread(this::runWarmup, "llm-model-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    private void runWarmup() {
        long startedAt = System.currentTimeMillis();
        try {
            log.info("LLM warm-up 시작 — gemma4 모델을 메모리에 프리로드합니다...");
            String response = chatClient.prompt()
                    .user(warmupPrompt)
                    .call()
                    .content();
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("LLM warm-up 완료 ({} ms) — 모델이 웜 상태입니다. 첫 SQL 생성 요청은 콜드 로딩 없이 응답합니다. "
                    + "(응답 길이={})", elapsedMs, response == null ? 0 : response.length());
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            // 실패 격리: Ollama 미기동/타임아웃 등은 경고만 남기고 서비스는 정상 동작.
            // 첫 사용자 요청이 콜드 스타트를 겪을 뿐, 기능 자체는 영향받지 않는다.
            log.warn("LLM warm-up 실패 ({} ms, Ollama 미기동 가능성). 서비스는 정상 동작하며 첫 요청은 "
                    + "콜드 로딩을 겪습니다. 원인: {}", elapsedMs, e.getMessage());
        }
    }
}

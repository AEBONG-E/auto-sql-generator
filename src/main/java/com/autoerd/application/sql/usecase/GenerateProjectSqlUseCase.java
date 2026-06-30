package com.autoerd.application.sql.usecase;

import reactor.core.publisher.Flux;

public interface GenerateProjectSqlUseCase {

    Flux<String> generateSql(GenerateProjectSqlCommand command);
}

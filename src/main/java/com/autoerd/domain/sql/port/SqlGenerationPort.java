package com.autoerd.domain.sql.port;

import com.autoerd.domain.sql.model.SqlGenerationRequest;
import reactor.core.publisher.Flux;

public interface SqlGenerationPort {

    Flux<String> generate(SqlGenerationRequest request);
}

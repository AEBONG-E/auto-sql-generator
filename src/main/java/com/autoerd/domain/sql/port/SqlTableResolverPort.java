package com.autoerd.domain.sql.port;

import com.autoerd.domain.sql.model.SqlTableResolutionRequest;

import java.util.List;

public interface SqlTableResolverPort {

    List<String> resolveRelevantTableNames(SqlTableResolutionRequest request);
}

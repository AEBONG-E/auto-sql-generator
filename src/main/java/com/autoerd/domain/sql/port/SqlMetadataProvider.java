package com.autoerd.domain.sql.port;

import com.autoerd.domain.sql.model.SqlMetadataSnapshot;

public interface SqlMetadataProvider {

    SqlMetadataSnapshot getCurrentSnapshot(Long projectId);
}

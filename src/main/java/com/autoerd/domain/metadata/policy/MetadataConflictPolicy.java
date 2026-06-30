package com.autoerd.domain.metadata.policy;

import java.util.List;

public interface MetadataConflictPolicy {

    List<String> resolveOverwrittenTableNames(List<String> existingActiveTables, List<String> incomingTables);
}

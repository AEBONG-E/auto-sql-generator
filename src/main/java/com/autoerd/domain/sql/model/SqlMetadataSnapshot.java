package com.autoerd.domain.sql.model;

import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;

import java.util.List;

public record SqlMetadataSnapshot(
        List<TableSchema> tables,
        List<TableRelation> relations
) {
}

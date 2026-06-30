package com.autoerd.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TableSchema {
    private final String tableName;
    private final String tableDescription;
    private final List<ColumnDef> columns;
}

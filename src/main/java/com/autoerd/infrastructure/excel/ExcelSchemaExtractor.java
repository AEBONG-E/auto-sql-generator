package com.autoerd.infrastructure.excel;

import com.autoerd.model.TableSchema;

import java.util.List;

public interface ExcelSchemaExtractor {

    List<TableSchema> extract(ExtractedSchemaFile file) throws Exception;
}

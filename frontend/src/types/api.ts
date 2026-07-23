/**
 * 백엔드 DTO와 수동 동기화되는 타입 계약. 자동 codegen 파이프라인이 아직 없으므로
 * 백엔드 응답 형태가 바뀌면 아래 각 타입 옆 소스 경로를 먼저 확인해 갱신할 것.
 */

/** src/main/java/com/autoerd/controller/v1/ProjectController.java#toProjectResponse */
export interface Project {
  id: number
  projectKey: string
  projectName: string
  projectStatus: string
  description: string
}

/** src/main/java/com/autoerd/controller/v1/MetadataImportController.java#listFiles */
export interface ProjectFile {
  id: number
  originalFilename: string
  sourceType: string
  importStatus: string
  tableCount: number
  columnCount: number
  relationCount: number
  uploadedAt: string
}

/** src/main/java/com/autoerd/dto/ErdResponse.java#ColumnInfo */
export interface ColumnInfo {
  ordinal: number
  columnName: string
  columnComment: string | null
  dataType: string
  columnType: string
  keyType: 'PRI' | 'MUL' | 'UNI' | 'NONE' | string
  nullable: boolean
  autoIncrement: boolean
  defaultValue: string | null
}

/** src/main/java/com/autoerd/dto/ErdResponse.java#TableInfo */
export interface TableInfo {
  tableName: string
  tableDescription: string | null
  columnCount: number
  columns: ColumnInfo[]
}

/** src/main/java/com/autoerd/dto/ErdResponse.java#RelationInfo */
export interface RelationInfo {
  fromTable: string
  fromColumn: string
  toTable: string
  relationType: string
}

/** src/main/java/com/autoerd/dto/ErdResponse.java#Stats */
export interface ErdStats {
  tableCount: number
  columnCount: number
  relationCount: number
}

/** src/main/java/com/autoerd/dto/ErdResponse.java */
export interface ErdResponse {
  stats: ErdStats
  tables: TableInfo[]
  relations: RelationInfo[]
  files: { filename: string; tableCount: number }[]
}

/** src/main/java/com/autoerd/exception/GlobalExceptionHandler.java */
export interface ApiErrorBody {
  error?: string
  message?: string
}

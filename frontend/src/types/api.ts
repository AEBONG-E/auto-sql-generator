export interface Project {
  id: number
  projectKey: string
  projectName: string
  projectStatus: string
  description: string
}

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

export interface TableInfo {
  tableName: string
  tableDescription: string | null
  columnCount: number
  columns: ColumnInfo[]
}

export interface RelationInfo {
  fromTable: string
  fromColumn: string
  toTable: string
  relationType: string
}

export interface ErdStats {
  tableCount: number
  columnCount: number
  relationCount: number
}

export interface ErdResponse {
  stats: ErdStats
  tables: TableInfo[]
  relations: RelationInfo[]
  files: { filename: string; tableCount: number }[]
}

export interface ApiErrorBody {
  error?: string
  message?: string
}

import type { SqlDialect } from '../types/api'

/** 화면에 보여 줄 순서와 표시 이름. 값은 백엔드 SqlDialect enum 의 name() 과 같다. */
export const SQL_DIALECT_OPTIONS: { value: SqlDialect; label: string }[] = [
  { value: 'MSSQL', label: 'SQL Server' },
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'POSTGRESQL', label: 'PostgreSQL' },
]

export function sqlDialectLabel(dialect: SqlDialect | undefined): string {
  return SQL_DIALECT_OPTIONS.find((o) => o.value === dialect)?.label ?? dialect ?? '-'
}

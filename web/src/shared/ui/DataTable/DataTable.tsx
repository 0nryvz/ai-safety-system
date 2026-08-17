import type { ReactNode } from 'react'
import './DataTable.css'

export interface DataTableColumn<T> {
  key: string
  header: string
  render: (item: T) => ReactNode
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  data: T[]
  getRowKey: (item: T) => string
  emptyMessage?: string
}

function DataTable<T>({
  columns,
  data,
  getRowKey,
  emptyMessage = 'Kayıt bulunamadı.',
}: DataTableProps<T>) {
  return (
    <div className="ui-data-table-wrapper">
      <table className="ui-data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {data.length === 0 ? (
            <tr>
              <td className="ui-data-table__empty" colSpan={columns.length}>
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((item) => (
              <tr key={getRowKey(item)}>
                {columns.map((column) => (
                  <td key={column.key}>{column.render(item)}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}

export default DataTable

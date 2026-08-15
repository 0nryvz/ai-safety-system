import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import DataTable, { type DataTableColumn } from './DataTable'

interface TestRow {
  id: string
  name: string
  status: string
}

const columns: DataTableColumn<TestRow>[] = [
  {
    key: 'name',
    header: 'Ad',
    render: (item) => item.name,
  },
  {
    key: 'status',
    header: 'Durum',
    render: (item) => item.status,
  },
]

afterEach(() => {
  cleanup()
})

describe('DataTable', () => {
  it('renders column headers', () => {
    render(<DataTable columns={columns} data={[]} getRowKey={(item) => item.id} />)

    expect(screen.getByRole('columnheader', { name: 'Ad' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Durum' })).toBeInTheDocument()
  })

  it('renders table rows', () => {
    const data: TestRow[] = [
      {
        id: '1',
        name: 'Kamera 1',
        status: 'ONLINE',
      },
      {
        id: '2',
        name: 'Kamera 2',
        status: 'OFFLINE',
      },
    ]

    render(<DataTable columns={columns} data={data} getRowKey={(item) => item.id} />)

    expect(screen.getByText('Kamera 1')).toBeInTheDocument()
    expect(screen.getByText('ONLINE')).toBeInTheDocument()
    expect(screen.getByText('Kamera 2')).toBeInTheDocument()
    expect(screen.getByText('OFFLINE')).toBeInTheDocument()
  })

  it('renders the default empty state when data is empty', () => {
    render(<DataTable columns={columns} data={[]} getRowKey={(item) => item.id} />)

    expect(screen.getByText('Kayıt bulunamadı.')).toBeInTheDocument()
  })

  it('supports a custom empty message', () => {
    render(
      <DataTable
        columns={columns}
        data={[]}
        getRowKey={(item) => item.id}
        emptyMessage="İhlal kaydı bulunamadı."
      />,
    )

    expect(screen.getByText('İhlal kaydı bulunamadı.')).toBeInTheDocument()
  })
})

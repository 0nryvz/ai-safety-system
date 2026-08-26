import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DateRange from './DateRange'

afterEach(() => {
  cleanup()
})

describe('DateRange', () => {
  it('renders start and end date inputs', () => {
    render(
      <DateRange startDate="" endDate="" onStartDateChange={vi.fn()} onEndDateChange={vi.fn()} />,
    )

    expect(screen.getByLabelText('Başlangıç tarihi')).toHaveAttribute('type', 'date')
    expect(screen.getByLabelText('Bitiş tarihi')).toHaveAttribute('type', 'date')
  })

  it('renders the provided date values', () => {
    render(
      <DateRange
        startDate="2026-08-01"
        endDate="2026-08-15"
        onStartDateChange={vi.fn()}
        onEndDateChange={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Başlangıç tarihi')).toHaveValue('2026-08-01')
    expect(screen.getByLabelText('Bitiş tarihi')).toHaveValue('2026-08-15')
  })

  it('notifies when the start and end dates change', () => {
    const handleStartDateChange = vi.fn()
    const handleEndDateChange = vi.fn()

    render(
      <DateRange
        startDate=""
        endDate=""
        onStartDateChange={handleStartDateChange}
        onEndDateChange={handleEndDateChange}
      />,
    )

    fireEvent.change(screen.getByLabelText('Başlangıç tarihi'), {
      target: {
        value: '2026-08-01',
      },
    })

    fireEvent.change(screen.getByLabelText('Bitiş tarihi'), {
      target: {
        value: '2026-08-15',
      },
    })

    expect(handleStartDateChange).toHaveBeenCalledWith('2026-08-01')
    expect(handleEndDateChange).toHaveBeenCalledWith('2026-08-15')
  })

  it('supports custom labels', () => {
    render(
      <DateRange
        startDate=""
        endDate=""
        startLabel="İlk tarih"
        endLabel="Son tarih"
        onStartDateChange={vi.fn()}
        onEndDateChange={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('İlk tarih')).toBeInTheDocument()
    expect(screen.getByLabelText('Son tarih')).toBeInTheDocument()
  })

  it('disables both inputs when disabled', () => {
    render(
      <DateRange
        startDate=""
        endDate=""
        disabled
        onStartDateChange={vi.fn()}
        onEndDateChange={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Başlangıç tarihi')).toBeDisabled()
    expect(screen.getByLabelText('Bitiş tarihi')).toBeDisabled()
  })
})

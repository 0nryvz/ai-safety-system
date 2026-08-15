import { useId } from 'react'
import Input from '../Input/Input'
import './DateRange.css'

interface DateRangeProps {
  startDate: string
  endDate: string
  onStartDateChange: (value: string) => void
  onEndDateChange: (value: string) => void
  startLabel?: string
  endLabel?: string
  disabled?: boolean
}

function DateRange({
  startDate,
  endDate,
  onStartDateChange,
  onEndDateChange,
  startLabel = 'Başlangıç tarihi',
  endLabel = 'Bitiş tarihi',
  disabled = false,
}: DateRangeProps) {
  const generatedId = useId()
  const startInputId = `${generatedId}-start`
  const endInputId = `${generatedId}-end`

  return (
    <div className="ui-date-range" role="group" aria-label="Tarih aralığı">
      <Input
        id={startInputId}
        label={startLabel}
        type="date"
        value={startDate}
        disabled={disabled}
        onChange={(event) => onStartDateChange(event.target.value)}
      />

      <Input
        id={endInputId}
        label={endLabel}
        type="date"
        value={endDate}
        disabled={disabled}
        onChange={(event) => onEndDateChange(event.target.value)}
      />
    </div>
  )
}

export default DateRange

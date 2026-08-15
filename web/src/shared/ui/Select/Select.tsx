import { useId, type SelectHTMLAttributes } from 'react'
import './Select.css'

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  error?: string
}

function Select({ label, error, id, className = '', children, ...props }: SelectProps) {
  const generatedId = useId()
  const selectId = id ?? props.name ?? generatedId
  const errorId = `${selectId}-error`
  const classes = ['ui-select', error ? 'ui-select--error' : '', className]
    .filter(Boolean)
    .join(' ')

  return (
    <div className="ui-select-field">
      <label htmlFor={selectId}>{label}</label>

      <select
        id={selectId}
        className={classes}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : props['aria-describedby']}
        {...props}
      >
        {children}
      </select>

      {error && (
        <p id={errorId} className="ui-select-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export default Select

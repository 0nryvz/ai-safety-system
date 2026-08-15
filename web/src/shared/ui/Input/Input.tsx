import { useId, type InputHTMLAttributes } from 'react'
import './Input.css'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
}

function Input({ label, error, id, className = '', ...props }: InputProps) {
  const generatedId = useId()
  const inputId = id ?? props.name ?? generatedId
  const errorId = `${inputId}-error`
  const classes = ['ui-input', error ? 'ui-input--error' : '', className].filter(Boolean).join(' ')

  return (
    <div className="ui-input-field">
      <label htmlFor={inputId}>{label}</label>

      <input
        id={inputId}
        className={classes}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : props['aria-describedby']}
        {...props}
      />

      {error && (
        <p id={errorId} className="ui-input-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export default Input

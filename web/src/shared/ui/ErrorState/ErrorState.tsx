import type { ReactNode } from 'react'
import './ErrorState.css'

interface ErrorStateProps {
  title: string
  description?: string
  action?: ReactNode
}

function ErrorState({ title, description, action }: ErrorStateProps) {
  return (
    <section className="ui-error-state" role="alert">
      <h3>{title}</h3>

      {description && <p>{description}</p>}

      {action && <div className="ui-error-state__action">{action}</div>}
    </section>
  )
}

export default ErrorState

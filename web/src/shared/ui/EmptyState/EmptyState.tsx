import type { ReactNode } from 'react'
import './EmptyState.css'

interface EmptyStateProps {
  title: string
  description?: string
  action?: ReactNode
}

function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <section className="ui-empty-state" aria-live="polite">
      <h3>{title}</h3>

      {description && <p>{description}</p>}

      {action && <div className="ui-empty-state__action">{action}</div>}
    </section>
  )
}

export default EmptyState

import type { ReactNode } from 'react'
import './StatusBadge.css'

type StatusBadgeVariant = 'neutral' | 'success' | 'warning' | 'critical' | 'info'

interface StatusBadgeProps {
  children: ReactNode
  variant?: StatusBadgeVariant
}

function StatusBadge({ children, variant = 'neutral' }: StatusBadgeProps) {
  return <span className={`ui-status-badge ui-status-badge--${variant}`}>{children}</span>
}

export default StatusBadge

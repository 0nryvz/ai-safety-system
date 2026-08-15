import { useId } from 'react'
import Button from '../Button/Button'
import './ConfirmDialog.css'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  confirmVariant?: 'primary' | 'danger'
  onConfirm: () => void
  onCancel: () => void
}

function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Onayla',
  cancelLabel = 'İptal',
  confirmVariant = 'primary',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const generatedId = useId()
  const titleId = `${generatedId}-title`
  const descriptionId = `${generatedId}-description`

  if (!open) {
    return null
  }

  return (
    <div className="ui-confirm-dialog-backdrop">
      <section
        className="ui-confirm-dialog"
        role="dialog"
        aria-modal="true"
      >
        <div className="ui-confirm-dialog__content">
          <h2 id={titleId}>{title}</h2>

          {description && <p id={descriptionId}>{description}</p>}
        </div>

        <div className="ui-confirm-dialog__actions">
          <Button type="button" variant="secondary" onClick={onCancel}>
            {cancelLabel}
          </Button>

          <Button type="button" variant={confirmVariant} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </section>
    </div>
  )
}

export default ConfirmDialog

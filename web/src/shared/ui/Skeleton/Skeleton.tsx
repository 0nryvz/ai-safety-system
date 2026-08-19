import './Skeleton.css'

interface SkeletonProps {
  width?: string
  height?: string
  borderRadius?: string
}

function Skeleton({
  width = '100%',
  height = '16px',
  borderRadius = 'var(--radius-md)',
}: SkeletonProps) {
  return (
    <span
      className="ui-skeleton"
      aria-hidden="true"
      style={{
        width,
        height,
        borderRadius,
      }}
    />
  )
}

export default Skeleton

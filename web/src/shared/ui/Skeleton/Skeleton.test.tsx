import { cleanup, render } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import Skeleton from './Skeleton'

afterEach(() => {
  cleanup()
})

describe('Skeleton', () => {
  it('renders as a hidden decorative loading element', () => {
    const { container } = render(<Skeleton />)

    const skeleton = container.querySelector('.ui-skeleton')

    expect(skeleton).toBeInTheDocument()
    expect(skeleton).toHaveAttribute('aria-hidden', 'true')
  })

  it('supports custom dimensions', () => {
    const { container } = render(<Skeleton width="120px" height="24px" />)

    const skeleton = container.querySelector('.ui-skeleton')

    expect(skeleton).toHaveStyle({
      width: '120px',
      height: '24px',
    })
  })
})

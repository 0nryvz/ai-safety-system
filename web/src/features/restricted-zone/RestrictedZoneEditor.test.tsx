import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RestrictedZoneEditor from './RestrictedZoneEditor'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('RestrictedZoneEditor', () => {
  it('renders the drawing area', () => {
    render(<RestrictedZoneEditor points={[]} onChange={vi.fn()} />)

    expect(
      screen.getByRole('application', {
        name: 'Yasaklı alan çizim alanı',
      }),
    ).toBeInTheDocument()
  })

  it('adds a normalized point when the drawing area is clicked', () => {
    const onChange = vi.fn()

    render(<RestrictedZoneEditor points={[]} onChange={onChange} />)

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 250,
      clientY: 200,
    })

    expect(onChange).toHaveBeenCalledWith([
      {
        x: 0.25,
        y: 0.4,
      },
    ])
  })

  it('appends a new point to existing points', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[
          {
            x: 0.1,
            y: 0.2,
          },
        ]}
        onChange={onChange}
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 500,
      clientY: 250,
    })

    expect(onChange).toHaveBeenCalledWith([
      {
        x: 0.1,
        y: 0.2,
      },
      {
        x: 0.5,
        y: 0.5,
      },
    ])
  })

  it('moves an existing polygon point by dragging', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[
          { x: 0.1, y: 0.2 },
          { x: 0.8, y: 0.2 },
          { x: 0.8, y: 0.8 },
        ]}
        onChange={onChange}
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.mouseDown(screen.getByTestId('restricted-zone-point-0'))

    fireEvent.mouseMove(editor, {
      clientX: 300,
      clientY: 250,
    })

    fireEvent.mouseUp(editor)

    expect(onChange).toHaveBeenCalledWith([
      { x: 0.3, y: 0.5 },
      { x: 0.8, y: 0.2 },
      { x: 0.8, y: 0.8 },
    ])
  })

  it('renders a placeholder when no reference image is available', () => {
    render(<RestrictedZoneEditor points={[]} onChange={vi.fn()} />)

    expect(screen.getByLabelText('Referans görüntü bekleniyor')).toBeInTheDocument()
  })

  it('renders the reference image when an image url is provided', () => {
    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={vi.fn()}
        imageUrl="https://example.com/reference.jpg"
      />,
    )

    expect(screen.getByLabelText('Kamera referans görüntüsü')).toHaveAttribute(
      'src',
      'https://example.com/reference.jpg',
    )
  })
})

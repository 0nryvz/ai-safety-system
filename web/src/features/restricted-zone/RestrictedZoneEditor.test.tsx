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

  it('normalizes clicks against the rendered reference image instead of the container', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={onChange}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    const image = screen.getByLabelText('Kamera referans görüntüsü')

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1600,
      height: 900,
      top: 0,
      left: 0,
      right: 1600,
      bottom: 900,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    vi.spyOn(image, 'getBoundingClientRect').mockReturnValue({
      width: 1200,
      height: 900,
      top: 0,
      left: 200,
      right: 1400,
      bottom: 900,
      x: 200,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 500,
      clientY: 450,
    })

    expect(onChange).toHaveBeenCalledWith([
      {
        x: 0.25,
        y: 0.5,
      },
    ])
  })

  it('ignores clicks inside the container letterbox area', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={onChange}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    const image = screen.getByLabelText('Kamera referans görüntüsü')

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1600,
      height: 900,
      top: 0,
      left: 0,
      right: 1600,
      bottom: 900,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    vi.spyOn(image, 'getBoundingClientRect').mockReturnValue({
      width: 1200,
      height: 900,
      top: 0,
      left: 200,
      right: 1400,
      bottom: 900,
      x: 200,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 100,
      clientY: 450,
    })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('normalizes clicks correctly for a portrait reference image', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={onChange}
        imageUrl="https://example.com/reference-portrait.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    const image = screen.getByLabelText('Kamera referans görüntüsü')

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1600,
      height: 900,
      top: 0,
      left: 0,
      right: 1600,
      bottom: 900,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    vi.spyOn(image, 'getBoundingClientRect').mockReturnValue({
      width: 506.25,
      height: 900,
      top: 0,
      left: 546.875,
      right: 1053.125,
      bottom: 900,
      x: 546.875,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 800,
      clientY: 225,
    })

    expect(onChange).toHaveBeenCalledWith([
      {
        x: 0.5,
        y: 0.25,
      },
    ])
  })

  it('aligns the polygon overlay with the rendered reference image rect', () => {
    render(
      <RestrictedZoneEditor
        points={[
          { x: 0.25, y: 0.25 },
          { x: 0.75, y: 0.25 },
          { x: 0.75, y: 0.75 },
        ]}
        onChange={vi.fn()}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    const image = screen.getByLabelText('Kamera referans görüntüsü')
    const overlay = screen.getByTestId('restricted-zone-overlay')

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1600,
      height: 900,
      top: 0,
      left: 0,
      right: 1600,
      bottom: 900,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    vi.spyOn(image, 'getBoundingClientRect').mockReturnValue({
      width: 1200,
      height: 900,
      top: 0,
      left: 200,
      right: 1400,
      bottom: 900,
      x: 200,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.load(image)

    expect(overlay).toHaveStyle({
      left: '200px',
      top: '0px',
      width: '1200px',
      height: '900px',
    })
  })

  it('keeps the polygon aligned after the reference image is resized', () => {
    render(
      <RestrictedZoneEditor
        points={[
          { x: 0.25, y: 0.25 },
          { x: 0.75, y: 0.25 },
          { x: 0.75, y: 0.75 },
        ]}
        onChange={vi.fn()}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    const image = screen.getByLabelText('Kamera referans görüntüsü')
    const overlay = screen.getByTestId('restricted-zone-overlay')

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1600,
      height: 900,
      top: 0,
      left: 0,
      right: 1600,
      bottom: 900,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    const imageRectSpy = vi
      .spyOn(image, 'getBoundingClientRect')
      .mockReturnValueOnce({
        width: 1200,
        height: 900,
        top: 0,
        left: 200,
        right: 1400,
        bottom: 900,
        x: 200,
        y: 0,
        toJSON: () => {},
      })
      .mockReturnValue({
        width: 800,
        height: 600,
        top: 150,
        left: 400,
        right: 1200,
        bottom: 750,
        x: 400,
        y: 150,
        toJSON: () => {},
      })

    fireEvent.load(image)

    expect(overlay).toHaveStyle({
      left: '200px',
      top: '0px',
      width: '1200px',
      height: '900px',
    })

    fireEvent(window, new Event('resize'))

    expect(imageRectSpy).toHaveBeenCalled()

    expect(overlay).toHaveStyle({
      left: '400px',
      top: '150px',
      width: '800px',
      height: '600px',
    })
  })
})

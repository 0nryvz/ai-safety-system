import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RestrictedZoneEditor from './RestrictedZoneEditor'
import { getImageContentRect } from './polygonUtils'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function createDomRect(width: number, height: number, left = 0, top = 0) {
  return {
    width,
    height,
    top,
    left,
    right: left + width,
    bottom: top + height,
    x: left,
    y: top,
    toJSON: () => {},
  }
}

function mockBoxRect(element: HTMLElement, width: number, height: number, left = 0, top = 0) {
  vi.spyOn(element, 'getBoundingClientRect').mockReturnValue(
    createDomRect(width, height, left, top),
  )
}

function mockLoadedReferenceImage(
  editor: HTMLElement,
  image: HTMLElement,
  options: {
    boxWidth: number
    boxHeight: number
    naturalWidth: number
    naturalHeight: number
    left?: number
    top?: number
  },
) {
  const left = options.left ?? 0
  const top = options.top ?? 0
  const rect = createDomRect(options.boxWidth, options.boxHeight, left, top)

  vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue(rect)
  vi.spyOn(image, 'getBoundingClientRect').mockReturnValue(rect)
  vi.spyOn(image as HTMLImageElement, 'naturalWidth', 'get').mockReturnValue(options.naturalWidth)
  vi.spyOn(image as HTMLImageElement, 'naturalHeight', 'get').mockReturnValue(
    options.naturalHeight,
  )

  fireEvent.load(image)

  return getImageContentRect(rect, options.naturalWidth, options.naturalHeight)
}

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

    mockBoxRect(editor, 1000, 500)

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

    mockBoxRect(editor, 1000, 500)

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

    mockBoxRect(editor, 1000, 500)

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

  it('does not create a point before the reference image has loaded', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={onChange}
        imageUrl="https://example.com/reference-unloaded.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })
    const image = screen.getByLabelText('Kamera referans görüntüsü')

    mockBoxRect(editor, 1600, 900)
    mockBoxRect(image, 1600, 900)
    vi.spyOn(image as HTMLImageElement, 'naturalWidth', 'get').mockReturnValue(0)
    vi.spyOn(image as HTMLImageElement, 'naturalHeight', 'get').mockReturnValue(0)

    fireEvent.click(editor, {
      clientX: 800,
      clientY: 450,
    })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('normalizes 16:9 image clicks without letterbox offset', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={onChange}
        imageUrl="https://example.com/reference-16-9.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })
    const image = screen.getByLabelText('Kamera referans görüntüsü')

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1600,
      naturalHeight: 900,
    })

    fireEvent.click(editor, {
      clientX: 0,
      clientY: 0,
    })

    fireEvent.click(editor, {
      clientX: 800,
      clientY: 450,
    })

    expect(onChange).toHaveBeenNthCalledWith(1, [{ x: 0, y: 0 }])
    expect(onChange).toHaveBeenNthCalledWith(2, [{ x: 0.5, y: 0.5 }])
  })

  it('normalizes 4:3 image clicks against the contained content rect', () => {
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

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1200,
      naturalHeight: 900,
    })

    fireEvent.click(editor, { clientX: 200, clientY: 450 })
    fireEvent.click(editor, { clientX: 800, clientY: 450 })
    fireEvent.click(editor, { clientX: 1400, clientY: 450 })

    expect(onChange).toHaveBeenNthCalledWith(1, [{ x: 0, y: 0.5 }])
    expect(onChange).toHaveBeenNthCalledWith(2, [{ x: 0.5, y: 0.5 }])
    expect(onChange).toHaveBeenNthCalledWith(3, [{ x: 1, y: 0.5 }])
  })

  it('ignores clicks inside the 4:3 letterbox area', () => {
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

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1200,
      naturalHeight: 900,
    })

    fireEvent.click(editor, {
      clientX: 100,
      clientY: 450,
    })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('normalizes portrait image clicks with horizontal letterbox', () => {
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

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 900,
      naturalHeight: 1600,
    })

    fireEvent.click(editor, { clientX: 546.875, clientY: 450 })
    fireEvent.click(editor, { clientX: 800, clientY: 450 })

    expect(onChange).toHaveBeenNthCalledWith(1, [{ x: 0, y: 0.5 }])
    expect(onChange).toHaveBeenNthCalledWith(2, [{ x: 0.5, y: 0.5 }])
  })

  it('ignores top letterbox clicks on a wide image and maps the content top edge to y=0', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[]}
        onChange={onChange}
        imageUrl="https://example.com/reference-wide.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })
    const image = screen.getByLabelText('Kamera referans görüntüsü')

    const contentRect = mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1920,
      naturalHeight: 800,
    })

    expect(contentRect).not.toBeNull()
    expect(contentRect?.width).toBe(1600)
    expect(contentRect?.height).toBeCloseTo(666.67, 1)
    expect(contentRect?.top).toBeCloseTo(116.67, 1)

    fireEvent.click(editor, {
      clientX: 800,
      clientY: 50,
    })

    expect(onChange).not.toHaveBeenCalled()

    fireEvent.click(editor, {
      clientX: 800,
      clientY: contentRect!.top,
    })

    expect(onChange).toHaveBeenCalledWith([{ x: 0.5, y: 0 }])
  })

  it('drags an existing point inside the content rect and ignores letterbox drag coordinates', () => {
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={[
          { x: 0.1, y: 0.2 },
          { x: 0.8, y: 0.2 },
          { x: 0.8, y: 0.8 },
        ]}
        onChange={onChange}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })
    const image = screen.getByLabelText('Kamera referans görüntüsü')

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1200,
      naturalHeight: 900,
    })

    fireEvent.mouseDown(screen.getByTestId('restricted-zone-point-0'))

    fireEvent.mouseMove(editor, {
      clientX: 800,
      clientY: 450,
    })

    expect(onChange).toHaveBeenCalledWith([
      { x: 0.5, y: 0.5 },
      { x: 0.8, y: 0.2 },
      { x: 0.8, y: 0.8 },
    ])

    onChange.mockClear()

    fireEvent.mouseMove(editor, {
      clientX: 100,
      clientY: 450,
    })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('aligns the polygon overlay with the contained image content rect', () => {
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

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1200,
      naturalHeight: 900,
    })

    expect(overlay).toHaveStyle({
      left: '200px',
      top: '0px',
      width: '1200px',
      height: '900px',
    })
  })

  it('keeps normalized polygon values and realigns the overlay after resize', () => {
    const points = [
      { x: 0.25, y: 0.25 },
      { x: 0.75, y: 0.25 },
      { x: 0.75, y: 0.75 },
    ]
    const onChange = vi.fn()

    render(
      <RestrictedZoneEditor
        points={points}
        onChange={onChange}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })
    const image = screen.getByLabelText('Kamera referans görüntüsü')
    const overlay = screen.getByTestId('restricted-zone-overlay')

    const box = {
      width: 1600,
      height: 900,
      left: 0,
      top: 0,
    }

    const getRect = () => createDomRect(box.width, box.height, box.left, box.top)

    vi.spyOn(editor, 'getBoundingClientRect').mockImplementation(getRect)
    vi.spyOn(image, 'getBoundingClientRect').mockImplementation(getRect)
    vi.spyOn(image as HTMLImageElement, 'naturalWidth', 'get').mockReturnValue(1200)
    vi.spyOn(image as HTMLImageElement, 'naturalHeight', 'get').mockReturnValue(900)

    fireEvent.load(image)

    expect(overlay).toHaveStyle({
      left: '200px',
      top: '0px',
      width: '1200px',
      height: '900px',
    })

    box.width = 800
    box.height = 450

    fireEvent(window, new Event('resize'))

    expect(onChange).not.toHaveBeenCalled()
    expect(screen.getByTestId('restricted-zone-point-0')).toHaveAttribute('cx', '25')
    expect(screen.getByTestId('restricted-zone-point-0')).toHaveAttribute('cy', '25')
    expect(overlay).toHaveStyle({
      left: '100px',
      top: '0px',
      width: '600px',
      height: '450px',
    })
  })

  it('renders an existing normalized polygon on the content overlay without transforming payload values', () => {
    const existingPolygon = [
      { x: 0.1, y: 0.1 },
      { x: 0.9, y: 0.1 },
      { x: 0.5, y: 0.9 },
    ]

    render(
      <RestrictedZoneEditor
        points={existingPolygon}
        onChange={vi.fn()}
        imageUrl="https://example.com/reference-4-3.jpg"
      />,
    )

    const editor = screen.getByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })
    const image = screen.getByLabelText('Kamera referans görüntüsü')
    const overlay = screen.getByTestId('restricted-zone-overlay')

    mockLoadedReferenceImage(editor, image, {
      boxWidth: 1600,
      boxHeight: 900,
      naturalWidth: 1200,
      naturalHeight: 900,
    })

    expect(overlay).toHaveStyle({
      left: '200px',
      top: '0px',
      width: '1200px',
      height: '900px',
    })

    expect(screen.getByTestId('restricted-zone-point-0')).toHaveAttribute('cx', '10')
    expect(screen.getByTestId('restricted-zone-point-0')).toHaveAttribute('cy', '10')
    expect(screen.getByTestId('restricted-zone-point-1')).toHaveAttribute('cx', '90')
    expect(screen.getByTestId('restricted-zone-point-1')).toHaveAttribute('cy', '10')
    expect(screen.getByTestId('restricted-zone-point-2')).toHaveAttribute('cx', '50')
    expect(screen.getByTestId('restricted-zone-point-2')).toHaveAttribute('cy', '90')
  })
})

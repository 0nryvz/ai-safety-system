export function parseUtcDate(value: string): Date {
  return new Date(value)
}

export function formatUtcToLocal(value: string): string {
  const date = parseUtcDate(value)

  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

export function localDateToUtcIso(value: Date): string {
  return value.toISOString()
}

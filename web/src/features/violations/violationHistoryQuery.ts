import {
  violationLifecycleStatuses,
  violationReviewStatuses,
  violationTypes,
  type ViolationHistoryQuery,
  type ViolationLifecycleStatus,
  type ViolationReviewStatus,
  type ViolationType,
} from '../../services/violationService'

export const DEFAULT_VIOLATION_HISTORY_PAGE = 0
export const DEFAULT_VIOLATION_HISTORY_PAGE_SIZE = 20

function isViolationType(value: string): value is ViolationType {
  return violationTypes.some((type) => type === value)
}

function isLifecycleStatus(value: string): value is ViolationLifecycleStatus {
  return violationLifecycleStatuses.some((status) => status === value)
}

function isReviewStatus(value: string): value is ViolationReviewStatus {
  return violationReviewStatuses.some((status) => status === value)
}

function parseNonNegativeInteger(value: string | null): number | undefined {
  if (value === null || value.trim() === '') {
    return undefined
  }

  const parsed = Number(value)

  if (!Number.isInteger(parsed) || parsed < 0) {
    return undefined
  }

  return parsed
}

function parsePositiveInteger(value: string | null): number | undefined {
  const parsed = parseNonNegativeInteger(value)

  if (parsed === undefined || parsed === 0) {
    return undefined
  }

  return parsed
}

export function parseViolationHistoryQuery(searchParams: URLSearchParams): ViolationHistoryQuery {
  const query: ViolationHistoryQuery = {}

  const from = searchParams.get('from')
  const to = searchParams.get('to')
  const type = searchParams.get('type')
  const cameraId = searchParams.get('cameraId')
  const departmentId = searchParams.get('departmentId')
  const lifecycleStatus = searchParams.get('lifecycleStatus')
  const reviewStatus = searchParams.get('reviewStatus')

  if (from) query.from = from
  if (to) query.to = to

  if (type && isViolationType(type)) {
    query.type = type
  }

  if (cameraId) {
    query.cameraId = cameraId
  }

  if (departmentId) {
    query.departmentId = departmentId
  }

  if (lifecycleStatus && isLifecycleStatus(lifecycleStatus)) {
    query.lifecycleStatus = lifecycleStatus
  }

  if (reviewStatus && isReviewStatus(reviewStatus)) {
    query.reviewStatus = reviewStatus
  }

  query.page = parseNonNegativeInteger(searchParams.get('page')) ?? DEFAULT_VIOLATION_HISTORY_PAGE

  query.size = parsePositiveInteger(searchParams.get('size')) ?? DEFAULT_VIOLATION_HISTORY_PAGE_SIZE

  const sort = searchParams.getAll('sort').filter((value) => value.trim().length > 0)

  if (sort.length > 0) {
    query.sort = sort
  }

  return query
}

export function serializeViolationHistoryQuery(query: ViolationHistoryQuery): URLSearchParams {
  const params = new URLSearchParams()

  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.type) params.set('type', query.type)
  if (query.cameraId) params.set('cameraId', query.cameraId)

  if (query.departmentId) {
    params.set('departmentId', query.departmentId)
  }

  if (query.lifecycleStatus) {
    params.set('lifecycleStatus', query.lifecycleStatus)
  }

  if (query.reviewStatus) {
    params.set('reviewStatus', query.reviewStatus)
  }

  if (query.page !== undefined && query.page !== DEFAULT_VIOLATION_HISTORY_PAGE) {
    params.set('page', String(query.page))
  }

  if (query.size !== undefined && query.size !== DEFAULT_VIOLATION_HISTORY_PAGE_SIZE) {
    params.set('size', String(query.size))
  }

  query.sort?.forEach((sort) => {
    params.append('sort', sort)
  })

  return params
}

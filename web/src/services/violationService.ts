import { apiClient } from '../core/api/apiClient'

export const violationTypes = [
  'MISSING_WELDING_MASK',
  'MISSING_GLOVES',
  'MISSING_WELDING_APRON',
  'RESTRICTED_ZONE',
  'UNPROTECTED_PERSON',
] as const

export const violationLifecycleStatuses = ['ACTIVE', 'PREPARING', 'COMPLETED', 'ERROR'] as const

export const violationReviewStatuses = [
  'UNREVIEWED',
  'REVIEWED',
  'CONFIRMED',
  'FALSE_ALARM',
] as const

export type ViolationType = (typeof violationTypes)[number]

export type ViolationLifecycleStatus = (typeof violationLifecycleStatuses)[number]

export type ViolationReviewStatus = (typeof violationReviewStatuses)[number]

export interface ViolationListItem {
  violationId: string
  cameraId: string
  departmentId: string
  type: ViolationType
  startedAt: string
  endedAt: string | null
  confidence: number
  lifecycleStatus: ViolationLifecycleStatus
  reviewStatus: ViolationReviewStatus
}

export interface ViolationClipUrl {
  url: string
  expiresAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ViolationHistoryQuery {
  from?: string
  to?: string
  type?: ViolationType
  cameraId?: string
  departmentId?: string
  lifecycleStatus?: ViolationLifecycleStatus
  reviewStatus?: ViolationReviewStatus
  page?: number
  size?: number
  sort?: string[]
}

function buildViolationQueryParams(query: ViolationHistoryQuery): URLSearchParams {
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
  if (query.page !== undefined) {
    params.set('page', String(query.page))
  }
  if (query.size !== undefined) {
    params.set('size', String(query.size))
  }

  query.sort?.forEach((sort) => {
    params.append('sort', sort)
  })

  return params
}

export async function getViolationHistory(
  query: ViolationHistoryQuery = {},
): Promise<PageResponse<ViolationListItem>> {
  const response = await apiClient.get<PageResponse<ViolationListItem>>('/violations', {
    params: buildViolationQueryParams(query),
  })

  return response.data
}

export async function getViolationClipUrl(violationId: string): Promise<ViolationClipUrl> {
  const response = await apiClient.get<ViolationClipUrl>(`/violations/${violationId}/clip-url`)

  return response.data
}

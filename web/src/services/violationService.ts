import { apiClient } from '../core/api/apiClient'
import type { RealtimeRecordingStatus } from '../core/realtime/realtimeTypes'

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

export type ViolationRecordingStatus = Exclude<RealtimeRecordingStatus, 'UNKNOWN'>

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
  recordingStatus: ViolationRecordingStatus | null
  updatedAt: string
}

export interface ViolationDetailResponse {
  violationId: string
  cameraId: string
  cameraName: string
  cameraCode: string
  departmentId: string
  departmentName: string
  sessionId: string
  type: ViolationType
  confidence: number
  modelVersion: string
  detectedAt: string
  startedAt: string
  endedAt: string | null
  lifecycleStatus: ViolationLifecycleStatus
  reviewStatus: ViolationReviewStatus
  reviewedBy: string | null
  reviewedAt: string | null
  recordingStatus: ViolationRecordingStatus
  clipReady: boolean
  playbackUrl: string | null
  coverImageKey: string | null
  coverImageReady: boolean
  version: number
}

export const editableViolationReviewStatuses = ['REVIEWED', 'CONFIRMED', 'FALSE_ALARM'] as const

export type EditableViolationReviewStatus = (typeof editableViolationReviewStatuses)[number]
export interface ViolationReviewResponse {
  violationId: string
  reviewStatus: ViolationReviewStatus
  reviewedBy: string
  reviewedAt: string
  version: number
}

export interface ViolationClipUrl {
  url: string
  expiresAt: string
}

export interface ViolationCoverUrl {
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

export async function getViolationCoverUrl(violationId: string): Promise<ViolationCoverUrl> {
  const response = await apiClient.get<ViolationCoverUrl>(`/violations/${violationId}/cover-url`)

  return response.data
}

export async function getViolationDetail(violationId: string): Promise<ViolationDetailResponse> {
  const response = await apiClient.get<ViolationDetailResponse>(`/violations/${violationId}`)

  return response.data
}

export async function reviewViolation(
  violationId: string,
  reviewStatus: EditableViolationReviewStatus,
  version: number,
): Promise<ViolationReviewResponse> {
  const response = await apiClient.patch<ViolationReviewResponse>(
    `/violations/${violationId}/review`,
    {
      reviewStatus,
      version,
    },
  )

  return response.data
}

import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import AppShell from '../app/AppShell'
import { formatUtcToLocal } from '../core/date/dateTime'
import {
  parseViolationHistoryQuery,
  serializeViolationHistoryQuery,
} from '../features/violations/violationHistoryQuery'
import { useViolationHistory } from '../features/violations/useViolationHistory'
import {
  violationLifecycleStatuses,
  violationReviewStatuses,
  violationTypes,
  type ViolationHistoryQuery,
  type ViolationLifecycleStatus,
  type ViolationListItem,
  type ViolationReviewStatus,
  type ViolationType,
} from '../services/violationService'
import Button from '../shared/ui/Button/Button'
import DataTable, { type DataTableColumn } from '../shared/ui/DataTable/DataTable'
import DateRange from '../shared/ui/DateRange/DateRange'
import EmptyState from '../shared/ui/EmptyState/EmptyState'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import Select from '../shared/ui/Select/Select'
import StatusBadge from '../shared/ui/StatusBadge/StatusBadge'
import Skeleton from '../shared/ui/Skeleton/Skeleton'
import './ViolationHistoryPage.css'
import { useCameraOptions } from '../features/violations/useCameraOptions'
import { useDepartmentOptions } from '../features/violations/useDepartmentOptions'
import { ApiError } from '../core/api/apiError'
import { getApiErrorKind } from '../core/api/apiErrorPolicy'
import { Link } from 'react-router-dom'

const violationTypeLabels: Record<ViolationListItem['type'], string> = {
  MISSING_WELDING_MASK: 'Kaynak maskesi eksik',
  MISSING_GLOVES: 'Eldiven eksik',
  MISSING_WELDING_APRON: 'Kaynak önlüğü eksik',
  RESTRICTED_ZONE: 'Yasak bölge ihlali',
  UNPROTECTED_PERSON: 'Koruyucu ekipmansız kişi',
}

const lifecycleLabels: Record<ViolationListItem['lifecycleStatus'], string> = {
  ACTIVE: 'Aktif',
  PREPARING: 'Hazırlanıyor',
  COMPLETED: 'Tamamlandı',
  ERROR: 'Hata',
}

const reviewLabels: Record<ViolationListItem['reviewStatus'], string> = {
  UNREVIEWED: 'İncelenmedi',
  REVIEWED: 'İncelendi',
  CONFIRMED: 'Onaylandı',
  FALSE_ALARM: 'Yanlış alarm',
}

function getLifecycleVariant(
  status: ViolationListItem['lifecycleStatus'],
): 'neutral' | 'success' | 'warning' | 'critical' {
  switch (status) {
    case 'ACTIVE':
      return 'warning'
    case 'COMPLETED':
      return 'success'
    case 'ERROR':
      return 'critical'
    default:
      return 'neutral'
  }
}

function getReviewVariant(
  status: ViolationListItem['reviewStatus'],
): 'neutral' | 'success' | 'warning' {
  switch (status) {
    case 'CONFIRMED':
      return 'success'
    case 'UNREVIEWED':
      return 'warning'
    default:
      return 'neutral'
  }
}

function utcIsoToLocalDateInput(value?: string): string {
  if (!value) {
    return ''
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function localDateToUtcStart(value: string): string | undefined {
  if (!value) {
    return undefined
  }

  return new Date(`${value}T00:00:00`).toISOString()
}

function localDateToUtcEnd(value: string): string | undefined {
  if (!value) {
    return undefined
  }

  return new Date(`${value}T23:59:59.999`).toISOString()
}

function ViolationHistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const queryKey = searchParams.toString()

  const query = useMemo(() => parseViolationHistoryQuery(new URLSearchParams(queryKey)), [queryKey])

  const { data, isLoading, error, retry } = useViolationHistory(query)

  const errorKind = error instanceof ApiError ? getApiErrorKind(error) : 'unknown'

  const { cameras, isLoading: camerasLoading, error: camerasError } = useCameraOptions()

  const {
    departments,
    isLoading: departmentsLoading,
    error: departmentsError,
  } = useDepartmentOptions()

  const cameraNamesById = useMemo(
    () => new Map(cameras.map((camera) => [camera.id, `${camera.name} (${camera.code})`])),
    [cameras],
  )

  const departmentNamesById = useMemo(
    () => new Map(departments.map((department) => [department.id, department.name])),
    [departments],
  )

  const columns = useMemo<DataTableColumn<ViolationListItem>[]>(
    () => [
      {
        key: 'type',
        header: 'İhlal tipi',
        render: (item) => violationTypeLabels[item.type],
      },
      {
        key: 'camera',
        header: 'Kamera',
        render: (item) => cameraNamesById.get(item.cameraId) ?? item.cameraId,
      },
      {
        key: 'department',
        header: 'Departman',
        render: (item) => departmentNamesById.get(item.departmentId) ?? item.departmentId,
      },
      {
        key: 'startedAt',
        header: 'Başlangıç',
        render: (item) => formatUtcToLocal(item.startedAt),
      },
      {
        key: 'confidence',
        header: 'Güven',
        render: (item) => `%${Math.round(item.confidence * 100)}`,
      },
      {
        key: 'lifecycleStatus',
        header: 'Durum',
        render: (item) => (
          <StatusBadge variant={getLifecycleVariant(item.lifecycleStatus)}>
            {lifecycleLabels[item.lifecycleStatus]}
          </StatusBadge>
        ),
      },
      {
        key: 'reviewStatus',
        header: 'İnceleme',
        render: (item) => (
          <StatusBadge variant={getReviewVariant(item.reviewStatus)}>
            {reviewLabels[item.reviewStatus]}
          </StatusBadge>
        ),
      },
      {
        key: 'detail',
        header: 'Detay',
        render: (item) => (
          <Link className="violation-history__detail-link" to={`/violations/${item.violationId}`}>
            Görüntüle
          </Link>
        ),
      },
    ],
    [cameraNamesById, departmentNamesById],
  )

  function updateQuery(changes: Partial<ViolationHistoryQuery>) {
    const nextQuery: ViolationHistoryQuery = {
      ...query,
      ...changes,
      page: 0,
    }

    setSearchParams(serializeViolationHistoryQuery(nextQuery))
  }

  function handleStartDateChange(value: string) {
    updateQuery({
      from: localDateToUtcStart(value),
    })
  }

  function handleEndDateChange(value: string) {
    updateQuery({
      to: localDateToUtcEnd(value),
    })
  }

  function clearFilters() {
    setSearchParams(
      serializeViolationHistoryQuery({
        page: 0,
        size: query.size,
      }),
    )
  }

  return (
    <AppShell>
      <section className="violation-history">
        <header className="violation-history__header">
          <div>
            <h2>İhlal Geçmişi</h2>
            <p>Geçmiş güvenlik ihlallerini görüntüleyin ve inceleyin.</p>
          </div>
        </header>

        <div className="violation-history__filters">
          <DateRange
            startDate={utcIsoToLocalDateInput(query.from)}
            endDate={utcIsoToLocalDateInput(query.to)}
            onStartDateChange={handleStartDateChange}
            onEndDateChange={handleEndDateChange}
          />

          <Select
            label="Kamera"
            value={query.cameraId ?? ''}
            disabled={camerasLoading}
            error={camerasError ? 'Kamera seçenekleri yüklenemedi.' : undefined}
            onChange={(event) => {
              updateQuery({
                cameraId: event.target.value || undefined,
              })
            }}
          >
            <option value="">{camerasLoading ? 'Kameralar yükleniyor...' : 'Tümü'}</option>

            {cameras.map((camera) => (
              <option key={camera.id} value={camera.id}>
                {camera.name} ({camera.code})
              </option>
            ))}
          </Select>

          <Select
            label="Departman"
            value={query.departmentId ?? ''}
            disabled={departmentsLoading}
            error={departmentsError ? 'Departman seçenekleri yüklenemedi.' : undefined}
            onChange={(event) => {
              updateQuery({
                departmentId: event.target.value || undefined,
              })
            }}
          >
            <option value="">{departmentsLoading ? 'Departmanlar yükleniyor...' : 'Tümü'}</option>

            {departments.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </Select>

          <Select
            label="İhlal tipi"
            value={query.type ?? ''}
            onChange={(event) => {
              const value = event.target.value

              updateQuery({
                type: violationTypes.includes(value as ViolationType)
                  ? (value as ViolationType)
                  : undefined,
              })
            }}
          >
            <option value="">Tümü</option>

            {violationTypes.map((type) => (
              <option key={type} value={type}>
                {violationTypeLabels[type]}
              </option>
            ))}
          </Select>

          <Select
            label="İhlal durumu"
            value={query.lifecycleStatus ?? ''}
            onChange={(event) => {
              const value = event.target.value

              updateQuery({
                lifecycleStatus: violationLifecycleStatuses.includes(
                  value as ViolationLifecycleStatus,
                )
                  ? (value as ViolationLifecycleStatus)
                  : undefined,
              })
            }}
          >
            <option value="">Tümü</option>

            {violationLifecycleStatuses.map((status) => (
              <option key={status} value={status}>
                {lifecycleLabels[status]}
              </option>
            ))}
          </Select>

          <Select
            label="İnceleme durumu"
            value={query.reviewStatus ?? ''}
            onChange={(event) => {
              const value = event.target.value

              updateQuery({
                reviewStatus: violationReviewStatuses.includes(value as ViolationReviewStatus)
                  ? (value as ViolationReviewStatus)
                  : undefined,
              })
            }}
          >
            <option value="">Tümü</option>

            {violationReviewStatuses.map((status) => (
              <option key={status} value={status}>
                {reviewLabels[status]}
              </option>
            ))}
          </Select>

          <Select
            label="Sıralama"
            value={query.sort?.[0] ?? 'startedAt,desc'}
            onChange={(event) => {
              updateQuery({
                sort: [event.target.value],
              })
            }}
          >
            <option value="startedAt,desc">En yeni</option>
            <option value="startedAt,asc">En eski</option>
            <option value="confidence,desc">Güven oranı: yüksekten düşüğe</option>
            <option value="confidence,asc">Güven oranı: düşükten yükseğe</option>
          </Select>

          <div className="violation-history__filter-action">
            <Button type="button" variant="secondary" onClick={clearFilters}>
              Filtreleri temizle
            </Button>
          </div>
        </div>

        {isLoading && !data ? (
          <div
            className="violation-history__loading"
            role="status"
            aria-label="İhlal geçmişi yükleniyor"
          >
            {Array.from({ length: 5 }, (_, index) => (
              <div className="violation-history__skeleton-row" key={index}>
                <Skeleton width="18%" />
                <Skeleton width="16%" />
                <Skeleton width="14%" />
                <Skeleton width="18%" />
                <Skeleton width="8%" />
                <Skeleton width="10%" />
                <Skeleton width="10%" />
              </div>
            ))}
          </div>
        ) : error ? (
          <ErrorState
            title={
              errorKind === 'network'
                ? 'Sunucuya bağlanılamadı'
                : errorKind === 'client'
                  ? 'Filtreler işlenemedi'
                  : 'İhlal geçmişi yüklenemedi'
            }
            description={
              errorKind === 'network'
                ? 'Bağlantınızı kontrol edip tekrar deneyin.'
                : errorKind === 'client'
                  ? 'Seçili filtreleri ve tarih aralığını kontrol edip tekrar deneyin.'
                  : 'İhlal verileri alınırken bir hata oluştu.'
            }
            action={
              <Button type="button" onClick={retry}>
                Tekrar dene
              </Button>
            }
          />
        ) : !data || data.content.length === 0 ? (
          <EmptyState
            title="İhlal bulunamadı"
            description="Seçili kriterlere uygun ihlal kaydı bulunmuyor."
          />
        ) : (
          <>
            <DataTable
              columns={columns}
              data={data.content}
              getRowKey={(item) => item.violationId}
            />

            <div className="violation-history__pagination">
              <p className="violation-history__summary">Toplam {data.totalElements} kayıt</p>

              <div className="violation-history__pagination-controls">
                <Select
                  label="Sayfa boyutu"
                  value={String(query.size ?? 20)}
                  onChange={(event) => {
                    updateQuery({
                      size: Number(event.target.value),
                    })
                  }}
                >
                  <option value="10">10</option>
                  <option value="20">20</option>
                  <option value="50">50</option>
                </Select>

                <Button
                  type="button"
                  variant="secondary"
                  disabled={(query.page ?? 0) <= 0}
                  onClick={() => {
                    setSearchParams(
                      serializeViolationHistoryQuery({
                        ...query,
                        page: Math.max((query.page ?? 0) - 1, 0),
                      }),
                    )
                  }}
                >
                  Önceki
                </Button>

                <span className="violation-history__page-info">
                  Sayfa {(query.page ?? 0) + 1} / {Math.max(data.totalPages, 1)}
                </span>

                <Button
                  type="button"
                  variant="secondary"
                  disabled={data.totalPages === 0 || (query.page ?? 0) >= data.totalPages - 1}
                  onClick={() => {
                    setSearchParams(
                      serializeViolationHistoryQuery({
                        ...query,
                        page: (query.page ?? 0) + 1,
                      }),
                    )
                  }}
                >
                  Sonraki
                </Button>
              </div>
            </div>
          </>
        )}
      </section>
    </AppShell>
  )
}

export default ViolationHistoryPage

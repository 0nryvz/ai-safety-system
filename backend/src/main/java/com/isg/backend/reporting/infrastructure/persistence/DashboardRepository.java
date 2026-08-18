package com.isg.backend.reporting.infrastructure.persistence;

import com.isg.backend.reporting.dto.DashboardDistributionResponse;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.dto.DashboardTrendResponse;
import com.isg.backend.reporting.dto.RecentViolationResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class DashboardRepository {

    @PersistenceContext
    private EntityManager entityManager;


    private Instant toInstant(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }

        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }

        if (value instanceof Instant instant) {
            return instant;
        }

        return Instant.parse(value.toString());
    }


    public DashboardSummaryResponse getSummary(List<UUID> departmentIds) {

        Object[] result = (Object[]) entityManager.createNativeQuery("""
            SELECT
                COUNT(*) FILTER (
                    WHERE v.started_at >=
                          date_trunc(
                              'day',
                              CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
                          ) AT TIME ZONE 'UTC'
                ),

                COUNT(*) FILTER (
                    WHERE v.started_at >=
                          CURRENT_TIMESTAMP - INTERVAL '7 days'
                ),

                (
                    SELECT v2.violation_type
                    FROM violations v2
                    WHERE v2.department_id IN (:departmentIds)
                    GROUP BY v2.violation_type
                    ORDER BY COUNT(*) DESC
                    LIMIT 1
                ),

                (
                    SELECT COUNT(*)
                    FROM cameras c
                    WHERE c.status IN ('ONLINE', 'DEGRADED')
                      AND c.department_id IN (:departmentIds)
                ),

                (
                    SELECT COUNT(*)
                    FROM cameras c
                    WHERE c.status = 'OFFLINE'
                      AND c.department_id IN (:departmentIds)
                ),

                COUNT(*) FILTER (
                    WHERE v.lifecycle_status = 'ACTIVE'
                )

            FROM violations v
            WHERE v.department_id IN (:departmentIds)
            """)
                .setParameter("departmentIds", departmentIds)
                .getSingleResult();

        return new DashboardSummaryResponse(
                ((Number) result[0]).longValue(),
                ((Number) result[1]).longValue(),
                result[2] != null ? result[2].toString() : null,
                ((Number) result[3]).longValue(),
                ((Number) result[4]).longValue(),
                ((Number) result[5]).longValue()
        );
    }


    public List<DashboardTrendResponse> getTrend(
            LocalDate from,
            LocalDate to,
            String bucket,
            List<UUID> departmentIds
    ) {

        if (!"DAY".equalsIgnoreCase(bucket)) {
            throw new IllegalArgumentException(
                    "Only DAY bucket is supported"
            );
        }

        Instant fromUtc =
                from.atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant toUtc =
                to.plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();

        return entityManager.createNativeQuery("""
            SELECT
                (started_at AT TIME ZONE 'UTC')::date AS day,
                COUNT(*) AS count
            FROM violations
            WHERE started_at >= :fromUtc
              AND started_at < :toUtc
              AND department_id IN (:departmentIds)
            GROUP BY (started_at AT TIME ZONE 'UTC')::date
            ORDER BY day
            """)
                .setParameter("fromUtc", fromUtc)
                .setParameter("toUtc", toUtc)
                .setParameter("departmentIds", departmentIds)
                .getResultList()
                .stream()
                .map(row -> {

                    Object[] data = (Object[]) row;

                    LocalDate date;

                    if (data[0] instanceof java.sql.Date sqlDate) {
                        date = sqlDate.toLocalDate();
                    } else {
                        date = LocalDate.parse(data[0].toString());
                    }

                    return new DashboardTrendResponse(
                            date,
                            ((Number) data[1]).longValue()
                    );
                })
                .toList();
    }


    public List<DashboardDistributionResponse> getDistribution(
            String groupBy,
            List<UUID> departmentIds
    ) {

        String column;

        switch (groupBy.toUpperCase()) {

            case "TYPE" ->
                    column = "violation_type";

            case "CAMERA" ->
                    column = "camera_id";

            case "DEPARTMENT" ->
                    column = "department_id";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported groupBy: " + groupBy
                    );
        }

        return entityManager.createNativeQuery("""
            SELECT
                %s::text AS label,
                COUNT(*) AS count
            FROM violations
            WHERE department_id IN (:departmentIds)
            GROUP BY %s
            ORDER BY count DESC
            """.formatted(column, column))
                .setParameter("departmentIds", departmentIds)
                .getResultList()
                .stream()
                .map(row -> {

                    Object[] data = (Object[]) row;

                    return new DashboardDistributionResponse(
                            data[0].toString(),
                            ((Number) data[1]).longValue()
                    );
                })
                .toList();
    }


    public List<RecentViolationResponse> getRecentViolations(
            List<UUID> departmentIds,
            int limit
    ) {

        return entityManager.createNativeQuery("""
            SELECT
                v.id,
                v.detected_at,
                v.started_at,
                v.violation_type,
                v.camera_id,
                v.department_id,
                c.name,
                c.code,
                v.lifecycle_status,
                v.review_status,
                r.status,
                r.ready_at,
                r.object_key,
                v.cover_image_key,
                v.confidence,
                v.model_version
            FROM violations v
            LEFT JOIN cameras c
                ON c.id = v.camera_id
            LEFT JOIN recordings r
                ON r.violation_id = v.id
            WHERE v.department_id IN (:departmentIds)
            ORDER BY v.started_at DESC
            LIMIT :limit
            """)
                .setParameter("departmentIds", departmentIds)
                .setParameter("limit", limit)
                .getResultList()
                .stream()
                .map(row -> {

                    Object[] data = (Object[]) row;

                    return new RecentViolationResponse(
                            UUID.fromString(data[0].toString()),
                            toInstant(data[1]),
                            toInstant(data[2]),
                            data[3] != null ? data[3].toString() : null,
                            data[4] != null
                                    ? UUID.fromString(data[4].toString())
                                    : null,
                            data[5] != null
                                    ? UUID.fromString(data[5].toString())
                                    : null,
                            data[6] != null ? data[6].toString() : null,
                            data[7] != null ? data[7].toString() : null,
                            data[8] != null ? data[8].toString() : null,
                            data[9] != null ? data[9].toString() : null,
                            data[10] != null ? data[10].toString() : null,
                            toInstant(data[11]),
                            data[12] != null ? data[12].toString() : null,
                            data[13] != null ? data[13].toString() : null,
                            data[14] != null
                                    ? ((Number) data[14]).doubleValue()
                                    : null,
                            data[15] != null ? data[15].toString() : null
                    );
                })
                .toList();
    }
}
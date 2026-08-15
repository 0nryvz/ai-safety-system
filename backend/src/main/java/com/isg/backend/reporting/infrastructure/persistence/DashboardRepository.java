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

    public DashboardSummaryResponse getSummary() {

        Object[] result = (Object[]) entityManager.createNativeQuery("""
                SELECT
                    COUNT(*) FILTER (
                        WHERE v.started_at >= CURRENT_DATE
                    ),

                    COUNT(*) FILTER (
                        WHERE v.started_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                    ),

                    (
                        SELECT violation_type
                        FROM violations
                        GROUP BY violation_type
                        ORDER BY COUNT(*) DESC
                        LIMIT 1
                    ),

                    (
                        SELECT COUNT(*)
                        FROM cameras
                        WHERE status IN ('ONLINE','DEGRADED')
                    ),

                    (
                        SELECT COUNT(*)
                        FROM cameras
                        WHERE status = 'OFFLINE'
                    ),

                    COUNT(*) FILTER (
                        WHERE v.lifecycle_status = 'ACTIVE'
                    )

                FROM violations v
                """)
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
            LocalDate to
    ) {

        return entityManager.createNativeQuery("""
                SELECT
                    DATE(started_at) AS day,
                    COUNT(*) AS count
                FROM violations
                WHERE started_at >= :from
                  AND started_at < :to
                GROUP BY DATE(started_at)
                ORDER BY day
                """)
                .setParameter("from", from)
                .setParameter("to", to)
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
            String groupBy
    ) {

        String column;

        switch (groupBy.toUpperCase()) {
            case "TYPE" -> column = "violation_type";
            case "CAMERA" -> column = "camera_id";
            case "DEPARTMENT" -> column = "department_id";
            default -> throw new IllegalArgumentException(
                    "Unsupported groupBy: " + groupBy
            );
        }

        return entityManager.createNativeQuery(
                        """
                        SELECT
                            CAST(%s AS TEXT) AS group_name,
                            COUNT(*) AS count
                        FROM violations
                        GROUP BY %s
                        ORDER BY count DESC
                        """.formatted(column, column)
                )
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

    public List<RecentViolationResponse> getRecentViolations(UUID userId) {

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
                JOIN user_departments ud
                    ON ud.department_id = v.department_id
                   AND ud.user_id = :userId
                LEFT JOIN cameras c
                    ON c.id = v.camera_id
                LEFT JOIN recordings r
                    ON r.violation_id = v.id
                ORDER BY v.started_at DESC
                LIMIT 20
                """)
                .setParameter("userId", userId)
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
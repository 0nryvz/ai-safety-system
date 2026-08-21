package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionLabel;

import java.util.List;

final class PpeSpatialRules {

    /*
     * Welding çalışma alanı person boyutuna göre ölçeklenir.
     *
     * Yakın planda person büyükse zone büyür,
     * uzak planda person küçükse zone küçülür.
     *
     * Sabit pixel kullanılmaz.
     */
    private static final double
            WELDING_ZONE_HORIZONTAL_PERSON_RATIO = 0.30;

    private static final double
            WELDING_ZONE_VERTICAL_PERSON_RATIO = 0.25;

    /*
     * Welding mask yalnızca person bbox'ın üst
     * %45'lik bölümünde ise takılı kabul edilir.
     */
    private static final double
            MASK_HEAD_ZONE_RATIO = 0.45;

    /*
     * Glove merkezi zone dışında kalsa bile glove bbox'ın
     * en az %25'i welding zone ile kesişiyorsa kabul ediyoruz.
     * Bu, bbox jitter yüzünden false violation üretmeyi azaltır.
     */
    private static final double
            MIN_GLOVE_ZONE_OVERLAP_RATIO = 0.25;


    private PpeSpatialRules() {
    }


    static boolean hasGlovesInWeldingZone(
            PersonContext person
    ) {
        List<DetectedObject> gloves =
                detections(
                        person,
                        DetectionLabel.GLOVES
                );

        List<DetectedObject> weldingDetections =
                detections(
                        person,
                        DetectionLabel.WELDING
                );

        if (gloves.isEmpty()
                || weldingDetections.isEmpty()) {
            return false;
        }

        BoundingBox personBox =
                person.person().boundingBox();

        for (DetectedObject welding
                : weldingDetections) {

            BoundingBox weldingBox =
                    welding.boundingBox();

            WeldingZone zone =
                    buildWeldingZone(
                            personBox,
                            weldingBox
                    );

            for (DetectedObject glove
                    : gloves) {

                BoundingBox gloveBox =
                        glove.boundingBox();

                if (zone.containsCenter(gloveBox)) {
                    return true;
                }

                if (overlapRatio(
                        gloveBox,
                        zone
                ) >= MIN_GLOVE_ZONE_OVERLAP_RATIO) {
                    return true;
                }
            }
        }

        return false;
    }


    static boolean hasWeldingMaskInHeadZone(
            PersonContext person
    ) {
        BoundingBox personBox =
                person.person().boundingBox();

        double personRight =
                personBox.x()
                        + personBox.width();

        double headZoneBottom =
                personBox.y()
                        + (
                        personBox.height()
                                * MASK_HEAD_ZONE_RATIO
                );

        return detections(
                person,
                DetectionLabel.WELDING_MASK
        )
                .stream()
                .map(
                        DetectedObject::boundingBox
                )
                .anyMatch(maskBox ->
                        maskBox.centerX()
                                >= personBox.x()
                                &&
                                maskBox.centerX()
                                        <= personRight
                                &&
                                maskBox.centerY()
                                        >= personBox.y()
                                &&
                                maskBox.centerY()
                                        <= headZoneBottom
                );
    }


    private static WeldingZone buildWeldingZone(
            BoundingBox personBox,
            BoundingBox weldingBox
    ) {
        double horizontalExpansion =
                personBox.width()
                        * WELDING_ZONE_HORIZONTAL_PERSON_RATIO;

        double verticalExpansion =
                personBox.height()
                        * WELDING_ZONE_VERTICAL_PERSON_RATIO;

        double personRight =
                personBox.x()
                        + personBox.width();

        double personBottom =
                personBox.y()
                        + personBox.height();

        double weldingRight =
                weldingBox.x()
                        + weldingBox.width();

        double weldingBottom =
                weldingBox.y()
                        + weldingBox.height();

        /*
         * Zone'u aynı person bbox sınırlarında tutuyoruz.
         * Böylece komşu kişinin PPE'sine doğru gereksiz
         * genişlemez.
         */
        double left =
                Math.max(
                        personBox.x(),
                        weldingBox.x()
                                - horizontalExpansion
                );

        double top =
                Math.max(
                        personBox.y(),
                        weldingBox.y()
                                - verticalExpansion
                );

        double right =
                Math.min(
                        personRight,
                        weldingRight
                                + horizontalExpansion
                );

        double bottom =
                Math.min(
                        personBottom,
                        weldingBottom
                                + verticalExpansion
                );

        return new WeldingZone(
                left,
                top,
                right,
                bottom
        );
    }


    private static double overlapRatio(
            BoundingBox glove,
            WeldingZone zone
    ) {
        double gloveRight =
                glove.x()
                        + glove.width();

        double gloveBottom =
                glove.y()
                        + glove.height();

        double overlapLeft =
                Math.max(
                        glove.x(),
                        zone.left()
                );

        double overlapTop =
                Math.max(
                        glove.y(),
                        zone.top()
                );

        double overlapRight =
                Math.min(
                        gloveRight,
                        zone.right()
                );

        double overlapBottom =
                Math.min(
                        gloveBottom,
                        zone.bottom()
                );

        double overlapWidth =
                Math.max(
                        0.0,
                        overlapRight
                                - overlapLeft
                );

        double overlapHeight =
                Math.max(
                        0.0,
                        overlapBottom
                                - overlapTop
                );

        double overlapArea =
                overlapWidth
                        * overlapHeight;

        double gloveArea =
                glove.area();

        if (gloveArea <= 0.0) {
            return 0.0;
        }

        return overlapArea
                / gloveArea;
    }


    private static List<DetectedObject> detections(
            PersonContext person,
            DetectionLabel label
    ) {
        return person
                .associatedDetections()
                .stream()
                .filter(
                        detection ->
                                detection.label()
                                        == label
                )
                .toList();
    }


    private record WeldingZone(
            double left,
            double top,
            double right,
            double bottom
    ) {

        boolean containsCenter(
                BoundingBox box
        ) {
            return box.centerX() >= left
                    && box.centerX() <= right
                    && box.centerY() >= top
                    && box.centerY() <= bottom;
        }
    }
}
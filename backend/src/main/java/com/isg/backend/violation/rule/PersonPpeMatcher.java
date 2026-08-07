package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministically associates PPE and welding detections with person detections.
 *
 * <p>The matcher expects all bounding boxes to use the same normalized coordinate
 * system. Each associated detection is assigned to at most one person using the
 * ratio of the detection bounding box area contained within the person's bounding
 * box. An assignment is accepted only when that containment ratio is greater than
 * or equal to the configured threshold.</p>
 *
 * <p>If multiple persons are eligible, the person with the highest containment
 * ratio is selected. Ties are resolved deterministically by person order.</p>
 */

public class PersonPpeMatcher {

    private static final Set<DetectionLabel> PERSON_LABELS =
            EnumSet.of(
                    DetectionLabel.PERSON
            );

    private static final Set<DetectionLabel> ASSOCIATED_LABELS =
            EnumSet.of(
                    DetectionLabel.WELDING_MASK,
                    DetectionLabel.WELDING_APRON,
                    DetectionLabel.WELDING_JACKET,
                    DetectionLabel.GLOVES,
                    DetectionLabel.NON_GLOVES,
                    DetectionLabel.NON_MASK,
                    DetectionLabel.NON_JACKET,
                    DetectionLabel.WELDING
            );

    public List<PersonContext> buildPersonContexts(
            DetectionFrame frame,
            double containmentThreshold
    ) {
        validateContainmentThreshold(containmentThreshold);

        List<DetectedObject> persons = frame.detections().stream()
                .filter(this::isPerson)
                .toList();

        Map<Integer, List<DetectedObject>> assignments =
                new HashMap<>();

        for (int i = 0; i < persons.size(); i++) {
            assignments.put(i, new ArrayList<>());
        }

        frame.detections().stream()
                .filter(this::isAssociatedDetection)
                .forEach(detection ->
                        assignToBestPerson(
                                detection,
                                persons,
                                assignments,
                                containmentThreshold
                        )
                );

        List<PersonContext> contexts = new ArrayList<>();

        for (int i = 0; i < persons.size(); i++) {
            DetectedObject person = persons.get(i);

            contexts.add(
                    new PersonContext(
                            buildPersonKey(frame, person, i),
                            person,
                            assignments.get(i)
                    )
            );
        }

        return List.copyOf(contexts);
    }

    private void assignToBestPerson(
            DetectedObject detection,
            List<DetectedObject> persons,
            Map<Integer, List<DetectedObject>> assignments,
            double containmentThreshold
    ) {
        BestMatch bestMatch =
                findBestMatch(detection, persons);

        if (bestMatch == null
                || bestMatch.containmentRatio() < containmentThreshold) {
            return;
        }

        assignments.get(bestMatch.personIndex())
                .add(detection);
    }

    private BestMatch findBestMatch(
            DetectedObject detection,
            List<DetectedObject> persons
    ) {
        BoundingBox detectionBox =
                detection.boundingBox();

        return java.util.stream.IntStream
                .range(0, persons.size())
                .mapToObj(index ->
                        new BestMatch(
                                index,
                                containmentRatio(
                                        detectionBox,
                                        persons.get(index)
                                                .boundingBox()
                                )
                        )
                )
                .max(
                        Comparator
                                .comparingDouble(
                                        BestMatch::containmentRatio
                                )
                                .thenComparingInt(
                                        match ->
                                                -match.personIndex()
                                )
                )
                .orElse(null);
    }

    private double containmentRatio(
            BoundingBox child,
            BoundingBox container
    ) {
        double childRight =
                child.x() + child.width();

        double childBottom =
                child.y() + child.height();

        double containerRight =
                container.x() + container.width();

        double containerBottom =
                container.y() + container.height();

        double overlapLeft =
                Math.max(child.x(), container.x());

        double overlapTop =
                Math.max(child.y(), container.y());

        double overlapRight =
                Math.min(childRight, containerRight);

        double overlapBottom =
                Math.min(childBottom, containerBottom);

        double overlapWidth =
                Math.max(
                        0.0,
                        overlapRight - overlapLeft
                );

        double overlapHeight =
                Math.max(
                        0.0,
                        overlapBottom - overlapTop
                );

        double overlapArea =
                overlapWidth * overlapHeight;

        double childArea = child.area();

        if (childArea == 0.0) {
            return 0.0;
        }

        return overlapArea / childArea;
    }

    private String buildPersonKey(
            DetectionFrame frame,
            DetectedObject person,
            int personIndex
    ) {
        if (person.trackId() != null
                && !person.trackId().isBlank()) {
            return "track-" + person.trackId();
        }

        return "frame-"
                + frame.eventId()
                + "-person-"
                + personIndex;
    }

    private boolean isPerson(
            DetectedObject detection
    ) {
        return PERSON_LABELS.contains(
                detection.label()
        );
    }

    private boolean isAssociatedDetection(
            DetectedObject detection
    ) {
        return ASSOCIATED_LABELS.contains(
                detection.label()
        );
    }

    private static void validateContainmentThreshold(
            double threshold
    ) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "containmentThreshold must be between 0.0 and 1.0"
            );
        }
    }

    private record BestMatch(
            int personIndex,
            double containmentRatio
    ) {
    }
}
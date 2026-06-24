package dk.mallingbio.domain.state;

import dk.mallingbio.domain.ScreenId;

import java.time.Instant;

public record ScreenTimingPlan(
        ScreenId screenId,
        Instant introStartAt,
        Instant publicStartAt,
        boolean autoStartEnabled
) {
}
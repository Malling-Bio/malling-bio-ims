package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.ScreenSnapshot;
import dk.mallingbio.domain.state.ScreenAction;
import dk.mallingbio.domain.state.ScreenTimingPlan;

import java.time.Instant;
import java.util.List;

public record ScreenRuntimeStatus(
        ScreenId screenId,
        ScreenSnapshot snapshot,
        OperationalState supervisedState,
        List<ScreenAction> lastActions,
        ScreenTimingPlan timingPlan,
        Instant updatedAt
) {
}
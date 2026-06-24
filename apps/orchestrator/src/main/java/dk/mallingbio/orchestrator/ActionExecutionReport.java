package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenAction;

import java.time.Instant;
import java.util.List;

public record ActionExecutionReport(
        ScreenId screenId,
        List<ScreenAction> requestedActions,
        List<ScreenAction> executedActions,
        List<ScreenAction> skippedActions,
        List<String> errors,
        Instant executedAt
) {
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean didAnything() {
        return !executedActions.isEmpty();
    }

    public static ActionExecutionReport empty(ScreenId screenId, List<ScreenAction> requestedActions) {
        return new ActionExecutionReport(
                screenId,
                requestedActions,
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }
}
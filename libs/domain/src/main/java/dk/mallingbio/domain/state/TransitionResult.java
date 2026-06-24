package dk.mallingbio.domain.state;

import dk.mallingbio.domain.OperationalState;

import java.util.List;

public record TransitionResult(
        OperationalState newState,
        List<ScreenAction> actions
) {
    public static TransitionResult noChange(OperationalState state) {
        return new TransitionResult(state, List.of());
    }

    public static TransitionResult to(OperationalState state, ScreenAction... actions) {
        return new TransitionResult(state, List.of(actions));
    }
}
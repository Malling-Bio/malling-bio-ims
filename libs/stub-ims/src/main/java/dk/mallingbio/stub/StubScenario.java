package dk.mallingbio.stub;

import dk.mallingbio.domain.OperationalState;

import java.time.Duration;

/**
 * Simple scenario definition used by StubImsClient.
 * This can later be loaded from YAML/JSON.
 */
public record StubScenario(
        OperationalState initialState,
        Duration featureDuration,
        Duration doorReminderOffset
) {
    public static StubScenario defaultScenario() {
        return new StubScenario(OperationalState.IDLE, Duration.ofMinutes(95), Duration.ofMinutes(8));
    }
}

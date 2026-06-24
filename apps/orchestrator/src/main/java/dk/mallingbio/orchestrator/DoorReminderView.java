package dk.mallingbio.orchestrator;

public record DoorReminderView(
        Long offsetSeconds,
        Long secondsUntil,
        boolean triggered
) {
}
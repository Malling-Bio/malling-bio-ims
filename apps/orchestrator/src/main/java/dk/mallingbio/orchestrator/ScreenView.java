package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;

public record ScreenView(
        ScreenId screenId,
        ScreenRuntimeStatus runtime,
        ActionExecutionReport lastExecutionReport,
        DoorReminderView doorReminder
) {
}
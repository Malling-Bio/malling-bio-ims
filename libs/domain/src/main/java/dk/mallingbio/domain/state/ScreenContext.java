package dk.mallingbio.domain.state;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;

public record ScreenContext(
        ScreenId screenId,
        AppMode appMode,
        ConnectivityState connectivityState,
        OperationalState operationalState,
        boolean schedulerRunning,
        String currentContentTitle,
        String currentContentKind,
        String showStatus
) {
    public boolean isConnected() {
        return connectivityState == ConnectivityState.CONNECTED;
    }

    public boolean commandsAllowed() {
        return appMode == AppMode.AUTO && isConnected();
    }

    public boolean isManualLikeMode() {
        return appMode == AppMode.MANUAL || appMode == AppMode.MAINTENANCE;
    }

    public boolean currentContentLooksLikeTrailer() {
        return currentContentTitle != null && currentContentTitle.contains("_TRL_");
    }

    public boolean currentContentLooksLikeFeature() {
        return currentContentTitle != null && currentContentTitle.contains("_FEA_");
    }

    public boolean showIsPaused() {
        return showStatus != null && showStatus.equalsIgnoreCase("PAUSED");
    }

    public boolean showIsIdle() {
        return showStatus != null && showStatus.equalsIgnoreCase("IDLE");
    }
}
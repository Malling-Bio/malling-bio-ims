package dk.mallingbio.domain;

import java.time.Instant;

public record ScreenSnapshot(
        ScreenId screenId,
        ConnectivityState connectivity,
        OperationalState operational,
        boolean schedulerRunning,
        String currentContentTitle,
        String currentContentKind,
        String showStatus,
        String lastError,
        Instant observedAt
) {}

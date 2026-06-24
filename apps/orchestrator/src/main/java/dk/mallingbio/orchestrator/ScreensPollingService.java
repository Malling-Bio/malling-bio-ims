package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.ScreenSnapshot;
import dk.mallingbio.ims.ImsClient;
import dk.mallingbio.orchestrator.dev.DevScreenSimulationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ScreensPollingService {

    @Inject
    ImsRegistry registry;

    @Inject
    Instance<DevScreenSimulationService> simulationService;

    /**
     * Seneste kendte snapshot pr. sal
     */
    private final ConcurrentHashMap<ScreenId, ScreenSnapshot> latest = new ConcurrentHashMap<>();

    /**
     * Backoff (antal fejl i træk pr. sal)
     */
    private final ConcurrentHashMap<ScreenId, Integer> failureCount = new ConcurrentHashMap<>();

    /**
     * Tidspunkt hvor næste polling må ske
     */
    private final ConcurrentHashMap<ScreenId, Instant> nextAllowedPoll = new ConcurrentHashMap<>();

    @Scheduled(every = "2s")
    void pollScreens() {
        Instant now = Instant.now();

        //I dev mode simuleres afspilning
        DevScreenSimulationService simulator = getSimulationServiceOrNull();
        if (simulator != null) {
            simulator.refresh(now);
            latest.putAll(simulator.getLatestSnapshots());
        }

        for (Map.Entry<ScreenId, ImsClient> entry : registry.all().entrySet()) {
            ScreenId screenId = entry.getKey();
            ImsClient client = entry.getValue();

            ScreenSnapshot simulated = simulator != null ? simulator.getSnapshot(screenId) : null;
            if (simulated != null) {
                latest.put(screenId, simulated);
                failureCount.put(screenId, 0);
                nextAllowedPoll.put(screenId, now);
                continue;
            }

            if (!isAllowedToPoll(screenId, now)) {
                continue;
            }

            try {
                client.ensureSession();
                ScreenSnapshot snapshot = client.snapshot();

                latest.put(screenId, snapshot);
                failureCount.put(screenId, 0);
                nextAllowedPoll.put(screenId, now);

            } catch (Exception ex) {
                int failures = failureCount.getOrDefault(screenId, 0) + 1;
                failureCount.put(screenId, failures);

                ScreenSnapshot prev = latest.get(screenId);

                ScreenSnapshot fallback = new ScreenSnapshot(
                        screenId,
                        ConnectivityState.NO_CONNECTION,
                        prev != null ? prev.operational() : OperationalState.IDLE,
                        false,
                        null,
                        null,
                        "NO_CONNECTION",
                        ex.getMessage(),
                        now
                );

                latest.put(screenId, fallback);

                long backoffSeconds = Math.min(30, (long) Math.pow(2, failures));
                nextAllowedPoll.put(screenId, now.plusSeconds(backoffSeconds));
            }
        }
    }

    public Map<ScreenId, ScreenSnapshot> getLatest() {
        return Map.copyOf(latest);
    }

    private boolean isAllowedToPoll(ScreenId screenId, Instant now) {
        Instant next = nextAllowedPoll.get(screenId);
        return next == null || now.isAfter(next);
    }

    private DevScreenSimulationService getSimulationServiceOrNull() {
        return simulationService.isResolvable() ? simulationService.get() : null;
    }
}
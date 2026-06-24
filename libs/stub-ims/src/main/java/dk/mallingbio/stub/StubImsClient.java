package dk.mallingbio.stub;

import dk.mallingbio.domain.*;
import dk.mallingbio.ims.ImsClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic simulator for an IMS screen.
 * <p>
 * Designed for development when the real IMS servers are offline.
 */
public class StubImsClient implements ImsClient {

    private final ScreenId screenId;
    private final AtomicReference<ConnectivityState> connectivity = new AtomicReference<>(ConnectivityState.CONNECTED);
    private final AtomicReference<OperationalState> operational = new AtomicReference<>();
    private volatile boolean schedulerRunning = true;

    private volatile String currentTitle;
    private volatile String currentKind;
    private volatile String showStatus;
    private volatile String lastError;

    private volatile Instant featureStartedAt;
    private volatile StubScenario scenario;

    public StubImsClient(ScreenId screenId) {
        this.screenId = screenId;
        this.scenario = StubScenario.defaultScenario();
        this.operational.set(scenario.initialState());
        this.showStatus = "IDLE";
    }

    @Override
    public ScreenId screenId() {
        return screenId;
    }

    @Override
    public void ensureSession() {
        // no-op in stub
        if (connectivity.get() == ConnectivityState.NO_CONNECTION) {
            throw new RuntimeException("NO_CONNECTION");
        }
    }

    @Override
    public ScreenSnapshot snapshot() {
        return new ScreenSnapshot(
                screenId,
                connectivity.get(),
                operational.get(),
                schedulerRunning,
                currentTitle,
                currentKind,
                showStatus,
                lastError,
                Instant.now()
        );
    }

    @Override
    public void stopScheduler() {
        schedulerRunning = false;
    }

    @Override
    public void startScheduler() {
        schedulerRunning = true;
    }

    @Override
    public void play() {
        // Simulate transition: STARTING -> REKLAMER -> TRAILERS -> FEATURE
        OperationalState st = operational.get();
        if (st == OperationalState.INTRO_LOOP || st == OperationalState.IDLE || st == OperationalState.STARTING) {
            operational.set(OperationalState.REKLAMER);
            showStatus = "PAUSED"; // mimic ads behavior
            currentTitle = "ADS_EXTERNAL";
            currentKind = "Ads";
        }
    }

    @Override
    public void eject() {
        operational.set(OperationalState.IDLE);
        showStatus = "IDLE";
        currentTitle = null;
        currentKind = null;
        featureStartedAt = null;
    }

    @Override
    public String getSplRuntimeBase64() {
        // Minimal SPL XML placeholder with a named cue.
        String xml = "<spl><cues><cue name=\"loftlys skelne\" offsetSeconds=\""
                + scenario.featureDuration().minus(scenario.doorReminderOffset()).toSeconds()
                + "\"/></cues></spl>";
        return Base64.getEncoder().encodeToString(xml.getBytes());
    }

    // --- test driver controls

    public void setConnectivity(ConnectivityState s) {
        connectivity.set(s);
        if (s == ConnectivityState.NO_CONNECTION) {
            lastError = "Connection refused";
        } else {
            lastError = null;
        }
    }

    public void setOperational(OperationalState s) {
        operational.set(s);
        if (s == OperationalState.FEATURE) {
            featureStartedAt = Instant.now();
            showStatus = "PLAYING";
            currentTitle = "Example_FEA_01";
            currentKind = "Feature";
        } else if (s == OperationalState.TRAILERS) {
            showStatus = "PLAYING";
            currentTitle = "Example_TRL_01";
            currentKind = "Trailer";
        } else if (s == OperationalState.REKLAMER) {
            showStatus = "PAUSED";
            currentTitle = "ADS_EXTERNAL";
            currentKind = "Ads";
        } else if (s == OperationalState.INTRO_LOOP) {
            showStatus = "LOOP";
            currentTitle = "INTRO_LOOP";
            currentKind = "Intro";
        } else if (s == OperationalState.ENDING) {
            showStatus = "PLAYING";
        } else {
            // IDLE etc
            showStatus = "IDLE";
        }
    }

    public void advance(Duration d) {
        // Naive timeline simulation: from REKLAMER -> TRAILERS -> FEATURE -> ENDING
        OperationalState st = operational.get();
        if (st == OperationalState.REKLAMER) {
            operational.set(OperationalState.TRAILERS);
            showStatus = "PLAYING";
            currentTitle = "Example_TRL_01";
            currentKind = "Trailer";
            return;
        }
        if (st == OperationalState.TRAILERS) {
            operational.set(OperationalState.FEATURE);
            showStatus = "PLAYING";
            currentTitle = "Example_FEA_01";
            currentKind = "Feature";
            featureStartedAt = Instant.now();
            return;
        }
        if (st == OperationalState.FEATURE && featureStartedAt != null) {
            Instant now = Instant.now().plus(d);
            long elapsed = Duration.between(featureStartedAt, now).toSeconds();
            long threshold = scenario.featureDuration().minus(scenario.doorReminderOffset()).toSeconds();
            if (elapsed >= threshold) {
                operational.set(OperationalState.ENDING);
            }
        }
    }

    public void setScenario(StubScenario scenario) {
        this.scenario = scenario;
        this.operational.set(scenario.initialState());
    }
}

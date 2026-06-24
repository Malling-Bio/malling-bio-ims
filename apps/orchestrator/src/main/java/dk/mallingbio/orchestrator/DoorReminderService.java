package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenContext;
import dk.mallingbio.domain.state.ScreenEvent;
import dk.mallingbio.spl.SplCue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DoorReminderService {

    private static final Logger LOG = Logger.getLogger(DoorReminderService.class);

    private final SplService splService;

    private final Map<ScreenId, Instant> featureStartedAt = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Long> doorReminderOffsetSeconds = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, String> doorReminderCueName = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Boolean> doorReminderTriggered = new EnumMap<>(ScreenId.class);

    @Inject
    public DoorReminderService(SplService splService) {
        this.splService = splService;
    }

    /**
     * Kald denne når supervisoren laver en state transition,
     * så door reminder-servicen kan holde styr på FEATURE-livscyklussen.
     */
    public synchronized void onTransition(ScreenId screenId, OperationalState previousState, OperationalState newState) {
        boolean wasFeatureFlow = previousState == OperationalState.FEATURE || previousState == OperationalState.ENDING;

        boolean isFeatureFlow = newState == OperationalState.FEATURE || newState == OperationalState.ENDING;

        // Når vi går ind i FEATURE første gang
        if (previousState != OperationalState.FEATURE && newState == OperationalState.FEATURE) {
            featureStartedAt.put(screenId, Instant.now());
            doorReminderTriggered.put(screenId, false);

            // Cue metadata kan oplades tidligt
            resolveDoorReminderOffsetSeconds(screenId);

            LOG.infof("Door reminder initialized for %s (feature started)", screenId);
            return;
        }

        // Når vi forlader feature-forløbet, rydder vi det hele
        if (wasFeatureFlow && !isFeatureFlow) {
            featureStartedAt.remove(screenId);
            doorReminderTriggered.put(screenId, false);
            doorReminderOffsetSeconds.remove(screenId);
            doorReminderCueName.remove(screenId);

            LOG.infof("Door reminder fully reset for %s after leaving feature flow (%s -> %s)", screenId, previousState, newState);
        }
    }


    /**
     * Afgør om dørpåmindelsen bør udløses lige nu.
     * Returnerer evt. ScreenEvent.DOOR_REMINDER_REACHED.
     */
    public synchronized Optional<ScreenEvent> deriveEvent(ScreenId screenId, ScreenContext ctx) {
        if (ctx.connectivityState() != ConnectivityState.CONNECTED) {
            return Optional.empty();
        }

        if (ctx.operationalState() != OperationalState.FEATURE) {
            return Optional.empty();
        }

        if (Boolean.TRUE.equals(doorReminderTriggered.get(screenId))) {
            return Optional.empty();
        }

        Instant featureStart = featureStartedAt.get(screenId);
        if (featureStart == null) {
            // Hvis vi af en eller anden grund er i FEATURE uden at have registreret entry,
            // så initialisér pragmatisk her.
            featureStart = Instant.now();
            featureStartedAt.put(screenId, featureStart);
            doorReminderTriggered.put(screenId, false);
            resolveDoorReminderOffsetSeconds(screenId);

            LOG.infof("Door reminder lazy-initialized for %s while already in FEATURE", screenId);
            return Optional.empty();
        }

        Long offsetSeconds = resolveDoorReminderOffsetSeconds(screenId);
        if (offsetSeconds == null) {
            return Optional.empty();
        }

        Instant reminderAt = featureStart.plusSeconds(offsetSeconds);

        if (!Instant.now().isBefore(reminderAt)) {
            LOG.infof("Door reminder reached for %s (cue=%s, offsetSeconds=%s)", screenId, doorReminderCueName.get(screenId), offsetSeconds);
            return Optional.of(ScreenEvent.DOOR_REMINDER_REACHED);
        }

        return Optional.empty();
    }

    public synchronized Long getSecondsUntil(ScreenId screenId) {
        Long offsetSeconds = doorReminderOffsetSeconds.get(screenId);
        Instant featureStart = featureStartedAt.get(screenId);

        if (offsetSeconds == null || featureStart == null) {
            return null;
        }

        if (Boolean.TRUE.equals(doorReminderTriggered.get(screenId))) {
            return 0L;
        }

        Instant reminderAt = featureStart.plusSeconds(offsetSeconds);
        long secondsUntil = java.time.Duration.between(Instant.now(), reminderAt).getSeconds();

        return Math.max(0L, secondsUntil);
    }

    /**
     * Kaldes når eventet faktisk er anvendt i supervisor/state machine,
     * så det ikke fyres igen.
     */
    public synchronized void markTriggered(ScreenId screenId) {
        doorReminderTriggered.put(screenId, true);
    }

    public synchronized String getCueName(ScreenId screenId) {
        return doorReminderCueName.get(screenId);
    }

    public synchronized Long getOffsetSeconds(ScreenId screenId) {
        return doorReminderOffsetSeconds.get(screenId);
    }

    public synchronized boolean isTriggered(ScreenId screenId) {
        return Boolean.TRUE.equals(doorReminderTriggered.get(screenId));
    }

    private Long resolveDoorReminderOffsetSeconds(ScreenId screenId) {
        if (doorReminderOffsetSeconds.containsKey(screenId)) {
            return doorReminderOffsetSeconds.get(screenId);
        }

        Optional<SplCue> maybeCue = splService.findFirstCueByNameContains(screenId, "loftlys");

        if (maybeCue.isPresent()) {
            SplCue cue = maybeCue.get();
            doorReminderCueName.put(screenId, cue.cueName());
            doorReminderOffsetSeconds.put(screenId, cue.offsetSeconds());

            LOG.infof("Resolved door reminder cue for %s: cueName=%s, offsetSeconds=%s", screenId, cue.cueName(), cue.offsetSeconds());

            return cue.offsetSeconds();
        }

        doorReminderCueName.put(screenId, null);
        doorReminderOffsetSeconds.put(screenId, null);

        LOG.infof("No door reminder cue found for %s", screenId);

        return null;
    }
}
package dk.mallingbio.orchestrator.dev;

import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.ScreenSnapshot;
import dk.mallingbio.domain.state.ScreenEvent;
import dk.mallingbio.domain.state.ScreenTimingPlan;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
@IfBuildProfile("dev")
public class DevScreenSimulationService {

    private static final long PAUSED_AFTER_PUBLIC_START_SECONDS = 6;
    private static final long TRAILER_AFTER_PAUSED_SECONDS = 15;
    private static final long FEATURE_AFTER_TRAILER_SECONDS = 15;
    private static final long DOOR_REMINDER_AFTER_FEATURE_SECONDS = 45;
    private static final long SHOW_FINISHED_AFTER_DOOR_REMINDER_SECONDS = 10;
    private static final long PREPARE_NEXT_COMPLETED_AFTER_SHOW_FINISHED_SECONDS = 8;
    private static final long IDLE_AFTER_PREPARE_NEXT_COMPLETED_SECONDS = 2;

    private final Map<ScreenId, ScreenSnapshot> latestSnapshots = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Queue<SimulationStep>> pendingSteps = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Queue<ScreenEvent>> pendingEvents = new EnumMap<>(ScreenId.class);

    public synchronized void start(ScreenTimingPlan plan) {
        ScreenId screenId = plan.screenId();
        Instant now = Instant.now();

        Queue<SimulationStep> steps = new ArrayDeque<>();

        if (now.isBefore(plan.introStartAt())) {
            latestSnapshots.put(screenId, idleSnapshot(screenId, now));
            steps.add(new SimulationStep(plan.introStartAt(), SimulationAction.INTRO_LOOP_SNAPSHOT));
        } else {
            latestSnapshots.put(screenId, introLoopSnapshot(screenId, now));
        }

        Instant pausedAt = plan.publicStartAt().plusSeconds(PAUSED_AFTER_PUBLIC_START_SECONDS);
        Instant trailerAt = pausedAt.plusSeconds(TRAILER_AFTER_PAUSED_SECONDS);
        Instant featureAt = trailerAt.plusSeconds(FEATURE_AFTER_TRAILER_SECONDS);
        Instant doorReminderAt = featureAt.plusSeconds(DOOR_REMINDER_AFTER_FEATURE_SECONDS);
        Instant showFinishedAt = doorReminderAt.plusSeconds(SHOW_FINISHED_AFTER_DOOR_REMINDER_SECONDS);
        Instant prepareNextCompletedAt = showFinishedAt.plusSeconds(PREPARE_NEXT_COMPLETED_AFTER_SHOW_FINISHED_SECONDS);
        Instant idleAt = prepareNextCompletedAt.plusSeconds(IDLE_AFTER_PREPARE_NEXT_COMPLETED_SECONDS);

        steps.add(new SimulationStep(pausedAt, SimulationAction.PAUSED_SNAPSHOT));
        steps.add(new SimulationStep(trailerAt, SimulationAction.TRAILER_SNAPSHOT));
        steps.add(new SimulationStep(featureAt, SimulationAction.FEATURE_SNAPSHOT));
        steps.add(new SimulationStep(doorReminderAt, SimulationAction.DOOR_REMINDER_EVENT));
        steps.add(new SimulationStep(showFinishedAt, SimulationAction.SHOW_FINISHED_EVENT));
        steps.add(new SimulationStep(prepareNextCompletedAt, SimulationAction.PREPARE_NEXT_COMPLETED_EVENT));
        steps.add(new SimulationStep(idleAt, SimulationAction.IDLE_SNAPSHOT));

        pendingSteps.put(screenId, steps);
        pendingEvents.put(screenId, new ArrayDeque<>());
    }

    public synchronized void clear(ScreenId screenId) {
        latestSnapshots.remove(screenId);
        pendingSteps.remove(screenId);
        pendingEvents.remove(screenId);
    }

    public synchronized ScreenSnapshot getSnapshot(ScreenId screenId) {
        return latestSnapshots.get(screenId);
    }

    public synchronized Map<ScreenId, ScreenSnapshot> getLatestSnapshots() {
        return Map.copyOf(latestSnapshots);
    }

    public synchronized void refresh(Instant now) {
        for (Map.Entry<ScreenId, Queue<SimulationStep>> entry : pendingSteps.entrySet()) {
            ScreenId screenId = entry.getKey();
            Queue<SimulationStep> steps = entry.getValue();

            while (!steps.isEmpty() && !now.isBefore(steps.peek().at())) {
                SimulationStep step = steps.poll();
                applyStep(screenId, step, now);
            }
        }
    }

    public synchronized List<ScreenEvent> consumeEvents(ScreenId screenId, OperationalState currentState) {
        Queue<ScreenEvent> events = pendingEvents.get(screenId);
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<ScreenEvent> result = new ArrayList<>();

        while (!events.isEmpty()) {
            ScreenEvent next = events.peek();

            if (!isReadyForCurrentState(next, currentState)) {
                break;
            }

            result.add(events.poll());
        }

        return result;
    }

    private boolean isReadyForCurrentState(ScreenEvent event, OperationalState currentState) {
        return switch (event) {
            case DOOR_REMINDER_REACHED -> currentState == OperationalState.FEATURE;
            case SHOW_FINISHED -> currentState == OperationalState.ENDING;
            case PREPARE_NEXT_COMPLETED -> currentState == OperationalState.PREPARE_NEXT;
            default -> true;
        };
    }

    private void applyStep(ScreenId screenId, SimulationStep step, Instant now) {
        switch (step.action()) {
            case INTRO_LOOP_SNAPSHOT -> latestSnapshots.put(screenId, introLoopSnapshot(screenId, now));
            case PAUSED_SNAPSHOT -> latestSnapshots.put(screenId, pausedSnapshot(screenId, now));
            case TRAILER_SNAPSHOT -> latestSnapshots.put(screenId, trailerSnapshot(screenId, now));
            case FEATURE_SNAPSHOT -> latestSnapshots.put(screenId, featureSnapshot(screenId, now));
            case DOOR_REMINDER_EVENT ->
                    pendingEvents.computeIfAbsent(screenId, id -> new ArrayDeque<>()).add(ScreenEvent.DOOR_REMINDER_REACHED);
            case SHOW_FINISHED_EVENT ->
                    pendingEvents.computeIfAbsent(screenId, id -> new ArrayDeque<>()).add(ScreenEvent.SHOW_FINISHED);
            case PREPARE_NEXT_COMPLETED_EVENT ->
                    pendingEvents.computeIfAbsent(screenId, id -> new ArrayDeque<>()).add(ScreenEvent.PREPARE_NEXT_COMPLETED);
            case IDLE_SNAPSHOT -> latestSnapshots.put(screenId, idleSnapshot(screenId, now));
        }
    }

    private ScreenSnapshot idleSnapshot(ScreenId screenId, Instant observedAt) {
        return new ScreenSnapshot(screenId, ConnectivityState.CONNECTED, OperationalState.IDLE, true, null, null, "IDLE", null, observedAt);
    }

    private ScreenSnapshot introLoopSnapshot(ScreenId screenId, Instant observedAt) {
        return new ScreenSnapshot(screenId, ConnectivityState.CONNECTED, OperationalState.INTRO_LOOP, true, "INTRO_LOOP", "Intro", "LOOP", null, observedAt);
    }

    private ScreenSnapshot pausedSnapshot(ScreenId screenId, Instant observedAt) {
        return new ScreenSnapshot(screenId, ConnectivityState.CONNECTED, OperationalState.STARTING, false, null, null, "PAUSED", null, observedAt);
    }

    private ScreenSnapshot trailerSnapshot(ScreenId screenId, Instant observedAt) {
        return new ScreenSnapshot(screenId, ConnectivityState.CONNECTED, OperationalState.REKLAMER, false, "DemoMovie_TRL_01", "Trailer", "PLAYING", null, observedAt);
    }

    private ScreenSnapshot featureSnapshot(ScreenId screenId, Instant observedAt) {
        return new ScreenSnapshot(screenId, ConnectivityState.CONNECTED, OperationalState.TRAILERS, false, "DemoMovie_FEA_01", "Feature", "PLAYING", null, observedAt);
    }

    @PreDestroy
    void shutdown() {
        latestSnapshots.clear();
        pendingSteps.clear();
        pendingEvents.clear();
    }

    private record SimulationStep(Instant at, SimulationAction action) {
    }

    private enum SimulationAction {
        INTRO_LOOP_SNAPSHOT, PAUSED_SNAPSHOT, TRAILER_SNAPSHOT, FEATURE_SNAPSHOT, DOOR_REMINDER_EVENT, SHOW_FINISHED_EVENT, PREPARE_NEXT_COMPLETED_EVENT, IDLE_SNAPSHOT
    }
}

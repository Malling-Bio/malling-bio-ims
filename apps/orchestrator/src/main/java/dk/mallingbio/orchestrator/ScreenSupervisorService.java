package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.*;
import dk.mallingbio.domain.state.*;
import dk.mallingbio.orchestrator.dev.DevScreenSimulationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ScreenSupervisorService {

    private final ScreensPollingService polling;
    private final AppModeService appModeService;
    private final ScreenTimingService timingService;
    private final CommandExecutorService commandExecutor;
    private final DoorReminderService doorReminderService;
    private final Instance<DevScreenSimulationService> simulationService;
    private final ScreenStateMachine stateMachine = new ScreenStateMachine();

    /**
     * Supervisorens egen "sandhed" om current operational state pr. sal.
     */
    private final Map<ScreenId, ScreenRuntimeStatus> runtime = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, ActionExecutionReport> lastExecutionReports = new EnumMap<>(ScreenId.class);

    @Inject
    public ScreenSupervisorService(ScreensPollingService polling, AppModeService appModeService, ScreenTimingService timingService, CommandExecutorService commandExecutor, DoorReminderService doorReminderService, Instance<DevScreenSimulationService> simulationService) {
        this.polling = polling;
        this.appModeService = appModeService;
        this.timingService = timingService;
        this.commandExecutor = commandExecutor;
        this.doorReminderService = doorReminderService;
        this.simulationService = simulationService;
    }

    /**
     * Kaldes af REST-lag eller senere af en scheduler.
     * Læser polling snapshots og anvender automatiske transitions.
     */
    public synchronized void refreshFromSnapshots() {

        for (ScreenSnapshot snapshot : polling.getLatest().values()) {
            ScreenId screenId = snapshot.screenId();

            ScreenRuntimeStatus current = runtime.get(screenId);
            OperationalState currentState = current != null ? current.supervisedState() : snapshot.operational();

            ScreenContext ctx = new ScreenContext(screenId, appModeService.get(), snapshot.connectivity(), currentState, snapshot.schedulerRunning(), snapshot.currentContentTitle(), snapshot.currentContentKind(), snapshot.showStatus());

            // 1) timing-baseret event (IDLE -> INTRO_LOOP, INTRO_LOOP -> STARTING)
            Optional<ScreenEvent> maybeTimingEvent = deriveTimingEvent(ctx);
            if (maybeTimingEvent.isPresent()) {
                applyTransition(screenId, snapshot, ctx, maybeTimingEvent.get());
                continue;
            }

            // 2) snapshot-baserede events (PAUSED, _TRL_, _FEA_)
            Optional<ScreenEvent> maybeSnapshotEvent = deriveAutomaticEvent(ctx, snapshot);
            if (maybeSnapshotEvent.isPresent()) {
                applyTransition(screenId, snapshot, ctx, maybeSnapshotEvent.get());
                continue;
            }

            // 3) cue-/door-reminder-drevet event
            Optional<ScreenEvent> maybeDoorReminderEvent = doorReminderService.deriveEvent(screenId, ctx);
            if (maybeDoorReminderEvent.isPresent()) {
                applyTransition(screenId, snapshot, ctx, maybeDoorReminderEvent.get());
                continue;
            }


            // 4) dev-simulator events (fx DOOR_REMINDER_REACHED, SHOW_FINISHED)
            DevScreenSimulationService simulator = getSimulationServiceOrNull();
            if (simulator != null) {
                List<ScreenEvent> simulatedEvents = simulator.consumeEvents(screenId, currentState);
                if (!simulatedEvents.isEmpty()) {
                    for (ScreenEvent simulatedEvent : simulatedEvents) {
                        ScreenRuntimeStatus currentAfterEvent = runtime.get(screenId);
                        OperationalState stateAfterEvent = currentAfterEvent != null ? currentAfterEvent.supervisedState() : currentState;

                        ScreenContext eventCtx = new ScreenContext(screenId, appModeService.get(), snapshot.connectivity(), stateAfterEvent, snapshot.schedulerRunning(), snapshot.currentContentTitle(), snapshot.currentContentKind(), snapshot.showStatus());

                        applyTransition(screenId, snapshot, eventCtx, simulatedEvent);
                    }
                    continue;
                }
            }

            // 5) ingen event -> bare opdatér runtime snapshot
            ScreenTimingPlan timingPlan = timingService.getPlan(screenId).orElse(null);
            ScreenRuntimeStatus runtimeStatus = new ScreenRuntimeStatus(screenId, snapshot, currentState, List.of(), timingPlan, Instant.now());
            runtime.put(screenId, runtimeStatus);
        }
    }

    /**
     * Giver mulighed for at sende events ind manuelt/eksternt
     * (fx PUBLIC_START_REACHED, DOOR_REMINDER_REACHED, SHOW_FINISHED).
     */
    public synchronized void handleEvent(ScreenId screenId, ScreenEvent event) {
        ScreenRuntimeStatus current = runtime.get(screenId);
        if (current == null) {
            return;
        }

        // PUBLIC_START_REACHED er et automatisk timing-event og skal ikke kunne bruges i MANUAL mode
        if (event == ScreenEvent.PUBLIC_START_REACHED && appModeService.get() == AppMode.MANUAL) {
            return;
        }

        ScreenSnapshot snapshot = current.snapshot();

        ScreenContext ctx = new ScreenContext(screenId, appModeService.get(), snapshot.connectivity(), current.supervisedState(), snapshot.schedulerRunning(), snapshot.currentContentTitle(), snapshot.currentContentKind(), snapshot.showStatus());

        applyTransition(screenId, snapshot, ctx, event);
    }

    public synchronized Map<ScreenId, ScreenRuntimeStatus> getRuntime() {
        return Map.copyOf(runtime);
    }

    public synchronized ScreenRuntimeStatus get(ScreenId screenId) {
        return runtime.get(screenId);
    }

    public synchronized void putRuntime(ScreenRuntimeStatus status) {
        runtime.put(status.screenId(), status);
    }

    public synchronized ActionExecutionReport getLastExecutionReport(ScreenId screenId) {
        return lastExecutionReports.get(screenId);
    }

    public synchronized Map<ScreenId, ActionExecutionReport> getLastExecutionReports() {
        return Map.copyOf(lastExecutionReports);
    }

    public synchronized ScreenView getView(ScreenId screenId) {
        ScreenRuntimeStatus status = runtime.get(screenId);
        if (status == null) {
            return null;
        }

        DoorReminderView doorReminderView = new DoorReminderView(doorReminderService.getOffsetSeconds(screenId), doorReminderService.getSecondsUntil(screenId), doorReminderService.isTriggered(screenId));

        return new ScreenView(screenId, status, lastExecutionReports.get(screenId), doorReminderView);
    }

    public synchronized Map<ScreenId, ScreenView> getViews() {
        Map<ScreenId, ScreenView> views = new EnumMap<>(ScreenId.class);

        for (Map.Entry<ScreenId, ScreenRuntimeStatus> entry : runtime.entrySet()) {
            ScreenId screenId = entry.getKey();

            DoorReminderView doorReminderView = new DoorReminderView(doorReminderService.getOffsetSeconds(screenId), doorReminderService.getSecondsUntil(screenId), doorReminderService.isTriggered(screenId));

            views.put(screenId, new ScreenView(screenId, entry.getValue(), lastExecutionReports.get(screenId), doorReminderView));
        }

        return Map.copyOf(views);
    }

    private Optional<ScreenEvent> deriveTimingEvent(ScreenContext ctx) {
        if (ctx.connectivityState() != ConnectivityState.CONNECTED) {
            return Optional.empty();
        }


        Instant now = Instant.now();
        Optional<dk.mallingbio.domain.state.ScreenTimingPlan> maybePlan = timingService.getPlan(ctx.screenId());

        if (maybePlan.isEmpty()) {
            return Optional.empty();
        }

        var plan = maybePlan.get();

        return switch (ctx.operationalState()) {
            case IDLE -> {
                if (timingService.shouldFireIntroWindow(ctx.screenId(), now)) {
                    yield Optional.of(ScreenEvent.NEXT_SHOW_WINDOW_OPENED);
                }
                yield Optional.empty();
            }

            case INTRO_LOOP -> {
                if (ctx.appMode() == AppMode.AUTO && plan.autoStartEnabled() && timingService.shouldFirePublicStart(ctx.screenId(), now)) {
                    yield Optional.of(ScreenEvent.PUBLIC_START_REACHED);
                }
                yield Optional.empty();
            }

            default -> Optional.empty();
        };
    }

    private void applyTransition(ScreenId screenId, ScreenSnapshot snapshot, ScreenContext ctx, ScreenEvent event) {
        TransitionResult result = stateMachine.handle(ctx, event);

        OperationalState oldState = ctx.operationalState();
        OperationalState newState = result.newState();
        boolean stateChanged = newState != oldState;

        // Giv kun doorReminderService besked, hvis der faktisk skete et state-skift
        if (stateChanged) {
            doorReminderService.onTransition(screenId, oldState, newState);
        }

        // Markér kun door reminder som triggered, hvis eventet faktisk førte til ENDING
        if (event == ScreenEvent.DOOR_REMINDER_REACHED && stateChanged && newState == OperationalState.ENDING) {
            doorReminderService.markTriggered(screenId);
        }


        // Nulstil timing plan, når showet faktisk går videre til PREPARE_NEXT
        // eller defensivt hvis vi allerede er endt i IDLE
        if (newState == OperationalState.IDLE || (event == ScreenEvent.SHOW_FINISHED && stateChanged && newState == OperationalState.PREPARE_NEXT)) {
            timingService.clearPlan(screenId);
        }

        ScreenTimingPlan timingPlan = timingService.getPlan(screenId).orElse(null);

        ScreenRuntimeStatus updated = new ScreenRuntimeStatus(screenId, snapshot, newState, result.actions(), timingPlan, Instant.now());

        runtime.put(screenId, updated);

        if (!result.actions().isEmpty()) {
            ActionExecutionReport report = commandExecutor.execute(updated);
            lastExecutionReports.put(screenId, report);
        }
    }

    /**
     * Første simple event-udledning.
     * <p>
     * Senere kan denne udvides med:
     * - tid/vindue til næste show
     * - offentlig starttid nået
     * - cue-baseret dørtidspunkt
     * - show finished / prepare next completed
     */
    private Optional<ScreenEvent> deriveAutomaticEvent(ScreenContext ctx, ScreenSnapshot snapshot) {
        // Connectivity håndteres separat — state machine skal ikke ændre operational state pga offline.
        if (snapshot.connectivity() != ConnectivityState.CONNECTED) {
            return Optional.empty();
        }

        String title = snapshot.currentContentTitle();
        String kind = snapshot.currentContentKind();
        String showStatus = snapshot.showStatus();

        return switch (ctx.operationalState()) {
            case STARTING -> {
                if (showStatus != null && showStatus.equalsIgnoreCase("PAUSED")) {
                    yield Optional.of(ScreenEvent.SHOW_PAUSED_AFTER_START);
                }
                if (looksLikeTrailer(title, kind)) {
                    yield Optional.of(ScreenEvent.CURRENT_CONTENT_IS_TRAILER);
                }
                if (looksLikeFeature(title, kind)) {
                    yield Optional.of(ScreenEvent.CURRENT_CONTENT_IS_FEATURE);
                }
                yield Optional.empty();
            }

            case REKLAMER -> {
                if (looksLikeTrailer(title, kind)) {
                    yield Optional.of(ScreenEvent.CURRENT_CONTENT_IS_TRAILER);
                }
                if (looksLikeFeature(title, kind)) {
                    yield Optional.of(ScreenEvent.CURRENT_CONTENT_IS_FEATURE);
                }
                yield Optional.empty();
            }

            case TRAILERS -> {
                if (looksLikeFeature(title, kind)) {
                    yield Optional.of(ScreenEvent.CURRENT_CONTENT_IS_FEATURE);
                }
                yield Optional.empty();
            }

            default -> Optional.empty();
        };
    }

    private boolean looksLikeTrailer(String title, String kind) {
        return (kind != null && kind.equalsIgnoreCase("Trailer")) || (title != null && title.contains("_TRL_"));
    }

    private boolean looksLikeFeature(String title, String kind) {
        return (kind != null && kind.equalsIgnoreCase("Feature")) || (title != null && title.contains("_FEA_"));
    }

    private DevScreenSimulationService getSimulationServiceOrNull() {
        return simulationService.isResolvable() ? simulationService.get() : null;
    }
}
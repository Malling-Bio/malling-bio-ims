package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.*;
import dk.mallingbio.domain.state.ScreenAction;
import dk.mallingbio.domain.state.ScreenEvent;
import dk.mallingbio.domain.state.ScreenTimingPlan;
import dk.mallingbio.orchestrator.dev.DevScreenSimulationService;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScreenSupervisorServiceTest {

    private ScreensPollingService polling;
    private AppModeService appModeService;
    private ScreenTimingService timingService;
    private CommandExecutorService commandExecutor;
    private DoorReminderService doorReminderService;
    private Instance<DevScreenSimulationService> simulationService;
    private ScreenSupervisorService supervisor;

    @BeforeEach
    void setUp() {
        polling = Mockito.mock(ScreensPollingService.class);
        appModeService = Mockito.mock(AppModeService.class);
        timingService = Mockito.mock(ScreenTimingService.class);
        commandExecutor = Mockito.mock(CommandExecutorService.class);
        doorReminderService = Mockito.mock(DoorReminderService.class);
        //noinspection unchecked
        simulationService = Mockito.mock(Instance.class);

        when(appModeService.get()).thenReturn(AppMode.AUTO);

        // Default: ingen timing-events, medmindre testen specifikt siger andet
        when(timingService.shouldFireIntroWindow(Mockito.any(), Mockito.any())).thenReturn(false);
        when(timingService.shouldFirePublicStart(Mockito.any(), Mockito.any())).thenReturn(false);

        // Default: ingen cue-drevne events
        when(doorReminderService.deriveEvent(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());

        //default ingen simulering
        when(simulationService.isResolvable()).thenReturn(false);

        supervisor = new ScreenSupervisorService(polling, appModeService, timingService, commandExecutor, doorReminderService, simulationService);
    }

    // -------------------------------------------------------------------------
    // Snapshot-drevne transitions
    // -------------------------------------------------------------------------

    @Test
    void starting_and_paused_transitions_to_reklamer_without_command_execution() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.STARTING, true, null, null, "PAUSED");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.REKLAMER, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void starting_and_trailer_title_transitions_to_trailers_without_command_execution() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.STARTING, true, "SomeMovie_TRL_01", "Trailer", "PLAYING");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.TRAILERS, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void trailers_and_feature_title_transitions_to_feature_without_command_execution() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.TRAILERS, true, "SomeFilm_FEA_01", "Feature", "PLAYING");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.TRAILERS, List.of(), null, Instant.now()));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.FEATURE, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void no_connection_does_not_trigger_automatic_transition_or_command_execution() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.NO_CONNECTION, OperationalState.FEATURE, false, null, null, "NO_CONNECTION");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.FEATURE, List.of(), null, Instant.now()));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.FEATURE, status.supervisedState());
        assertEquals(ConnectivityState.NO_CONNECTION, status.snapshot().connectivity());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void no_event_only_updates_runtime_without_command_execution() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.IDLE, true, null, null, "IDLE");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.IDLE, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    // -------------------------------------------------------------------------
    // Timing-drevne transitions
    // -------------------------------------------------------------------------

    @Test
    void timing_intro_window_moves_idle_to_intro_loop_without_command_execution() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.IDLE, true, null, null, "IDLE");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        when(timingService.shouldFireIntroWindow(eq(ScreenId.SAL1), Mockito.any())).thenReturn(true);
        when(timingService.shouldFirePublicStart(eq(ScreenId.SAL1), Mockito.any())).thenReturn(false);
        when(timingService.getPlan(ScreenId.SAL1)).thenReturn(Optional.of(timingPlan(ScreenId.SAL1)));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.INTRO_LOOP, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void timing_public_start_moves_intro_loop_to_starting_and_executes_commands_in_auto_mode() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.INTRO_LOOP, true, "INTRO_LOOP", "Intro", "LOOP");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        when(timingService.shouldFireIntroWindow(eq(ScreenId.SAL1), Mockito.any())).thenReturn(false);
        when(timingService.shouldFirePublicStart(eq(ScreenId.SAL1), Mockito.any())).thenReturn(true);
        when(timingService.getPlan(ScreenId.SAL1)).thenReturn(Optional.of(timingPlan(ScreenId.SAL1)));

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.INTRO_LOOP, List.of(), null, Instant.now()));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.STARTING, status.supervisedState());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), status.lastActions());

        ArgumentCaptor<ScreenRuntimeStatus> captor = ArgumentCaptor.forClass(ScreenRuntimeStatus.class);
        verify(commandExecutor).execute(captor.capture());

        ScreenRuntimeStatus executedStatus = captor.getValue();
        assertEquals(ScreenId.SAL1, executedStatus.screenId());
        assertEquals(OperationalState.STARTING, executedStatus.supervisedState());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), executedStatus.lastActions());
    }

    @Test
    void timing_public_start_does_not_auto_start_in_manual_mode() {
        when(appModeService.get()).thenReturn(AppMode.MANUAL);

        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.INTRO_LOOP, true, "INTRO_LOOP", "Intro", "LOOP");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        when(timingService.shouldFireIntroWindow(eq(ScreenId.SAL1), Mockito.any())).thenReturn(false);
        when(timingService.shouldFirePublicStart(eq(ScreenId.SAL1), Mockito.any())).thenReturn(true);
        when(timingService.getPlan(ScreenId.SAL1)).thenReturn(Optional.of(timingPlan(ScreenId.SAL1)));

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.INTRO_LOOP, List.of(), null, Instant.now()));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.INTRO_LOOP, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void timing_events_are_ignored_when_snapshot_is_not_connected() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.NO_CONNECTION, OperationalState.IDLE, false, null, null, "NO_CONNECTION");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        when(timingService.shouldFireIntroWindow(eq(ScreenId.SAL1), Mockito.any())).thenReturn(true);
        when(timingService.shouldFirePublicStart(eq(ScreenId.SAL1), Mockito.any())).thenReturn(true);

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.IDLE, status.supervisedState());
        assertEquals(ConnectivityState.NO_CONNECTION, status.snapshot().connectivity());

        verifyNoInteractions(commandExecutor);
    }

    // -------------------------------------------------------------------------
    // Manuelle events
    // -------------------------------------------------------------------------

    @Test
    void manual_event_public_start_reached_executes_commands_when_actions_exist() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.INTRO_LOOP, true, "INTRO_LOOP", "Intro", "LOOP");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        supervisor.refreshFromSnapshots();

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.INTRO_LOOP, List.of(), null, Instant.now()));

        supervisor.handleEvent(ScreenId.SAL1, ScreenEvent.PUBLIC_START_REACHED);

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.STARTING, status.supervisedState());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), status.lastActions());

        verify(commandExecutor).execute(Mockito.any(ScreenRuntimeStatus.class));
    }

    @Test
    void manual_mode_public_start_does_not_execute_commands() {
        when(appModeService.get()).thenReturn(AppMode.MANUAL);

        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.INTRO_LOOP, true, "INTRO_LOOP", "Intro", "LOOP");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.INTRO_LOOP, List.of(), null, Instant.now()));

        supervisor.handleEvent(ScreenId.SAL1, ScreenEvent.PUBLIC_START_REACHED);

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.INTRO_LOOP, status.supervisedState());
        assertTrue(status.lastActions().isEmpty());

        verifyNoInteractions(commandExecutor);
    }

    @Test
    void feature_to_ending_with_door_reminder_executes_command_executor_with_ui_action() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.FEATURE, true, "SomeFilm_FEA_01", "Feature", "PLAYING");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.FEATURE, List.of(), null, Instant.now()));

        supervisor.handleEvent(ScreenId.SAL1, ScreenEvent.DOOR_REMINDER_REACHED);

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.ENDING, status.supervisedState());
        assertEquals(List.of(ScreenAction.ACTIVATE_DOOR_REMINDER), status.lastActions());

        verify(commandExecutor).execute(Mockito.any(ScreenRuntimeStatus.class));
    }

    @Test
    void show_finished_in_auto_mode_executes_prepare_next_actions() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.FEATURE, true, "SomeFilm_FEA_01", "Feature", "PLAYING");

        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));
        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.FEATURE, List.of(), null, Instant.now()));

        supervisor.handleEvent(ScreenId.SAL1, ScreenEvent.SHOW_FINISHED);

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.PREPARE_NEXT, status.supervisedState());
        assertEquals(List.of(ScreenAction.EJECT, ScreenAction.START_SCHEDULER), status.lastActions());

        verify(commandExecutor).execute(Mockito.any(ScreenRuntimeStatus.class));
    }

    @Test
    void get_view_includes_door_reminder_offset_seconds_until_and_triggered() {
        ScreenSnapshot snap = new ScreenSnapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.FEATURE, true, "SomeFilm_FEA_01", "Feature", "PLAYING", null, Instant.now());

        when(doorReminderService.getOffsetSeconds(ScreenId.SAL1)).thenReturn(5220L);
        when(doorReminderService.getSecondsUntil(ScreenId.SAL1)).thenReturn(4978L);
        when(doorReminderService.isTriggered(ScreenId.SAL1)).thenReturn(false);

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.FEATURE, List.of(), null, Instant.now()));

        ScreenView view = supervisor.getView(ScreenId.SAL1);

        assertNotNull(view);
        assertEquals(ScreenId.SAL1, view.screenId());

        assertNotNull(view.runtime());
        assertEquals(OperationalState.FEATURE, view.runtime().supervisedState());

        assertNotNull(view.doorReminder());
        assertEquals(5220L, view.doorReminder().offsetSeconds());
        assertEquals(4978L, view.doorReminder().secondsUntil());
        assertFalse(view.doorReminder().triggered());
    }

    @Test
    void get_view_includes_triggered_door_reminder_with_zero_seconds_until() {
        ScreenSnapshot snap = new ScreenSnapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.ENDING, true, "SomeFilm_FEA_01", "Feature", "PLAYING", null, Instant.now());

        when(doorReminderService.getOffsetSeconds(ScreenId.SAL1)).thenReturn(5220L);
        when(doorReminderService.getSecondsUntil(ScreenId.SAL1)).thenReturn(0L);
        when(doorReminderService.isTriggered(ScreenId.SAL1)).thenReturn(true);

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.ENDING, List.of(ScreenAction.ACTIVATE_DOOR_REMINDER), null, Instant.now()));

        ScreenView view = supervisor.getView(ScreenId.SAL1);

        assertNotNull(view);
        assertNotNull(view.doorReminder());
        assertEquals(5220L, view.doorReminder().offsetSeconds());
        assertEquals(0L, view.doorReminder().secondsUntil());
        assertTrue(view.doorReminder().triggered());
    }

    @Test
    void refresh_from_snapshots_applies_simulated_event_when_simulation_is_available() {
        ScreenSnapshot snap = snapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, OperationalState.FEATURE, true, "SomeFilm_FEA_01", "Feature", "PLAYING");

        DevScreenSimulationService simulator = Mockito.mock(DevScreenSimulationService.class);

        when(simulationService.isResolvable()).thenReturn(true);
        when(simulationService.get()).thenReturn(simulator);
        when(simulator.consumeEvents(ScreenId.SAL1, OperationalState.FEATURE)).thenReturn(List.of(ScreenEvent.DOOR_REMINDER_REACHED));
        when(polling.getLatest()).thenReturn(Map.of(ScreenId.SAL1, snap));

        supervisor.putRuntime(new ScreenRuntimeStatus(ScreenId.SAL1, snap, OperationalState.FEATURE, List.of(), null, Instant.now()));

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus status = supervisor.get(ScreenId.SAL1);

        assertNotNull(status);
        assertEquals(OperationalState.ENDING, status.supervisedState());
        assertEquals(List.of(ScreenAction.ACTIVATE_DOOR_REMINDER), status.lastActions());

        verify(simulator).consumeEvents(ScreenId.SAL1, OperationalState.FEATURE);
        verify(commandExecutor).execute(Mockito.any(ScreenRuntimeStatus.class));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static ScreenSnapshot snapshot(ScreenId screenId, ConnectivityState connectivity, OperationalState operational, boolean schedulerRunning, String currentContentTitle, String currentContentKind, String showStatus) {
        return new ScreenSnapshot(screenId, connectivity, operational, schedulerRunning, currentContentTitle, currentContentKind, showStatus, null, Instant.now());
    }

    private static ScreenTimingPlan timingPlan(ScreenId screenId) {
        Instant introStartAt = Instant.parse("2026-06-21T17:45:00Z");
        Instant publicStartAt = Instant.parse("2026-06-21T18:00:00Z");

        return new ScreenTimingPlan(screenId, introStartAt, publicStartAt, true);
    }
}

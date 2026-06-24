package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenAction;
import dk.mallingbio.ims.ImsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandExecutorServiceTest {

    private ImsRegistry registry;
    private AppModeService appModeService;
    private ImsClient imsClient;
    private CommandExecutorService executor;

    @BeforeEach
    void setUp() {
        registry = Mockito.mock(ImsRegistry.class);
        appModeService = Mockito.mock(AppModeService.class);
        imsClient = Mockito.mock(ImsClient.class);

        when(registry.client(ScreenId.SAL1)).thenReturn(imsClient);
        when(appModeService.get()).thenReturn(AppMode.AUTO);

        executor = new CommandExecutorService(registry, appModeService);
    }

    @Test
    void executes_stopScheduler_and_play_in_auto_mode_when_connected() {
        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY));

        verify(imsClient).stopScheduler();
        verify(imsClient).play();

        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), report.requestedActions());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), report.executedActions());
        assertTrue(report.skippedActions().isEmpty());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void executes_eject_and_startScheduler_in_auto_mode_when_connected() {
        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.EJECT, ScreenAction.START_SCHEDULER));

        verify(imsClient).eject();
        verify(imsClient).startScheduler();

        assertEquals(List.of(ScreenAction.EJECT, ScreenAction.START_SCHEDULER), report.executedActions());
        assertTrue(report.skippedActions().isEmpty());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void skips_all_actions_in_manual_mode() {
        when(appModeService.get()).thenReturn(AppMode.MANUAL);

        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY));

        verifyNoInteractions(imsClient);

        assertTrue(report.executedActions().isEmpty());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), report.skippedActions());
        assertFalse(report.errors().isEmpty());
        assertTrue(report.errors().getFirst().contains("MANUAL"));
    }

    @Test
    void skips_all_actions_in_maintenance_mode() {
        when(appModeService.get()).thenReturn(AppMode.MAINTENANCE);

        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY));

        verifyNoInteractions(imsClient);

        assertTrue(report.executedActions().isEmpty());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), report.skippedActions());
        assertFalse(report.errors().isEmpty());
        assertTrue(report.errors().getFirst().contains("MAINTENANCE"));
    }

    @Test
    void skips_all_actions_when_not_connected() {
        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.NO_CONNECTION, List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY));

        verifyNoInteractions(imsClient);

        assertTrue(report.executedActions().isEmpty());
        assertEquals(List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY), report.skippedActions());
        assertFalse(report.errors().isEmpty());
        assertTrue(report.errors().getFirst().contains("NO_CONNECTION"));
    }

    @Test
    void skips_ui_only_action_activateDoorReminder() {
        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.ACTIVATE_DOOR_REMINDER));

        verifyNoInteractions(imsClient);

        assertTrue(report.executedActions().isEmpty());
        assertEquals(List.of(ScreenAction.ACTIVATE_DOOR_REMINDER), report.skippedActions());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void skips_prepareIntroLoop_placeholder_action() {
        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.PREPARE_INTRO_LOOP));

        verifyNoInteractions(imsClient);

        assertTrue(report.executedActions().isEmpty());
        assertEquals(List.of(ScreenAction.PREPARE_INTRO_LOOP), report.skippedActions());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void continues_and_collects_error_when_one_action_fails() {
        doThrow(new RuntimeException("boom")).when(imsClient).stopScheduler();

        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY));

        verify(imsClient).stopScheduler();
        verify(imsClient).play();

        assertEquals(List.of(ScreenAction.PLAY), report.executedActions());
        assertTrue(report.skippedActions().isEmpty());
        assertEquals(1, report.errors().size());
        assertTrue(report.errors().getFirst().contains("STOP_SCHEDULER"));
    }

    @Test
    void returns_empty_report_when_action_list_is_empty() {
        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of());

        verifyNoInteractions(imsClient);

        assertTrue(report.requestedActions().isEmpty());
        assertTrue(report.executedActions().isEmpty());
        assertTrue(report.skippedActions().isEmpty());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void returns_error_when_no_client_registered() {
        when(registry.client(ScreenId.SAL1)).thenReturn(null);

        ActionExecutionReport report = executor.execute(ScreenId.SAL1, ConnectivityState.CONNECTED, List.of(ScreenAction.PLAY));

        assertTrue(report.executedActions().isEmpty());
        assertEquals(List.of(ScreenAction.PLAY), report.skippedActions());
        assertEquals(1, report.errors().size());
        assertTrue(report.errors().getFirst().contains("No ImsClient registered"));
    }

    @Test
    void execute_runtime_status_delegates_to_main_execute_method() {
        ScreenRuntimeStatus runtimeStatus = new ScreenRuntimeStatus(ScreenId.SAL1, new dk.mallingbio.domain.ScreenSnapshot(ScreenId.SAL1, ConnectivityState.CONNECTED, dk.mallingbio.domain.OperationalState.STARTING, true, null, null, "IDLE", null, java.time.Instant.now()), dk.mallingbio.domain.OperationalState.STARTING, List.of(ScreenAction.PLAY), null, java.time.Instant.now());

        ActionExecutionReport report = executor.execute(runtimeStatus);

        verify(imsClient).play();

        assertEquals(List.of(ScreenAction.PLAY), report.executedActions());
        assertTrue(report.skippedActions().isEmpty());
        assertTrue(report.errors().isEmpty());
    }
}
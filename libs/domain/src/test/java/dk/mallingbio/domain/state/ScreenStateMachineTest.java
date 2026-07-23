package dk.mallingbio.domain.state;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenStateMachineTest {

    private final ScreenStateMachine machine = new ScreenStateMachine();

    @Test
    void idle_to_introLoop_when_nextShowWindowOpened() {
        ScreenContext ctx = ctx(OperationalState.IDLE, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.NEXT_SHOW_WINDOW_OPENED);

        assertEquals(OperationalState.INTRO_LOOP, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void introLoop_to_starting_with_stopScheduler_and_play_when_publicStartReached_in_auto_mode() {
        ScreenContext ctx = ctx(OperationalState.INTRO_LOOP, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.PUBLIC_START_REACHED);

        assertEquals(OperationalState.STARTING, result.newState());
        assertEquals(
                List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY),
                result.actions()
        );
    }

    @Test
    void introLoop_stays_introLoop_when_publicStartReached_in_manual_mode() {
        ScreenContext ctx = ctx(OperationalState.INTRO_LOOP, AppMode.MANUAL, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.PUBLIC_START_REACHED);

        assertEquals(OperationalState.INTRO_LOOP, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void introLoop_to_starting_when_earlyStartRequested_and_commands_allowed() {
        ScreenContext ctx = ctx(OperationalState.INTRO_LOOP, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.MANUAL_EARLY_START_REQUESTED);

        assertEquals(OperationalState.STARTING, result.newState());
        assertEquals(
                List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY),
                result.actions()
        );
    }

    @Test
    void introLoop_to_starting_when_manualStartRequested_in_manual_mode() {
        ScreenContext ctx = ctx(OperationalState.INTRO_LOOP, AppMode.MANUAL, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.MANUAL_START_REQUESTED);

        assertEquals(OperationalState.STARTING, result.newState());
        assertEquals(
                List.of(ScreenAction.STOP_SCHEDULER, ScreenAction.PLAY),
                result.actions()
        );
    }

    @Test
    void introLoop_stays_when_manualStartRequested_in_auto_mode() {
        ScreenContext ctx = ctx(OperationalState.INTRO_LOOP, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.MANUAL_START_REQUESTED);

        assertEquals(OperationalState.INTRO_LOOP, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void introLoop_stays_when_delayRequested() {
        ScreenContext ctx = ctx(OperationalState.INTRO_LOOP, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.MANUAL_DELAY_REQUESTED);

        assertEquals(OperationalState.INTRO_LOOP, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void starting_to_reklamer_when_showPausedAfterStart() {
        ScreenContext ctx = ctx(OperationalState.STARTING, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.SHOW_PAUSED_AFTER_START);

        assertEquals(OperationalState.REKLAMER, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void starting_to_trailers_when_currentContentIsTrailer() {
        ScreenContext ctx = ctx(OperationalState.STARTING, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.CURRENT_CONTENT_IS_TRAILER);

        assertEquals(OperationalState.TRAILERS, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void starting_to_feature_when_currentContentIsFeature() {
        ScreenContext ctx = ctx(OperationalState.STARTING, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.CURRENT_CONTENT_IS_FEATURE);

        assertEquals(OperationalState.FEATURE, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void reklamer_to_trailers_when_currentContentIsTrailer() {
        ScreenContext ctx = ctx(OperationalState.REKLAMER, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.CURRENT_CONTENT_IS_TRAILER);

        assertEquals(OperationalState.TRAILERS, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void reklamer_to_feature_when_currentContentIsFeature() {
        ScreenContext ctx = ctx(OperationalState.REKLAMER, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.CURRENT_CONTENT_IS_FEATURE);

        assertEquals(OperationalState.FEATURE, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void trailers_to_feature_when_currentContentIsFeature() {
        ScreenContext ctx = ctx(OperationalState.TRAILERS, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.CURRENT_CONTENT_IS_FEATURE);

        assertEquals(OperationalState.FEATURE, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void feature_to_ending_when_doorReminderReached() {
        ScreenContext ctx = ctx(OperationalState.FEATURE, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.DOOR_REMINDER_REACHED);

        assertEquals(OperationalState.ENDING, result.newState());
        assertEquals(List.of(ScreenAction.ACTIVATE_DOOR_REMINDER), result.actions());
    }

    @Test
    void feature_to_prepareNext_with_eject_and_startScheduler_when_showFinished_in_auto_mode() {
        ScreenContext ctx = ctx(OperationalState.FEATURE, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.SHOW_FINISHED);

        assertEquals(OperationalState.PREPARE_NEXT, result.newState());
        assertEquals(
                List.of(ScreenAction.EJECT, ScreenAction.START_SCHEDULER),
                result.actions()
        );
    }

    @Test
    void feature_to_prepareNext_without_actions_when_showFinished_in_manual_mode() {
        ScreenContext ctx = ctx(OperationalState.FEATURE, AppMode.MANUAL, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.SHOW_FINISHED);

        assertEquals(OperationalState.PREPARE_NEXT, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void ending_stays_when_doorsConfirmed() {
        ScreenContext ctx = ctx(OperationalState.ENDING, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.MANUAL_DOORS_CONFIRMED);

        assertEquals(OperationalState.ENDING, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void ending_to_prepareNext_when_showFinished_in_auto_mode() {
        ScreenContext ctx = ctx(OperationalState.ENDING, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.SHOW_FINISHED);

        assertEquals(OperationalState.PREPARE_NEXT, result.newState());
        assertEquals(
                List.of(ScreenAction.EJECT, ScreenAction.START_SCHEDULER),
                result.actions()
        );
    }

    @Test
    void prepareNext_to_idle_when_prepareNextCompleted() {
        ScreenContext ctx = ctx(OperationalState.PREPARE_NEXT, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.PREPARE_NEXT_COMPLETED);

        assertEquals(OperationalState.IDLE, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void no_state_change_when_not_connected() {
        ScreenContext ctx = ctx(OperationalState.FEATURE, AppMode.AUTO, ConnectivityState.NO_CONNECTION);

        TransitionResult result = machine.handle(ctx, ScreenEvent.DOOR_REMINDER_REACHED);

        assertEquals(OperationalState.FEATURE, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void no_state_change_for_unknown_event_in_state() {
        ScreenContext ctx = ctx(OperationalState.IDLE, AppMode.AUTO, ConnectivityState.CONNECTED);

        TransitionResult result = machine.handle(ctx, ScreenEvent.SHOW_FINISHED);

        assertEquals(OperationalState.IDLE, result.newState());
        assertTrue(result.actions().isEmpty());
    }

    private static ScreenContext ctx(
            OperationalState operationalState,
            AppMode appMode,
            ConnectivityState connectivityState
    ) {
        return new ScreenContext(
                ScreenId.SAL1,
                appMode,
                connectivityState,
                operationalState,
                true,
                null,
                null,
                "IDLE"
        );
    }
}
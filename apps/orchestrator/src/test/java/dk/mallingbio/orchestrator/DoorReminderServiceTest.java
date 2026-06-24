package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenContext;
import dk.mallingbio.domain.state.ScreenEvent;
import dk.mallingbio.spl.SplCue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoorReminderServiceTest {

    private SplService splService;
    private DoorReminderService service;

    @BeforeEach
    void setUp() {
        splService = Mockito.mock(SplService.class);
        service = new DoorReminderService(splService);
    }

    @Test
    void returns_empty_when_not_connected() {
        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.NO_CONNECTION,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(event.isEmpty());
        assertNull(service.getSecondsUntil(ScreenId.SAL1));
        verifyNoInteractions(splService);
    }

    @Test
    void returns_empty_when_not_in_feature() {
        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.TRAILERS
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(event.isEmpty());
        assertNull(service.getSecondsUntil(ScreenId.SAL1));
        verifyNoInteractions(splService);
    }

    @Test
    void on_transition_to_feature_initializes_tracking_and_loads_cue_metadata() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "5400", 5400L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        assertEquals("loftlys skelne", service.getCueName(ScreenId.SAL1));
        assertEquals(5400L, service.getOffsetSeconds(ScreenId.SAL1));
        assertFalse(service.isTriggered(ScreenId.SAL1));

        Long secondsUntil = service.getSecondsUntil(ScreenId.SAL1);
        assertNotNull(secondsUntil);
        assertTrue(secondsUntil >= 0L);
        assertTrue(secondsUntil <= 5400L);

        verify(splService).findFirstCueByNameContains(ScreenId.SAL1, "loftlys");
    }

    @Test
    void feature_with_no_matching_cue_returns_empty_and_no_countdown() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.empty());

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(event.isEmpty());
        assertNull(service.getCueName(ScreenId.SAL1));
        assertNull(service.getOffsetSeconds(ScreenId.SAL1));
        assertNull(service.getSecondsUntil(ScreenId.SAL1));
    }

    @Test
    void feature_with_large_offset_does_not_trigger_immediately_and_has_positive_countdown() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "3600", 3600L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(event.isEmpty());
        assertFalse(service.isTriggered(ScreenId.SAL1));

        Long secondsUntil = service.getSecondsUntil(ScreenId.SAL1);
        assertNotNull(secondsUntil);
        assertTrue(secondsUntil > 0L);
        assertTrue(secondsUntil <= 3600L);
    }

    @Test
    void feature_with_zero_offset_triggers_door_reminder() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "0", 0L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(event.isPresent());
        assertEquals(ScreenEvent.DOOR_REMINDER_REACHED, event.get());

        Long secondsUntil = service.getSecondsUntil(ScreenId.SAL1);
        assertNotNull(secondsUntil);
        assertEquals(0L, secondsUntil);
    }

    @Test
    void mark_triggered_sets_trigger_flag_and_seconds_until_zero() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "120", 120L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        service.markTriggered(ScreenId.SAL1);

        assertTrue(service.isTriggered(ScreenId.SAL1));
        assertEquals(0L, service.getSecondsUntil(ScreenId.SAL1));
    }

    @Test
    void triggered_reminder_does_not_fire_again() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "0", 0L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        service.markTriggered(ScreenId.SAL1);

        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(event.isEmpty());
        assertTrue(service.isTriggered(ScreenId.SAL1));
        assertEquals(0L, service.getSecondsUntil(ScreenId.SAL1));
    }

    @Test
    void lazy_initialization_when_already_in_feature_does_not_fire_on_first_check_but_initializes_countdown() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "0", 0L)));

        // Ingen onTransition(...) kald her
        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> first = service.deriveEvent(ScreenId.SAL1, ctx);

        assertTrue(first.isEmpty(), "første check bør lazy-init'e og ikke fyre event med det samme");
        assertEquals("loftlys skelne", service.getCueName(ScreenId.SAL1));
        assertEquals(0L, service.getOffsetSeconds(ScreenId.SAL1));
        assertFalse(service.isTriggered(ScreenId.SAL1));

        Long secondsUntil = service.getSecondsUntil(ScreenId.SAL1);
        assertNotNull(secondsUntil);
        assertEquals(0L, secondsUntil);
    }

    @Test
    void on_transition_to_idle_resets_feature_tracking_and_trigger_flag() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "0", 0L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );
        service.markTriggered(ScreenId.SAL1);

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.FEATURE,
                OperationalState.IDLE
        );

        assertFalse(service.isTriggered(ScreenId.SAL1));
        assertNull(service.getSecondsUntil(ScreenId.SAL1));

        ScreenContext ctx = context(
                ScreenId.SAL1,
                AppMode.AUTO,
                ConnectivityState.CONNECTED,
                OperationalState.FEATURE
        );

        Optional<ScreenEvent> event = service.deriveEvent(ScreenId.SAL1, ctx);

        // Efter reset vil første deriveEvent lazy-init'e igen og returnere empty i første runde
        assertTrue(event.isEmpty());
    }

    @Test
    void on_transition_to_prepare_next_resets_feature_tracking_and_trigger_flag() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "0", 0L)));

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.FEATURE,
                OperationalState.ENDING
        );
        service.markTriggered(ScreenId.SAL1);

        service.onTransition(
                ScreenId.SAL1,
                OperationalState.ENDING,
                OperationalState.PREPARE_NEXT
        );

        assertFalse(service.isTriggered(ScreenId.SAL1));
        assertNull(service.getSecondsUntil(ScreenId.SAL1));
    }

    @Test
    void on_transition_from_feature_to_intro_loop_cleans_up_door_reminder_state() {
        when(splService.findFirstCueByNameContains(ScreenId.SAL1, "loftlys"))
                .thenReturn(Optional.of(new SplCue("cue", "loftlys skelne", "120", 120L)));

        // Initialisér reminder-state som om vi er gået ind i FEATURE
        service.onTransition(
                ScreenId.SAL1,
                OperationalState.TRAILERS,
                OperationalState.FEATURE
        );

        // Sanity check før cleanup
        assertEquals("loftlys skelne", service.getCueName(ScreenId.SAL1));
        assertEquals(120L, service.getOffsetSeconds(ScreenId.SAL1));
        assertNotNull(service.getSecondsUntil(ScreenId.SAL1));
        assertFalse(service.isTriggered(ScreenId.SAL1));

        // Forlad feature-flowet
        service.onTransition(
                ScreenId.SAL1,
                OperationalState.FEATURE,
                OperationalState.INTRO_LOOP
        );

        // Alt skal være ryddet
        assertNull(service.getCueName(ScreenId.SAL1));
        assertNull(service.getOffsetSeconds(ScreenId.SAL1));
        assertNull(service.getSecondsUntil(ScreenId.SAL1));
        assertFalse(service.isTriggered(ScreenId.SAL1));
    }

    private static ScreenContext context(
            ScreenId screenId,
            AppMode appMode,
            ConnectivityState connectivityState,
            OperationalState operationalState
    ) {
        return new ScreenContext(
                screenId,
                appMode,
                connectivityState,
                operationalState,
                true,
                "SomeFilm_FEA_01",
                "Feature",
                "PLAYING"
        );
    }
}
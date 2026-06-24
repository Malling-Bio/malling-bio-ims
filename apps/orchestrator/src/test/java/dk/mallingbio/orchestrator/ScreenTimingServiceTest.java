package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenTimingPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ScreenTimingServiceTest {

    private final ScreenTimingService timingService = new ScreenTimingService();

    @Test
    void intro_window_fires_when_now_is_after_intro_start() {
        ScreenTimingPlan plan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(plan);

        boolean fired = timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:01Z")
        );

        assertTrue(fired);
    }

    @Test
    void intro_window_does_not_fire_before_intro_start() {
        ScreenTimingPlan plan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(plan);

        boolean fired = timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T19:59:59Z")
        );

        assertFalse(fired);
    }

    @Test
    void intro_window_only_fires_once() {
        ScreenTimingPlan plan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(plan);

        boolean first = timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:01Z")
        );

        boolean second = timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:05Z")
        );

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void public_start_fires_when_now_is_after_public_start() {
        ScreenTimingPlan plan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(plan);

        boolean fired = timingService.shouldFirePublicStart(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:15:01Z")
        );

        assertTrue(fired);
    }

    @Test
    void public_start_does_not_fire_before_public_start() {
        ScreenTimingPlan plan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(plan);

        boolean fired = timingService.shouldFirePublicStart(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:14:59Z")
        );

        assertFalse(fired);
    }

    @Test
    void public_start_only_fires_once() {
        ScreenTimingPlan plan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(plan);

        boolean first = timingService.shouldFirePublicStart(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:15:01Z")
        );

        boolean second = timingService.shouldFirePublicStart(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:15:10Z")
        );

        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void setting_new_plan_resets_fired_flags() {
        ScreenTimingPlan firstPlan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z"),
                Instant.parse("2026-06-14T20:15:00Z"),
                true
        );

        timingService.setPlan(firstPlan);

        assertTrue(timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:01Z")
        ));

        assertFalse(timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:10Z")
        ));

        ScreenTimingPlan secondPlan = new ScreenTimingPlan(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T21:00:00Z"),
                Instant.parse("2026-06-14T21:15:00Z"),
                true
        );

        timingService.setPlan(secondPlan);

        assertTrue(timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T21:00:01Z")
        ));
    }

    @Test
    void no_plan_means_no_event() {
        assertFalse(timingService.shouldFireIntroWindow(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:00:00Z")
        ));

        assertFalse(timingService.shouldFirePublicStart(
                ScreenId.SAL1,
                Instant.parse("2026-06-14T20:15:00Z")
        ));
    }
}

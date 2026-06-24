package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenTimingPlan;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ScreenTimingService {

    private final Map<ScreenId, ScreenTimingPlan> plans = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Boolean> introEventFired = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Boolean> publicStartEventFired = new EnumMap<>(ScreenId.class);

    public synchronized void setPlan(ScreenTimingPlan plan) {
        plans.put(plan.screenId(), plan);
        introEventFired.put(plan.screenId(), false);
        publicStartEventFired.put(plan.screenId(), false);
    }

    public synchronized Optional<ScreenTimingPlan> getPlan(ScreenId screenId) {
        return Optional.ofNullable(plans.get(screenId));
    }

    public synchronized void clearPlan(ScreenId screenId) {
        plans.remove(screenId);
        introEventFired.remove(screenId);
        publicStartEventFired.remove(screenId);
    }

    public synchronized boolean shouldFireIntroWindow(ScreenId screenId, Instant now) {
        ScreenTimingPlan plan = plans.get(screenId);
        if (plan == null) {
            return false;
        }

        boolean alreadyFired = introEventFired.getOrDefault(screenId, false);
        if (alreadyFired) {
            return false;
        }

        if (!now.isBefore(plan.introStartAt())) {
            introEventFired.put(screenId, true);
            return true;
        }

        return false;
    }

    public synchronized boolean shouldFirePublicStart(ScreenId screenId, Instant now) {
        ScreenTimingPlan plan = plans.get(screenId);
        if (plan == null) {
            return false;
        }

        boolean alreadyFired = publicStartEventFired.getOrDefault(screenId, false);
        if (alreadyFired) {
            return false;
        }

        if (!now.isBefore(plan.publicStartAt())) {
            publicStartEventFired.put(screenId, true);
            return true;
        }

        return false;
    }
}

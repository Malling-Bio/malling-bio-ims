package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.AppMode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AppModeService {

    private final AtomicReference<AppMode> mode = new AtomicReference<>(AppMode.AUTO);

    public AppMode get() {
        return mode.get();
    }

    public AppMode set(AppMode newMode) {
        mode.set(newMode);
        return newMode;
    }

    public boolean commandsAllowed() {
        return mode.get() == AppMode.AUTO;
    }

    public boolean readOnly() {
        return mode.get() == AppMode.MANUAL || mode.get() == AppMode.MAINTENANCE;
    }
}

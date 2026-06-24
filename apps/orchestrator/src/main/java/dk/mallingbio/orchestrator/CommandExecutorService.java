package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenAction;
import dk.mallingbio.ims.ImsClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CommandExecutorService {

    private static final Logger LOG = Logger.getLogger(CommandExecutorService.class);

    private final ImsRegistry registry;
    private final AppModeService appModeService;

    @Inject
    public CommandExecutorService(ImsRegistry registry, AppModeService appModeService) {
        this.registry = registry;
        this.appModeService = appModeService;
    }

    /**
     * Udfører de actions der ligger på en runtime-status.
     */
    public ActionExecutionReport execute(ScreenRuntimeStatus runtimeStatus) {
        return execute(
                runtimeStatus.screenId(),
                runtimeStatus.snapshot().connectivity(),
                runtimeStatus.lastActions()
        );
    }

    /**
     * Udfører actions for en given sal — men kun hvis driftstilstanden tillader det.
     */
    public ActionExecutionReport execute(
            ScreenId screenId,
            ConnectivityState connectivityState,
            List<ScreenAction> actions
    ) {
        if (actions == null || actions.isEmpty()) {
            return ActionExecutionReport.empty(screenId, List.of());
        }

        List<ScreenAction> executed = new ArrayList<>();
        List<ScreenAction> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        AppMode appMode = appModeService.get();

        if (appMode != AppMode.AUTO) {
            LOG.infof(
                    "Skipping actions for %s because app mode is %s: %s",
                    screenId, appMode, actions
            );
            skipped.addAll(actions);
            return new ActionExecutionReport(
                    screenId,
                    List.copyOf(actions),
                    List.copyOf(executed),
                    List.copyOf(skipped),
                    List.of("Commands are disabled because app mode is " + appMode),
                    Instant.now()
            );
        }

        if (connectivityState != ConnectivityState.CONNECTED) {
            LOG.infof(
                    "Skipping actions for %s because connectivity is %s: %s",
                    screenId, connectivityState, actions
            );
            skipped.addAll(actions);
            return new ActionExecutionReport(
                    screenId,
                    List.copyOf(actions),
                    List.copyOf(executed),
                    List.copyOf(skipped),
                    List.of("Commands are disabled because connectivity is " + connectivityState),
                    Instant.now()
            );
        }

        ImsClient client = registry.client(screenId);
        if (client == null) {
            skipped.addAll(actions);
            return new ActionExecutionReport(
                    screenId,
                    List.copyOf(actions),
                    List.copyOf(executed),
                    List.copyOf(skipped),
                    List.of("No ImsClient registered for " + screenId),
                    Instant.now()
            );
        }

        for (ScreenAction action : actions) {
            try {
                switch (action) {
                    case STOP_SCHEDULER -> {
                        client.stopScheduler();
                        executed.add(action);
                        LOG.infof("Executed %s for %s", action, screenId);
                    }
                    case START_SCHEDULER -> {
                        client.startScheduler();
                        executed.add(action);
                        LOG.infof("Executed %s for %s", action, screenId);
                    }
                    case PLAY -> {
                        client.play();
                        executed.add(action);
                        LOG.infof("Executed %s for %s", action, screenId);
                    }
                    case EJECT -> {
                        client.eject();
                        executed.add(action);
                        LOG.infof("Executed %s for %s", action, screenId);
                    }

                    case ACTIVATE_DOOR_REMINDER -> {
                        // Første version: UI-only action.
                        // Der er ikke noget at sende til IMS endnu.
                        skipped.add(action);
                        LOG.infof("Skipped %s for %s (UI-only in first version)", action, screenId);
                    }

                    case PREPARE_INTRO_LOOP -> {
                        // Første version: placeholder til senere
                        // (fx load show, loop mode, intro assets, etc.)
                        skipped.add(action);
                        LOG.infof("Skipped %s for %s (not implemented yet)", action, screenId);
                    }
                }
            } catch (Exception ex) {
                errors.add(action + ": " + ex.getMessage());
                LOG.errorf(ex, "Failed to execute %s for %s", action, screenId);
            }
        }

        return new ActionExecutionReport(
                screenId,
                List.copyOf(actions),
                List.copyOf(executed),
                List.copyOf(skipped),
                List.copyOf(errors),
                Instant.now()
        );
    }
}


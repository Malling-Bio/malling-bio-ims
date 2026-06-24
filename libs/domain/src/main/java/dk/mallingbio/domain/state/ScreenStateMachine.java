package dk.mallingbio.domain.state;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.domain.OperationalState;

public class ScreenStateMachine {

    public TransitionResult handle(ScreenContext ctx, ScreenEvent event) {
        // Hvis der ikke er forbindelse, ændrer vi ikke operational state her.
        // Connectivity håndteres separat i polling/connectivity-laget.
        if (!ctx.isConnected()) {
            return TransitionResult.noChange(ctx.operationalState());
        }

        return switch (ctx.operationalState()) {
            case IDLE -> handleIdle(ctx, event);
            case INTRO_LOOP -> handleIntroLoop(ctx, event);
            case STARTING -> handleStarting(ctx, event);
            case REKLAMER -> handleReklamer(ctx, event);
            case TRAILERS -> handleTrailers(ctx, event);
            case FEATURE -> handleFeature(ctx, event);
            case ENDING -> handleEnding(ctx, event);
            case PREPARE_NEXT -> handlePrepareNext(ctx, event);
        };
    }

    private TransitionResult handleIdle(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case NEXT_SHOW_WINDOW_OPENED -> {
                // I første version laver vi ikke automatisk SOAP-kald her.
                // Vi skifter bare state, så UI kan vise INTRO_LOOP / countdown.
                yield TransitionResult.to(OperationalState.INTRO_LOOP);
            }
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handleIntroLoop(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case PUBLIC_START_REACHED -> {
                if (ctx.appMode() == AppMode.AUTO) {
                    yield TransitionResult.to(
                            OperationalState.STARTING,
                            ScreenAction.STOP_SCHEDULER,
                            ScreenAction.PLAY
                    );
                } else {
                    // I MANUAL / MAINTENANCE udfører vi ikke commands.
                    // Vi bliver i INTRO_LOOP og lader UI vise påmindelser.
                    yield TransitionResult.noChange(ctx.operationalState());
                }
            }
            case MANUAL_EARLY_START_REQUESTED -> {
                if (ctx.commandsAllowed()) {
                    yield TransitionResult.to(
                            OperationalState.STARTING,
                            ScreenAction.STOP_SCHEDULER,
                            ScreenAction.PLAY
                    );
                } else {
                    yield TransitionResult.noChange(ctx.operationalState());
                }
            }
            case MANUAL_DELAY_REQUESTED -> {
                // Delay håndteres uden state-skift i første version.
                // Selve ny starttid bør ligge i en separat countdown/schedule model.
                yield TransitionResult.noChange(ctx.operationalState());
            }
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handleStarting(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case SHOW_PAUSED_AFTER_START -> TransitionResult.to(OperationalState.REKLAMER);
            case CURRENT_CONTENT_IS_TRAILER -> TransitionResult.to(OperationalState.TRAILERS);
            case CURRENT_CONTENT_IS_FEATURE -> TransitionResult.to(OperationalState.FEATURE);
            case START_FAILED -> TransitionResult.to(OperationalState.INTRO_LOOP);
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handleReklamer(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case CURRENT_CONTENT_IS_TRAILER -> TransitionResult.to(OperationalState.TRAILERS);
            case CURRENT_CONTENT_IS_FEATURE -> TransitionResult.to(OperationalState.FEATURE);
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handleTrailers(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case CURRENT_CONTENT_IS_FEATURE -> TransitionResult.to(OperationalState.FEATURE);
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handleFeature(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case DOOR_REMINDER_REACHED -> TransitionResult.to(
                    OperationalState.ENDING,
                    ScreenAction.ACTIVATE_DOOR_REMINDER
            );
            case SHOW_FINISHED -> {
                // Hvis vi af en eller anden grund springer direkte til slut
                if (ctx.commandsAllowed()) {
                    yield TransitionResult.to(
                            OperationalState.PREPARE_NEXT,
                            ScreenAction.EJECT,
                            ScreenAction.START_SCHEDULER
                    );
                } else {
                    yield TransitionResult.to(OperationalState.PREPARE_NEXT);
                }
            }
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handleEnding(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case MANUAL_DOORS_CONFIRMED -> {
                // UI-only event i første version.
                // Vi bliver i ENDING indtil showet faktisk er slut.
                yield TransitionResult.noChange(ctx.operationalState());
            }
            case SHOW_FINISHED -> {
                if (ctx.commandsAllowed()) {
                    yield TransitionResult.to(
                            OperationalState.PREPARE_NEXT,
                            ScreenAction.EJECT,
                            ScreenAction.START_SCHEDULER
                    );
                } else {
                    yield TransitionResult.to(OperationalState.PREPARE_NEXT);
                }
            }
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }

    private TransitionResult handlePrepareNext(ScreenContext ctx, ScreenEvent event) {
        return switch (event) {
            case PREPARE_NEXT_COMPLETED -> TransitionResult.to(OperationalState.IDLE);
            default -> TransitionResult.noChange(ctx.operationalState());
        };
    }
}
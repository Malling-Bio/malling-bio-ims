package dk.mallingbio.orchestrator.api.dev;

import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.orchestrator.ScreenRuntimeStatus;
import dk.mallingbio.orchestrator.ScreenSupervisorService;
import dk.mallingbio.orchestrator.api.StateRequest;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;

@Path("/api/dev/supervisor")
@IfBuildProfile("dev")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupervisorDevResource {

    private final ScreenSupervisorService supervisor;

    public SupervisorDevResource(ScreenSupervisorService supervisor) {
        this.supervisor = supervisor;
    }

    @POST
    @Path("/{screenId}/state")
    public Map<String, Object> setSupervisedState(@PathParam("screenId") String screenIdRaw,
                                                  StateRequest request) {

        if (request == null || request.state() == null || request.state().isBlank()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Missing state",
                                    "message", "Request body must contain a non-empty 'state' field"
                            ))
                            .build()
            );
        }

        final ScreenId screenId;
        final OperationalState state;

        try {
            screenId = ScreenId.valueOf(screenIdRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Invalid screenId",
                                    "message", "Valid values are: SAL1, SAL2",
                                    "provided", screenIdRaw
                            ))
                            .build()
            );
        }

        try {
            state = OperationalState.valueOf(request.state().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Invalid state",
                                    "message", "Unknown OperationalState",
                                    "provided", request.state()
                            ))
                            .build()
            );
        }

        supervisor.refreshFromSnapshots();

        ScreenRuntimeStatus current = supervisor.get(screenId);
        if (current == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of(
                                    "error", "Screen not initialized",
                                    "message", "No runtime status exists yet for " + screenId.name()
                            ))
                            .build()
            );
        }

        ScreenRuntimeStatus updated = new ScreenRuntimeStatus(
                screenId,
                current.snapshot(),
                state,
                current.lastActions(),
                current.timingPlan(),
                Instant.now()
        );

        supervisor.putRuntime(updated);

        return Map.of(
                "ok", true,
                "screenId", screenId.name(),
                "supervisedState", state.name(),
                "runtime", updated
        );
    }
}
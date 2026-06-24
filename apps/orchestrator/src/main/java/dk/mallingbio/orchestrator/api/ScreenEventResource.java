package dk.mallingbio.orchestrator.api;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenEvent;
import dk.mallingbio.orchestrator.ScreenSupervisorService;
import dk.mallingbio.orchestrator.ScreenView;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/screens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScreenEventResource {

    private final ScreenSupervisorService supervisor;

    public ScreenEventResource(ScreenSupervisorService supervisor) {
        this.supervisor = supervisor;
    }

    @POST
    @Path("/{screenId}/events")
    public Map<String, Object> postEvent(@PathParam("screenId") String screenIdRaw,
                                         EventRequest request) {

        if (request == null || request.event() == null || request.event().isBlank()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Missing event",
                                    "message", "Request body must contain a non-empty 'event' field"
                            ))
                            .build()
            );
        }

        final ScreenId screenId;
        final ScreenEvent event;

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
            event = ScreenEvent.valueOf(request.event().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Invalid event",
                                    "message", "Unknown ScreenEvent",
                                    "provided", request.event()
                            ))
                            .build()
            );
        }

        // Sørg for at supervisoren har et aktuelt runtime snapshot at arbejde på
        supervisor.refreshFromSnapshots();

        ScreenView before = supervisor.getView(screenId);
        if (before == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of(
                                    "error", "Screen not initialized",
                                    "message", "No runtime/view exists yet for " + screenId.name()
                            ))
                            .build()
            );
        }

        supervisor.handleEvent(screenId, event);

        ScreenView after = supervisor.getView(screenId);

        return Map.of(
                "ok", true,
                "screenId", screenId.name(),
                "event", event.name(),
                "before", before,
                "after", after
        );
    }
}
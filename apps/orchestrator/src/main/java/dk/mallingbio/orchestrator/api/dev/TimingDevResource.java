package dk.mallingbio.orchestrator.api.dev;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.state.ScreenTimingPlan;
import dk.mallingbio.orchestrator.ScreenTimingService;
import dk.mallingbio.orchestrator.api.TimingPlanRequest;
import dk.mallingbio.orchestrator.dev.DevScreenSimulationService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;

@Path("/api/dev/timing")
@IfBuildProfile("dev")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TimingDevResource {

    private final ScreenTimingService timingService;
    private final DevScreenSimulationService simulationService;

    public TimingDevResource(ScreenTimingService timingService, DevScreenSimulationService simulationService) {
        this.timingService = timingService;
        this.simulationService = simulationService;
    }

    @POST
    @Path("/{screenId}/plan")
    public Map<String, Object> setPlan(@PathParam("screenId") String screenIdRaw, TimingPlanRequest request) {
        final ScreenId screenId;
        try {
            screenId = ScreenId.valueOf(screenIdRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Invalid screenId", "message", "Valid values are SAL1, SAL2", "provided", screenIdRaw)).build());
        }

        if (request == null || request.introStartAt() == null || request.publicStartAt() == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Missing timing fields", "message", "Body must contain introStartAt and publicStartAt as ISO-8601 instants")).build());
        }

        final Instant introStartAt;
        final Instant publicStartAt;

        try {
            introStartAt = Instant.parse(request.introStartAt());
            publicStartAt = Instant.parse(request.publicStartAt());
        } catch (Exception ex) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Invalid timestamp format", "message", "Use ISO-8601 format, e.g. 2026-06-14T21:45:00Z")).build());
        }

        boolean autoStart = request.autoStartEnabled() == null || request.autoStartEnabled();

        ScreenTimingPlan plan = new ScreenTimingPlan(screenId, introStartAt, publicStartAt, autoStart);

        timingService.setPlan(plan);
        simulationService.start(plan);

        return Map.of("ok", true, "screenId", screenId.name(), "plan", plan);
    }
}

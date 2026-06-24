package dk.mallingbio.orchestrator.api;

import dk.mallingbio.orchestrator.ScreenSupervisorService;
import dk.mallingbio.orchestrator.ScreenView;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/screens")
@Produces(MediaType.APPLICATION_JSON)
public class ScreensResource {

    private final ScreenSupervisorService supervisor;

    public ScreensResource(ScreenSupervisorService supervisor) {
        this.supervisor = supervisor;
    }

    @GET
    public Map<String, ScreenView> get() {
        supervisor.refreshFromSnapshots();

        return supervisor.getViews().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue
                ));
    }
}
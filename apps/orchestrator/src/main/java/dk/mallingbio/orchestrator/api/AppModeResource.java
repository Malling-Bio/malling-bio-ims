package dk.mallingbio.orchestrator.api;

import dk.mallingbio.domain.AppMode;
import dk.mallingbio.orchestrator.AppModeService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/app-mode")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppModeResource {

    private final AppModeService appMode;

    public AppModeResource(AppModeService appMode) {
        this.appMode = appMode;
    }

    @GET
    public Map<String, Object> get() {
        return Map.of("mode", appMode.get().name());
    }

    @PUT
    public Map<String, Object> set(Map<String, String> body) {
        String m = body.getOrDefault("mode", "AUTO");
        AppMode newMode = AppMode.valueOf(m);
        appMode.set(newMode);
        return Map.of("mode", newMode.name());
    }
}

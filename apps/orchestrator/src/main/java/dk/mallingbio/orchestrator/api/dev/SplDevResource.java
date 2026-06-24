package dk.mallingbio.orchestrator.api.dev;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.orchestrator.ScreenSplResult;
import dk.mallingbio.orchestrator.SplService;
import dk.mallingbio.spl.SplCue;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/api/dev/screens")
@IfBuildProfile("dev")
@Produces(MediaType.APPLICATION_JSON)
public class SplDevResource {

    private final SplService splService;

    public SplDevResource(SplService splService) {
        this.splService = splService;
    }

    @GET
    @Path("/{screenId}/spl/cues")
    public ScreenSplResult getCues(@PathParam("screenId") String screenIdRaw) {
        ScreenId screenId = parseScreenId(screenIdRaw);
        return splService.getRuntimeCues(screenId);
    }

    @GET
    @Path("/{screenId}/spl/cues/search")
    public Map<String, Object> findCue(@PathParam("screenId") String screenIdRaw,
                                       @QueryParam("text") String text) {
        ScreenId screenId = parseScreenId(screenIdRaw);

        if (text == null || text.isBlank()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Missing query parameter",
                                    "message", "Use ?text=..."
                            ))
                            .build()
            );
        }

        Optional<SplCue> match = splService.findFirstCueByNameContains(screenId, text);

        return Map.of(
                "screenId", screenId.name(),
                "searchText", text,
                "found", match.isPresent(),
                "cue", match.orElse(null)
        );
    }

    @GET
    @Path("/spl/cues")
    public Map<String, ScreenSplResult> getAllCues() {
        return splService.getRuntimeCuesForAllScreens().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue
                ));
    }

    private ScreenId parseScreenId(String screenIdRaw) {
        try {
            return ScreenId.valueOf(screenIdRaw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error", "Invalid screenId",
                                    "message", "Valid values are SAL1, SAL2",
                                    "provided", screenIdRaw
                            ))
                            .build()
            );
        }
    }
}

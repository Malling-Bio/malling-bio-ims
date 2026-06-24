package dk.mallingbio.orchestrator.api.dev;

import dk.mallingbio.domain.ConnectivityState;
import dk.mallingbio.domain.OperationalState;
import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.orchestrator.ImsRegistry;
import dk.mallingbio.stub.StubImsClient;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;
import java.util.Map;

/**
 * Development-only helper endpoints to simulate both halls without cinema access.
 */
@Path("/api/dev/stub")
@IfBuildProfile("dev")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StubControlResource {

    private final ImsRegistry registry;

    public StubControlResource(ImsRegistry registry) {
        this.registry = registry;
    }

    @POST
    @Path("/{screen}/operational")
    public Map<String, Object> setOperational(@PathParam("screen") String screen,
                                              Map<String, String> body) {
        ScreenId id = ScreenId.valueOf(screen.toUpperCase());
        var client = (StubImsClient) registry.client(id);
        client.setOperational(OperationalState.valueOf(body.get("state")));
        return Map.of("ok", true, "screen", id.name(), "state", client.snapshot().operational().name());
    }

    @POST
    @Path("/{screen}/connectivity")
    public Map<String, Object> setConnectivity(@PathParam("screen") String screen,
                                               Map<String, String> body) {
        ScreenId id = ScreenId.valueOf(screen.toUpperCase());
        var client = (StubImsClient) registry.client(id);
        client.setConnectivity(ConnectivityState.valueOf(body.get("state")));
        return Map.of("ok", true, "screen", id.name(), "state", client.snapshot().connectivity().name());
    }

    @POST
    @Path("/{screen}/advance")
    public Map<String, Object> advance(@PathParam("screen") String screen,
                                       @QueryParam("seconds") @DefaultValue("60") long seconds) {
        ScreenId id = ScreenId.valueOf(screen.toUpperCase());
        var client = (StubImsClient) registry.client(id);
        client.advance(Duration.ofSeconds(seconds));
        return Map.of("ok", true, "screen", id.name(), "state", client.snapshot().operational().name());
    }
}

package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.ims.ImsClient;
import dk.mallingbio.stub.StubImsClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry for per-screen IMS clients.
 *
 * For now (skeleton), this registry wires stub clients for both screens.
 * Next step: make this configurable (stub | soap) per screen.
 */
@ApplicationScoped
public class ImsRegistry {

    private final Map<ScreenId, ImsClient> clients = new EnumMap<>(ScreenId.class);

    @PostConstruct
    void init() {
        clients.put(ScreenId.SAL1, new StubImsClient(ScreenId.SAL1));
        clients.put(ScreenId.SAL2, new StubImsClient(ScreenId.SAL2));
    }

    public ImsClient client(ScreenId id) {
        return clients.get(id);
    }

    public Map<ScreenId, ImsClient> all() {
        return Map.copyOf(clients);
    }
}

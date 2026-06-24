package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.ims.ImsClient;
import dk.mallingbio.spl.ParsedSpl;
import dk.mallingbio.spl.SplCue;
import dk.mallingbio.spl.SplParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class SplService {

    private static final Logger LOG = Logger.getLogger(SplService.class);

    private final ImsRegistry registry;
    private final SplParser splParser = new SplParser();

    @Inject
    public SplService(ImsRegistry registry) {
        this.registry = registry;
    }

    public ScreenSplResult getRuntimeCues(ScreenId screenId) {
        ImsClient client = registry.client(screenId);
        if (client == null) {
            return new ScreenSplResult(
                    screenId,
                    false,
                    0,
                    List.of(),
                    "No ImsClient registered for " + screenId
            );
        }

        try {
            String base64 = client.getSplRuntimeBase64();

            if (base64 == null || base64.isBlank()) {
                return new ScreenSplResult(
                        screenId,
                        false,
                        0,
                        List.of(),
                        "SPL runtime not available"
                );
            }

            ParsedSpl parsed = splParser.parseBase64(base64);

            return new ScreenSplResult(
                    screenId,
                    true,
                    parsed.cues().size(),
                    parsed.cues(),
                    null
            );
        } catch (Exception ex) {
            LOG.errorf(ex, "Failed to parse SPL runtime for %s", screenId);
            return new ScreenSplResult(
                    screenId,
                    false,
                    0,
                    List.of(),
                    ex.getMessage()
            );
        }
    }

    public Optional<SplCue> findFirstCueByNameContains(ScreenId screenId, String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        ScreenSplResult result = getRuntimeCues(screenId);

        if (!result.available()) {
            return Optional.empty();
        }

        return result.cues().stream()
                .filter(cue -> cue.cueName() != null)
                .filter(cue -> cue.cueName().toLowerCase().contains(text.toLowerCase()))
                .findFirst();
    }

    public Map<ScreenId, ScreenSplResult> getRuntimeCuesForAllScreens() {
        return registry.all().keySet().stream()
                .collect(Collectors.toMap(
                        screenId -> screenId,
                        this::getRuntimeCues
                ));
    }
}
package dk.mallingbio.orchestrator;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.spl.SplCue;

import java.util.List;

public record ScreenSplResult(
        ScreenId screenId,
        boolean available,
        int cueCount,
        List<SplCue> cues,
        String error
) {
}
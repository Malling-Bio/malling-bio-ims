package dk.mallingbio.spl;

import java.util.List;
import java.util.Optional;

public record ParsedSpl(
        String xml,
        List<SplCue> cues
) {
    public Optional<SplCue> findFirstCueByNameContains(String text) {
        return cues.stream()
                .filter(c -> c.nameContainsIgnoreCase(text))
                .findFirst();
    }
}
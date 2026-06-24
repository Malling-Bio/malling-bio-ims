package dk.mallingbio.spl;

public record SplCue(
        String nodeName,
        String cueName,
        String rawOffset,
        Long offsetSeconds
) {
    public boolean nameContainsIgnoreCase(String value) {
        if (cueName == null || value == null) {
            return false;
        }
        return cueName.toLowerCase().contains(value.toLowerCase());
    }
}
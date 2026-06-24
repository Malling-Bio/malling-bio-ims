package dk.mallingbio.orchestrator.api;

public record TimingPlanRequest(
        String introStartAt,
        String publicStartAt,
        Boolean autoStartEnabled
) {
}
package dk.mallingbio.ims;

import dk.mallingbio.domain.ScreenId;
import dk.mallingbio.domain.ScreenSnapshot;

/**
 * Minimal abstraction for IMS interactions.
 *
 * In AUTO mode, the orchestrator will call mutation commands (stopScheduler/play/eject/startScheduler).
 * In MANUAL or MAINTENANCE, orchestrator will only call read methods.
 */
public interface ImsClient {

    ScreenId screenId();

    /** Ensures we have a valid session. Should do login/relogin internally. */
    void ensureSession();

    /** Read-only snapshot (safe during shows). */
    ScreenSnapshot snapshot();

    // --- Mutating commands (only used when appMode == AUTO) ---
    void stopScheduler();

    void startScheduler();

    void play();

    void eject();

    // --- SPL access (for cue parsing) ---
    /** Base64 encoded SPL runtime XML (as returned by SPLManagement.GetSPLRuntime). */
    String getSplRuntimeBase64();
}

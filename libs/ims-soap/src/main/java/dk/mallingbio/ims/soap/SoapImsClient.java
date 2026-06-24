package dk.mallingbio.ims.soap;

import dk.mallingbio.domain.*;
import dk.mallingbio.ims.*;

import java.time.Instant;

/**
 * Placeholder for real SOAP implementation.
 *
 * TODO: Implement with HTTP SOAP calls towards IMS3000.
 */
public class SoapImsClient implements ImsClient {

    private final ScreenId screenId;

    public SoapImsClient(ScreenId screenId) {
        this.screenId = screenId;
    }

    @Override
    public ScreenId screenId() {
        return screenId;
    }

    @Override
    public void ensureSession() {
        throw new UnsupportedOperationException("SOAP client not implemented yet");
    }

    @Override
    public ScreenSnapshot snapshot() {
        // Return a clearly-marked placeholder snapshot.
        return new ScreenSnapshot(
                screenId,
                ConnectivityState.CONNECTING,
                OperationalState.IDLE,
                false,
                null,
                null,
                "UNKNOWN",
                "SOAP client not implemented",
                Instant.now()
        );
    }

    @Override
    public void stopScheduler() {
        throw new UnsupportedOperationException("SOAP client not implemented yet");
    }

    @Override
    public void startScheduler() {
        throw new UnsupportedOperationException("SOAP client not implemented yet");
    }

    @Override
    public void play() {
        throw new UnsupportedOperationException("SOAP client not implemented yet");
    }

    @Override
    public void eject() {
        throw new UnsupportedOperationException("SOAP client not implemented yet");
    }

    @Override
    public String getSplRuntimeBase64() {
        throw new UnsupportedOperationException("SOAP client not implemented yet");
    }
}

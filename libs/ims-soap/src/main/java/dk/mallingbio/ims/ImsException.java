package dk.mallingbio.ims;

public class ImsException extends RuntimeException {
    public ImsException(String message) { super(message); }
    public ImsException(String message, Throwable cause) { super(message, cause); }
}

package com.amadeus.jenkins.plugins.unbreakablebuild;

/**
 * <p>
 * Technical Exceptions thrown by this modules
 */
public class UbTechnicalException extends RuntimeException {

    private static final long serialVersionUID = 805163558692065098L;

    /**
     * Constructs a new exception with the specified detail message and
     * cause.  <p>Note that the detail message associated with
     * {@code cause} is <i>not</i> automatically incorporated in
     * this exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method).  (A <tt>null</tt> value is
     *                permitted, and indicates that the cause is nonexistent or
     *                unknown.)
     * @since 1.4
     */
    public UbTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }

}

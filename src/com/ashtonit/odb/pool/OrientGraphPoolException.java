package com.ashtonit.odb.pool;

/**
 * Thrown when the pool cannot return an OrientGraph instance.
 * 
 * @author Bruce Ashton
 * @date 2015-05-30
 */
public class OrientGraphPoolException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    /**
     * A no constructor that takes no arguments.
     */
    public OrientGraphPoolException() {
        super();
    }


    /**
     * A constructor that takes a message as an argument.
     * 
     * @param message the exception message
     */
    public OrientGraphPoolException(final String message) {
        super(message);
    }


    /**
     * A constructor that takes a message and throwable as arguments.
     * 
     * @param message the exception message
     * @param throwable the throwable that caused this exception to be thrown
     */
    public OrientGraphPoolException(final String message, final Throwable throwable) {
        super(message, throwable);
    }


    /**
     * A constructor that takes a throwable as an argument.
     * 
     * @param throwable the throwable that caused this exception to be thrown
     */
    public OrientGraphPoolException(final Throwable throwable) {
        super(throwable);
    }
}

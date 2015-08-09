package com.ashtonit.odb.pool;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;


/**
 * An OrientGraphPool instance maintains a pool of OrientGraph instances. A call
 * to get() with url, username and password returns one to the caller. The
 * caller releases the instance back to the pool by calling shutdown() on the
 * OrientGraph instance with no arguments.
 * <p>
 * Require Java 1.7 or above.
 * </p>
 *
 * @author Bruce Ashton
 * @date 2015-05-23
 */
public interface OrientGraphPool {

    /**
     * Return an OrientGraph instance initialised with the given URI, username
     * and password.
     *
     * @param url an OrientDB database URI
     * @param username an OrientDB database username
     * @param password the password to match the username
     * @return an OrientGraph instance
     * @throws OrientGraphPoolException if no OrientGraph instance is available
     */
    OrientGraph get(final String url, final String username, final String password) throws OrientGraphPoolException;


    /**
     * Remove all OrientGraph instance from the pool that match the given URI
     * and username. Call this method when a user logs out of an application.
     *
     * @param uri an OrientDB database URI
     * @param username an OrientDB database username
     */
    void removeAll(final String uri, final String username);


    /**
     * Shut down the database instance. Actually just releases this instance
     * back into the pool.
     */
    void shutdown();
}

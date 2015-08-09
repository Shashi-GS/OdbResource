package com.ashtonit.odb.pool;

import java.util.concurrent.atomic.AtomicReference;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;


/**
 * Extends orientGraph and crucially overrides the shutdown() methods so that
 * OrientGraph instances can be recycled into a pool.
 *
 * @author Bruce Ashton
 * @date 2015-05-23
 */
class PooledOrientGraph extends OrientGraph {

    Key key;

    private final OrientGraphPoolImpl pool;
    private final AtomicReference<Thread> shutdownHandler = new AtomicReference<Thread>();


    /**
     * The constructor for a PooledOrientGraph, takes the same arguments as an
     * OrientGraph instance and also a reference to the pool implementation.
     * 
     * @param uri
     * @param username
     * @param password
     * @param pool
     */
    PooledOrientGraph(final String uri, final String username, final String password, final OrientGraphPoolImpl pool) {
        super(uri, username, password);
        this.pool = pool;
    }


    /**
     * <p>
     * Frees up this instance for use by other threads. Calls commit or rollback
     * depending on configuration. Overrides OrienGraph.shutdown() and dos not
     * call it, so the graph is never actually shut down. This is necessary for
     * instance to be re-useable.
     * </p>
     * <p>
     * The unfortunate side-effect of this is that all data read and write
     * operations can still be called on a graph instance after it has been
     * "shut down" by this method. It relies on developers honoring the implied
     * semantics of the shutdown() method and not changing data through it
     * again.
     * </p>
     * <p>
     * Resolving this issue would require overriding every public method of
     * OrientGraph, but this would almost certainly be a maintenance nightmare,
     * and very hard to prove safe.
     * </p>
     * 
     * @see OrientGraph#shutdown()
     */
    @Override
    public void shutdown() {
        try {
            final Thread thread = shutdownHandler.getAndSet(null);
            if (thread != null) {
                thread.interrupt();
            }
            if (pool.isCommitOnShutdown()) {
                commit();
            } else {
                rollback();
            }
        } finally {
            pool.passivate(this);
        }
    }


    /**
     * <p>
     * All overloaded shutdown() methods apart from the no-argument one remove
     * the instance from the pool completely and call the overridden method on
     * OrientGraph.
     * </p>
     * <p>
     * The commitOnShutdown configuration parameter has no effect when this
     * method is called.
     * </p>
     * 
     * @param closeDb as for OrientGraph
     * @see OrientGraph#shutdown(boolean)
     */
    @Override
    public void shutdown(final boolean closeDb) {
        try {
            pool.remove(this);
            final Thread thread = shutdownHandler.getAndSet(null);
            if (thread != null) {
                thread.interrupt();
            }
        } finally {
            super.shutdown(closeDb);
        }
    }


    /**
     * <p>
     * All overloaded shutdown() methods apart from the no-argument one remove
     * the instance from the pool completely and call the overridden method on
     * OrientGraph.
     * </p>
     * <p>
     * The commitOnShutdown configuration parameter has no effect when this
     * method is called.
     * </p>
     *
     * @param closeDb as for OrientGraph
     * @param commitTx as for OrientGraph
     * @see OrientGraph#shutdown(boolean, boolean)
     */
    @Override
    public void shutdown(final boolean closeDb, final boolean commitTx) {
        try {
            pool.remove(this);
            final Thread thread = shutdownHandler.getAndSet(null);
            if (thread != null) {
                thread.interrupt();
            }
        } finally {
            super.shutdown(closeDb, commitTx);
        }
    }


    /**
     * <p>
     * Called by the pool instance when activating a PooledOreintGraph instance
     * and associating it with a thread.
     * </p>
     * <p>
     * Also adds a shutdown handler that joins the active thread and shuts down
     * this instance if the thread exist without calling shutdown() explicitly.
     * This is to guard against resource leaks.
     * </p>
     * 
     * @param key the active key
     * @param thread the calling thread
     */
    void activate(final Key key, final Thread thread) {
        this.key = key;
        final Thread handler = new Thread("Shutdown handler for " + thread.getName()) {

            @Override
            public void run() {
                try {
                    thread.join();
                    shutdownHandler.set(null);
                    shutdown();
                } catch (final InterruptedException e) {
                    // Do nothing. Probably shutdown by an external call.
                }
            }
        };
        handler.start();
        final Thread oldHandler = shutdownHandler.getAndSet(handler);
        if (oldHandler != null) {
            oldHandler.interrupt();
        }
    }
}

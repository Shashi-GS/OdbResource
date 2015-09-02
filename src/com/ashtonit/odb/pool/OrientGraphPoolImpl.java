package com.ashtonit.odb.pool;

import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.orientechnologies.orient.server.OServer;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;


/**
 * The implementation of the OrientGraphPool interface.
 *
 * @author Bruce Ashton
 * @date 2015-05-23
 */
public class OrientGraphPoolImpl implements OrientGraphPool {

    private static final Logger log = Logger.getLogger(OrientGraphPoolImpl.class.getName());

    private final Map<Key, PooledOrientGraph> active = new ConcurrentHashMap<Key, PooledOrientGraph>();
    private final Map<Key, Queue<PooledOrientGraph>> dormant = new ConcurrentHashMap<Key, Queue<PooledOrientGraph>>();

    /**
     * Call commit() just prior to shutdown() being called when this is true. If
     * it is false rollback() is called instead.
     */
    private final boolean commitOnShutdown;

    /**
     * How long in milliseconds a thread will sleep in get() between attempts to
     * free up OrientGraph instances to return.
     */
    private final long getWait;

    /**
     * The maximum number of OrientGraph instance that can belong to a single
     * database user.
     */
    private final int perUserLimit;

    /**
     * The number of dormant OrientGraph instances belonging to users that will
     * be shutdown by the recycle() method when a call to get() hits the total
     * or per user limit.
     */
    private final int recycleCount;

    /**
     * If a call to get() cannot return an OrientGraph instance within timeOut
     * milliseconds, an OrientGraphException is thrown.
     */
    private final long timeOut;

    /**
     * The maximum number of OrientGraph instances the pool will hold.
     */
    private final int totalLimit;

    private final OServer server;

    private volatile int totalGraphCount = 0;


    /**
     * @param commitOnShutdown
     * @param getWait
     * @param perUserLimit
     * @param recycleCount
     * @param server
     * @param timeOut
     * @param totalLimit
     */
    OrientGraphPoolImpl(final boolean commitOnShutdown, final long getWait, final int perUserLimit, final int recycleCount, final OServer server, final long timeOut, final int totalLimit) {
        this.commitOnShutdown = commitOnShutdown;
        this.getWait = getWait;
        this.perUserLimit = perUserLimit;
        this.recycleCount = recycleCount;
        this.server = server;
        this.timeOut = timeOut;
        this.totalLimit = totalLimit;
    }


    /**
     * Returns an orientGraph instance to match the uri, username and password.
     * If none is available it will block for a number of milliseconds defined
     * by timeOut or until an instance can be made available. If this method
     * times out it throws an OrientGraphPoolException.
     *
     * @param uri the URI of the OrientDB database
     * @param username an OrientDB database username
     * @param password the password matching the username
     * @return an OrientGraph instance
     * @throws OrientGraphPoolException if it cannot return an OrientGraph
     *         instance within the time out period
     * @see OrientGraphPool#get(String, String, String)
     */
    @Override
    public OrientGraph get(final String uri, final String username, final String password) throws OrientGraphPoolException {
        if (uri == null || username == null || password == null) {
            throw new NullPointerException();
        }
        final Thread thread = Thread.currentThread();
        final Key aKey = Key.get(uri, username, password, thread);
        log.fine("get(String, String, String): + key=" + aKey);
        final Key dKey = Key.get(uri, username, null, null);
        final long timeOut = System.currentTimeMillis() + this.timeOut;

        OrientGraph graph = get(aKey, dKey, password, thread);
        while (graph == null && System.currentTimeMillis() < timeOut) {
            if (dKey.perUserCount < perUserLimit) {
                recycle();
            } else {
                try {
                    Thread.sleep(getWait);
                } catch (final InterruptedException e) {
                    log.throwing(OrientGraphPoolImpl.class.getName(), "get(String, String, String)", e);
                    Thread.currentThread().interrupt();
                    throw new OrientGraphPoolException(e);
                }
            }
            graph = get(aKey, dKey, password, thread);
        }
        if (graph == null) {
            throw new OrientGraphPoolException("aKey=" + aKey + "; timeOut=" + this.timeOut + "; totalGraphCount="
                    + totalGraphCount + "; totalLimit=" + totalLimit + "; perUserCount=" + dKey.perUserCount
                    + "; perUserLimit=" + perUserLimit);
        }
        return graph;
    }


    /**
     * Remove all OrientGraph instances matching the URI and username.
     * 
     * @param uri the URI of the OrientDB database
     * @param username an OrientDB database username
     * @see OrientGraphPool#removeAll(String, String)
     */
    @Override
    public void removeAll(final String uri, final String username) {
        final Key dKey = Key.get(uri, username, null, null);
        log.fine("removeAll(String, String): + key=" + dKey);
        final Set<Key> keys = Key.getKeysAllThreads(dKey);
        final Set<OrientGraph> graphs = new HashSet<OrientGraph>();
        for (final Key key : keys) {
            synchronized (dKey) {
                if (key.threadName != null) {
                    final OrientGraph graph = active.get(key);
                    if (graph != null) {
                        graphs.add(graph);
                    }
                } else {
                    final Queue<PooledOrientGraph> queue = dormant.get(key);
                    if (queue != null) {
                        OrientGraph graph = queue.poll();
                        while (graph != null) {
                            graphs.add(graph);
                            graph = queue.poll();
                        }
                    }
                }
            }
        }
        for (final OrientGraph graph : graphs) {
            try {
                graph.shutdown(true, commitOnShutdown);
            } catch (final Exception e) {
                log.throwing(OrientGraphPoolImpl.class.getName(), "removeAll(String, String)", e);
            }
        }
    }


    /**
     * Calls OServer.shutdown(). This method is here so that the shutdown can be
     * configured via the global resource configuration in server.xml.
     * 
     * @see OrientGraphPool#shutdown()
     */
    @Override
    public void shutdown() {
        server.shutdown();
    }


    /**
     * Returns true if the OrientGraph instance should automatically call commit
     * before shutdown.
     * 
     * @return true if the OrientGraph instance should automatically call commit
     *         before shutdown
     */
    boolean isCommitOnShutdown() {
        return commitOnShutdown;
    }


    /**
     * Called by PooledOrientGraph().shutdown(), this method moves an
     * OrientGraph instance from the active to the dormant collection, ready to
     * be returned by the next appropriate call to get().
     * 
     * @param graph the graph to passivate
     */
    void passivate(final PooledOrientGraph graph) {
        final Key aKey = graph.key;
        log.fine("passivate(PooledOrientGraph): + key=" + aKey);
        if (aKey != null) {
            final Key dKey = Key.get(aKey.uri, aKey.username, null, null);
            synchronized (dKey) {
                final PooledOrientGraph aGraph = active.remove(aKey);
                if (aGraph == graph) {
                    Key.remove(aKey);
                    Queue<PooledOrientGraph> queue = dormant.get(dKey);
                    if (queue == null) {
                        queue = new ArrayBlockingQueue<PooledOrientGraph>(perUserLimit);
                        dormant.put(dKey, queue);
                    }
                    graph.key = dKey;
                    queue.offer(graph);
                } else {
                    if (aGraph != null) {
                        active.put(aKey, aGraph);
                    }
                    log.warning("Graph not found in active collection: " + graph);
                }
            }
        }
    }


    /**
     * Removes a graph from all collections when it is shut down.
     * 
     * @param graph the graph to remove
     */
    void remove(final PooledOrientGraph graph) {
        final Key aKey = graph.key;
        log.fine("remove(PooledOrientGraph): + key=" + aKey);
        if (aKey != null) {
            final Key dKey = Key.get(aKey.uri, aKey.username, null, null);
            synchronized (dKey) {
                final PooledOrientGraph aGraph = active.remove(aKey);
                if (aGraph != graph) {
                    if (aGraph != null) {
                        active.put(aKey, aGraph);
                    }
                    final Queue<PooledOrientGraph> queue = dormant.get(dKey);
                    if (queue != null) {
                        if (queue.remove(graph)) {
                            dKey.perUserCount--;
                            totalGraphCount--;
                        } else {
                            log.warning("Graph not found in active or dormant collection: " + graph);
                        }
                    } else {
                        log.warning("Graph not found in active collection and no dormant collection found: " + graph);
                    }
                } else {
                    Key.remove(aKey);
                    dKey.perUserCount--;
                    totalGraphCount--;
                }
            }
        }
    }


    private OrientGraph get(final Key aKey, final Key dKey, final String password, final Thread thread) {
        synchronized (dKey) {
            PooledOrientGraph graph = active.get(aKey);
            if (graph == null) {
                final Queue<PooledOrientGraph> queue = dormant.get(dKey);
                if (queue != null) {
                    graph = queue.poll();
                }
                if (graph == null) {
                    if (dKey.perUserCount >= perUserLimit || totalGraphCount >= totalLimit) {
                        return null;
                    }
                    totalGraphCount++;
                    dKey.perUserCount++;
                    graph = new PooledOrientGraph(aKey.uri, aKey.username, password, this);
                }
                graph.activate(aKey, thread);
                active.put(aKey, graph);
            }
            return graph;
        }
    }


    private void recycle() {
        int count = 0;
        for (final Key dKey : Key.getAllDormantKeys()) {
            if (count >= recycleCount) {
                break;
            }
            synchronized (dKey) {
                final Queue<PooledOrientGraph> queue = dormant.get(dKey);
                if (queue != null) {
                    final PooledOrientGraph graph = queue.peek();
                    if (graph != null) {
                        try {
                            graph.shutdown(true, commitOnShutdown);
                        } catch (final Exception e) {
                            log.throwing(OrientGraphPoolImpl.class.getName(), "recycle()", e);
                        }
                        // Up the count regardless of exceptions, the graph is
                        // still probably gone from the pool.
                        count++;
                    }
                }
            }
        }
    }
}

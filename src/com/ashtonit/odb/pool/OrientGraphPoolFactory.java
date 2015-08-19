package com.ashtonit.odb.pool;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;


/**
 * <p>
 * An implementation of ObjectFactory that returns an OrientGraphPool instance
 * in response to a JNDI lookup. It takes several configuration parameters which
 * can be passed in with the JNDI reference.
 * </p>
 * <p>
 * Typically these parameters are given as attributes in a Resource definition
 * in the context.xml file of a web application or the server.xml file of a
 * Tomcat web application server or similar.
 * </p>
 * <table summary="Parameters">
 * <thead>
 * <tr>
 * <th>Parameter</th>
 * <th>Type</th>
 * <th>Default</th>
 * <th>Description</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <td>commitOnShutdown</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>If set to true, commit() is always called just before a PooledOrientGraph
 * is passivated and returned to the pool. If it is false, rollback() is called
 * instead.</td>
 * </tr>
 * <tr>
 * <td>getWait</td>
 * <td>long</td>
 * <td>1000</td>
 * <td>If the pool is exhausted (or the per user limit reached) The
 * OrientGraphPool.get() method loops for a time defined by the timeOut
 * configuration parameter trying to recycle paassivated OrientGraph instances.
 * getWait defines the period for which the current thread sleeps between
 * iterations. Most people should not need to change this.</td>
 * </tr>
 * <tr>
 * <td>perUserLimit</td>
 * <td>int</td>
 * <td>10</td>
 * <td>The maximum number of OrientGraph instances that any one user can have
 * open at one time.</td>
 * </tr>
 * <tr>
 * <td>recycleCount</td>
 * <td>int</td>
 * <td>3</td>
 * <td>When the pool is exhausted The get() method will try to recycle dormant
 * OrientGraph instances by removing them from the pool and shutting them down
 * fully. This value defines how many instances the method will try to recycle
 * at one time. Most people should not need to change this.</td>
 * </tr>
 * <tr>
 * <td>timeOut</td>
 * <td>long</td>
 * <td>10000</td>
 * <td>The amount of time in milliseconds that the get() method will block
 * trying to return an OrientGraph instance before throwing an
 * OrientGraphPoolException.</td>
 * </tr>
 * <tr>
 * <td>totalLimit</td>
 * <td>int</td>
 * <td>1000</td>
 * <td>The total number of OrientGraph instances (active and dormant) that this
 * pool can hold.</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * In a Tomcat web application, the name of this class is the value of the
 * factory attribute of a &lt;Resource&gt; tag. The parameters given in the
 * table above are also specified as attributes of the resource tag. The type
 * attribute is com.ashtonit.odb.pool.OrientGraphPool. The resource should
 * always be a singleton and a closeMethod attribute with the value "shutdown"
 * should also be present. The auth attribute should have the value "Container".
 * The server config file for OrientDB should be passed in with the attribute
 * "configFile". <br>
 * e.g.
 * </p>
 * 
 * <pre>
 * &lt;Resource
 *   auth="Container"
 *   closeMethod="shutdown"
 *   configFile="/mnt/share/orientdb-community-2.1-rc5/config/orientdb-server-config.xml"
 *   factory="com.ashtonit.odb.pool.OrientGraphPoolFactory"
 *   name="odbp"
 *   singleton="true"
 *   type="com.ashtonit.odb.pool.OrientGraphPool"
 *   commitOnShutdown="true"
 *   getWait="1000"
 *   perUserLimit="10"
 *   recycleCount="3"
 *   timeOut="10000"
 *   totalLimit="1000"
 * /&gt;
 * </pre>
 * 
 * @author Bruce Ashton
 * @date 2015-06-17
 */
public class OrientGraphPoolFactory implements ObjectFactory {

    private static final String COMMIT_ON_SHUTDOWN = "commitOnShutdown";
    private static final String CONFIG_FILE_NAME = "configFile";
    private static final String GET_WAIT = "getWait";
    private static final String PER_USER_LIMIT = "perUserLimit";
    private static final String RECYCLE_COUNT = "recycleCount";
    private static final String TIME_OUT = "timeOut";
    private static final String TOTAL_LIMIT = "totalLimit";

    private static final Object LOCK = new Object();

    private static OrientGraphPool pool;
    private static OServer server;


    /**
     * Returns an OrientGraphPool instance and initialises the embedded OServer
     * instance with the config file specified as an attribute. This instance is
     * always a singleton, regardless of attributes in server.xml files etc.
     *
     * @param obj the naming reference
     * @param name not used
     * @param nameCtx not used
     * @param environment not used
     * @return the OrientGraphPool instance
     * @throws NamingException if the config file is not a normal, readable file
     * @throws RuntimeException if the server startup throws an Exception
     * @see ObjectFactory#getObjectInstance(Object, Name, Context, Hashtable)
     */
    @Override
    public OrientGraphPool getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?, ?> environment) throws NamingException, RuntimeException {
        synchronized (LOCK) {
            // Don't do file system access if we already have a server.
            if (server == null) {
                // OK, find the config file
                final Reference reference = (Reference) obj;

                String configFile = null;

                for (final Enumeration<RefAddr> e = reference.getAll(); e.hasMoreElements();) {
                    final RefAddr addr = e.nextElement();
                    if (CONFIG_FILE_NAME.equalsIgnoreCase(addr.getType())) {
                        configFile = (String) addr.getContent();
                        break;
                    }
                }

                if (configFile == null) {
                    throw new NamingException(CONFIG_FILE_NAME + " attribute is null");
                }

                final File file = new File(configFile);
                if (!file.exists()) {
                    throw new NamingException(CONFIG_FILE_NAME + " does not exist");
                }
                if (!file.canRead()) {
                    throw new NamingException(CONFIG_FILE_NAME + " cannot be read");
                }
                if (!file.isFile()) {
                    throw new NamingException(CONFIG_FILE_NAME + " is not a normal file");
                }

                // Create and start the server
                try {
                    server = OServerMain.create();
                    server.startup(file);
                    server.activate();
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (pool == null) {
                final Reference reference = (Reference) obj;

                boolean commitOnShutdown = false;
                long getWait = 1000;
                int perUserLimit = 10;
                int recycleCount = 3;
                long timeOut = 10000;
                int totalLimit = 1000;

                for (final Enumeration<RefAddr> e = reference.getAll(); e.hasMoreElements();) {
                    final RefAddr addr = e.nextElement();
                    if (COMMIT_ON_SHUTDOWN.equalsIgnoreCase(addr.getType())) {
                        commitOnShutdown = Boolean.valueOf((String) addr.getContent());
                    } else if (GET_WAIT.equalsIgnoreCase(addr.getType())) {
                        getWait = Long.valueOf((String) addr.getContent());
                    } else if (PER_USER_LIMIT.equalsIgnoreCase(addr.getType())) {
                        perUserLimit = Integer.valueOf((String) addr.getContent());
                    } else if (RECYCLE_COUNT.equalsIgnoreCase(addr.getType())) {
                        recycleCount = Integer.valueOf((String) addr.getContent());
                    } else if (TIME_OUT.equalsIgnoreCase(addr.getType())) {
                        timeOut = Long.valueOf((String) addr.getContent());
                    } else if (TOTAL_LIMIT.equalsIgnoreCase(addr.getType())) {
                        totalLimit = Integer.valueOf((String) addr.getContent());
                    }
                }
                pool = new OrientGraphPoolImpl(commitOnShutdown, getWait, perUserLimit, recycleCount, server, timeOut, totalLimit);
            }
            return pool;
        }
    }
}

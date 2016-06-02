package com.ashtonit.odb.jndi;

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
 * Return a singleton {@link OServer} instance as a JNDI resource. When embedding the database in a container such as
 * Tomcat it allows the server to be shutdown cleanly by calling the {@link OServer#shutdown()} method.
 * </p>
 * <p>
 * This resource is always a singleton regardless of the resource configuration. When using with Tomcat it should still
 * be configured as a singleton however. This is so that the <code>shutdown()</code> method will be called appropriately
 * by the container.
 * </p>
 * <p>
 * If the <code>configFile</code> attribute does not refer to a valid readable OrientDB server configuration file the
 * <code>OServer</code> instance will not be created and started. Either a {@link NamingException} or
 * {@link RuntimeException} will be thrown.
 * </p>
 * <p>
 * An example resource declaration:
 * </p>
 *
 * <pre>
 * &lt;Resource
 *   auth="Container"
 *   closeMethod="shutdown"
 *   configFile="/mnt/share/orientdb-community-2.1.3/config/orientdb-server-config.xml"
 *   factory="com.ashtonit.odb.jndi.OServerObjectFactory"
 *   name="oserver"
 *   singleton="true"
 *   type="com.orientechnologies.orient.server.OServer"
 * /&gt;
 * </pre>
 * 
 * @author Bruce Ashton
 */
public class OServerObjectFactory implements ObjectFactory {

    private static final String CONFIG_FILE_NAME = "configFile";

    private static final Object LOCK = new Object();

    private static OServer server;


    /**
     * Starts and returns an embedded {@link OServer} instance. This instance is always a singleton, regardless of
     * configuration. It is important to declare it as singleton in the resource declaration though, to ensure it is
     * shut down cleanly.
     * 
     * @param obj the naming reference
     * @param name not used
     * @param nameCtx not used
     * @param environment not used
     * @return the {@link OServer} instance
     * @throws NamingException if the configuration file cannot be read
     * @throws RuntimeException if the OServer instance throws an exception during startup
     * @see ObjectFactory#getObjectInstance(Object, Name, Context, Hashtable)
     */
    @Override
    public OServer getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?, ?> environment) throws NamingException {
        if (server == null) {
            synchronized (LOCK) {
                // Don't do file system access if we already have a server.
                if (server == null) {
                    // OK, look for the config file
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
                        throw new NamingException(CONFIG_FILE_NAME + " attribute has not been declared");
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
            }
        }
        return server;
    }
}

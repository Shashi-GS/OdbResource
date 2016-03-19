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
 * Return a singleton {@link OServer} instance as a JNDI resource. When embedding the database in a container such as
 * Tomcat it allows the server to be shutdown cleanly by calling the {@link OServer#shutdown()} method.
 * <p>
 * This resource is always a singleton regardless of the resource configuration. When using with Tomcat it should still
 * be configured as a singleton however. This is so that the shutdown() method will be called appropriately by the
 * container.
 * 
 * @author Bruce Ashton
 * @date 2016-03-05
 */
public class OServerObjectFactory implements ObjectFactory {

    private static final String CONFIG_FILE_NAME = "configFile";

    private static final Object LOCK = new Object();

    private static OServer server;


    @Override
    public OServer getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?, ?> environment) throws Exception {
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

                    if (configFile != null) {
                        // We must be running embedded
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
        }
        return server;
    }
}

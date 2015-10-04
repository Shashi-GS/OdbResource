package com.ashtonit.odb;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;


/**
 * @author Bruce Ashton
 * @date 2015-10-03
 */
public class OPDPFObjectFactory implements ObjectFactory {

    private static final String CAPACITY = "capacity";
    private static final String CONFIG_FILE_NAME = "configFile";

    private static final Object LOCK = new Object();

    private static OPartitionedDatabasePoolFactory factory;
    private static OServer server;


    /**
     * @param obj
     * @param name
     * @param nameCtx
     * @param environment
     * @return
     * @throws NamingException
     * @see ObjectFactory#getObjectInstance(Object, Name, Context, Hashtable)
     */
    @Override
    public OPartitionedDatabasePoolFactory getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?, ?> environment) throws NamingException {
        synchronized (LOCK) {
            if (factory == null) {
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
                final Reference reference = (Reference) obj;

                int capacity = 100;

                for (final Enumeration<RefAddr> e = reference.getAll(); e.hasMoreElements();) {
                    final RefAddr addr = e.nextElement();
                    if (CAPACITY.equalsIgnoreCase(addr.getType())) {
                        capacity = Integer.valueOf((String) addr.getContent());
                        break;
                    }
                }
                factory = new OPartitionedDatabasePoolFactory(capacity);
            }
            return factory;
        }
    }
}

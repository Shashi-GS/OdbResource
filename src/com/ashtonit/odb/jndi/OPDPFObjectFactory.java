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

import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;


/**
 * <p>
 * An implementation of {@link ObjectFactory} that returns an {@link OPartitionedDatabasePoolFactory} instance in
 * response to a JNDI lookup.
 * </p>
 * <p>
 * If the <strong>configFile</strong> attribute is present in configuration the OrientDB server will be started as an
 * embedded instance. If the attribute is not present it will just provide the {@link OPartitionedDatabasePoolFactory}
 * instance.
 * </p>
 * <p>
 * In a Tomcat web application, the name of this class is the value of the factory attribute of a &lt;Resource&gt; tag.
 * The parameters given in the table above are also specified as attributes of the resource tag. The type attribute is
 * com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory. The resource should always be a singleton and a
 * closeMethod attribute with the value "close" should also be present. The auth attribute should have the value
 * "Container". The server config file for OrientDB should be passed in with the attribute "configFile" if you wish to
 * run an embedded OrientDB server. The maximum number of connections can be set with the attribute, "capacity".<br>
 * e.g.
 * </p>
 * 
 * <pre>
 * &lt;Resource
 *   auth="Container"
 *   capacity="100"
 *   closeMethod="close"
 *   configFile="/mnt/share/orientdb-community-2.1.3/config/orientdb-server-config.xml"
 *   factory="com.ashtonit.odb.OPDPFObjectFactory"
 *   name="opdpfactory"
 *   singleton="true"
 *   type="com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory"
 * /&gt;
 * </pre>
 * 
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
     * Returns an OrientGraphPool instance and initialises an embedded OServer instance with the config file specified
     * as an attribute, if it is present. This instance is always a singleton, regardless of attributes in server.xml
     * files etc.
     *
     * @param obj the naming reference
     * @param name not used
     * @param nameCtx not used
     * @param environment not used
     * @return the {@link OPartitionedDatabasePoolFactory} instance
     * @throws NamingException if the config file is not a normal, readable file
     * @throws RuntimeException if the embedded server startup throws an Exception
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

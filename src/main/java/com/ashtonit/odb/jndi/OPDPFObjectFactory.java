package com.ashtonit.odb.jndi;

import java.util.Enumeration;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;


/**
 * <p>
 * An implementation of {@link ObjectFactory} that returns an {@link OPartitionedDatabasePoolFactory} instance in
 * response to a JNDI lookup.
 * </p>
 * <p>
 * If the <code>configFile</code> attribute is present in configuration the OrientDB server will be started as an
 * embedded instance. If the attribute is not present it will just provide the {@link OPartitionedDatabasePoolFactory}
 * instance.
 * </p>
 * <p>
 * In a Tomcat web application, the name of this class is the value of the factory attribute of a
 * <code>&lt;Resource&gt;</code> tag. The parameters given in the table above are also specified as attributes of the
 * resource tag. The type attribute is
 * <code>com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory.</code> The resource should always be a
 * singleton and a closeMethod attribute with the value "<code>close</code>" should also be present. The
 * <code>auth</code> attribute should have the value "<code>Container</code>". The server config file for OrientDB
 * should be passed in with the attribute "<code>configFile</code>" if you wish to run an embedded OrientDB server. The
 * maximum number of connections can be set with the <code>capacity</code> attribute.<br>
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
 *   server="oServer"
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
    private static final String JAVA_COMP_ENV = "java:comp/env";
    private static final Object LOCK = new Object();
    private static final String SERVER = "server";

    private static OPartitionedDatabasePoolFactory factory;
    private static Object server;


    /**
     * Returns an OPartitionedDatabasePoolFactory instance and optionally initialises an embedded OServer instance if
     * the resource name is passed in as an attribute. This instance is always a singleton, regardless of attributes in
     * server.xml files etc.
     * <p>
     * The OServer instance is just looked up in JNDI. It is the responsibility of the OServer object factory to
     * actually start the server up.
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
        if (factory == null) {
            synchronized (LOCK) {
                if (factory == null) {
                    final Reference reference = (Reference) obj;

                    int capacity = 100;
                    String serverRef = null;

                    for (final Enumeration<RefAddr> e = reference.getAll(); e.hasMoreElements();) {
                        final RefAddr addr = e.nextElement();
                        if (CAPACITY.equalsIgnoreCase(addr.getType())) {
                            capacity = Integer.valueOf((String) addr.getContent());
                        } else if (SERVER.equalsIgnoreCase(addr.getType())) {
                            serverRef = (String) addr.getContent();
                        }
                    }

                    if (server == null) {
                        if (serverRef != null) {
                            if (nameCtx != null) {
                                server = nameCtx.lookup(serverRef);
                            } else {
                                final Context initCtx = new InitialContext(environment);
                                final Context envCtx = (Context) initCtx.lookup(JAVA_COMP_ENV);
                                server = envCtx.lookup(serverRef);
                            }
                        }
                    }
                    factory = new OPartitionedDatabasePoolFactory(capacity);
                }
            }
        }
        return factory;
    }
}

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
 * The <code>auth</code> attribute should have the value "<code>Container</code>".
 * </p>
 * <p>
 * The maximum number of connections can be set with the <code>capacity</code> attribute.
 * </p>
 * <p>
 * The resource should always be a singleton and a <code>closeMethod</code> attribute with the value "<code>close</code>
 * " should also be present.
 * </p>
 * <p>
 * Use the name of this class as the value of the <code>factory</code> attribute of the <code>&lt;Resource&gt;</code>
 * tag.
 * </p>
 * <p>
 * The <code>server</code> attribute is optional and should be used when running an embedded database. It references an
 * <code>OServer</code> instance produced by an <code>OServerObjectFactory</code> factory. If the server attribute is
 * present and this element is not, you will get a naming exception. If the other resource element is present but the
 * server attribute is not declared here, your server may never be started because the JNDI lookup never occurs.
 * </p>
 * <p>
 * The <code>type</code> attribute is <code>com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory.</code>
 * </p>
 * <p>
 * An example resource declaration:
 * </p>
 *
 * <pre>
 * &lt;Resource
 *   auth="Container"
 *   capacity="100"
 *   closeMethod="close"
 *   factory="com.ashtonit.odb.OPDPFObjectFactory"
 *   name="opdpfactory"
 *   server="oserver"
 *   singleton="true"
 *   type="com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory"
 * /&gt;
 * </pre>
 *
 * @author Bruce Ashton
 */
public class OPDPFObjectFactory implements ObjectFactory {

    private static final String CAPACITY = "capacity";
    private static final String JAVA_COMP_ENV = "java:comp/env";
    private static final Object LOCK = new Object();
    private static final String SERVER = "server";

    private static OPartitionedDatabasePoolFactory factory;
    private static Object server;


    /**
     * Returns an OPartitionedDatabasePoolFactory instance and optionally initializes an embedded OServer instance if
     * the resource name is passed in as an attribute. This instance is always a singleton, regardless of attributes in
     * server.xml files etc.
     * <p>
     * The OServer instance is just looked up in JNDI, if it is declared in the resource element. It is the
     * responsibility of the OServer object factory to actually start the server up. Be sure to add a
     * <code>OServerObjectFactory</code> resource element for the server if you do reference it here.
     *
     * @param obj the naming reference
     * @param name not used
     * @param nameCtx the naming context used if present
     * @param environment used to create an initial context if a naming context is not passed in
     * @return the {@link OPartitionedDatabasePoolFactory} instance
     * @throws NamingException if the declared server instance cannot be found in the JNDI context
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

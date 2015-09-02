package com.ashtonit.odb.realm;

import static com.ashtonit.odb.Version.VERSION;

import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.realm.RealmBase;
import org.ietf.jgss.GSSContext;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;


/**
 * A Tomcat Realm class for OrientDB graph databases.
 * <p>
 * OdbRealm allows a web application to authenticate users against an embedded OrientDB database. Authentication is for
 * actual database users, not just arbitrary records in the database.
 * </p>
 * <p>
 * The <a href="https://tomcat.apache.org/tomcat-8.0-doc/config/realm.html" target="_blank">Tomcat guide on realm
 * configuration is here</a>. Important things to note for OdbRealm configuration are:
 * <ol>
 * <li>The className attribute must have a value of "com.ashtonit.odb.realm.OdbRealm".</li>
 * <li>The value of the dbUser attribute must be the name of a user with read access to the OUser class in the OrientDB
 * database. The "admin" user can be used for this for development and testing purposes.</li>
 * <li>The value of the dbResource attribute must match the value of the "name" attribute in your OdbResource
 * configuration.</li>
 * <li>The value of the dbUrl attribute must be a valid OrientDB URI.</li>
 * <li>The realm uses a separate connection pool just for authenticating users. The pool size defaults to 64.</li>
 * </ol>
 * <p>
 * An example of an OdbRealm definition would be:
 * 
 * <pre>
 *   &lt;Realm
 *     className="com.ashtonit.odb.realm.OdbRealm"
 *     dbPass="admin"
 *     dbResource="odbp"
 *     dbUrl="plocal:/opt/odb/mygraphdb"
 *     dbUser="admin"
 *     poolSize="64"
 *   /&gt;
 * </pre>
 * 
 * @author Bruce Ashton
 * @date 2014-06-22
 */
public class OdbRealm extends RealmBase {

    protected static final String info = OdbRealm.class.getName() + "/" + VERSION;
    protected static final String name = "OdbRealm";

    private static final Logger log = Logger.getLogger(OdbRealm.class.getName());

    private static final String NAME = "name";
    private static final String PASSWORD = "password";
    private static final String ROLES = "roles";
    private static final String SELECT = "select from OUser where name = ?";
    private static final String SHA256 = "SHA-256";
    private static final String SHA256_PREFIX = "{SHA-256}";

    private final Object poolLock = new Object();

    private String dbPass;
    private String dbResource;
    private Object dbResourceImpl;
    private String dbUrl;
    private String dbUser;
    private OPartitionedDatabasePool pool;
    private int poolSize;


    /**
     * The default constructor sets the SHA-256 message digest credential handler and default configuration parameters
     * as required.
     */
    public OdbRealm() {
        final MessageDigestCredentialHandler handler = new MessageDigestCredentialHandler();
        try {
            handler.setAlgorithm(SHA256);
        } catch (final NoSuchAlgorithmException e) {
            containerLog.error("Authentication failed: dbUrl=" + dbUrl, e);
            log.severe("authenticate(String, String): dbUrl=" + dbUrl);
            log.throwing(OdbRealm.class.getName(), "authenticate(String, String)", e);
        }
        setCredentialHandler(handler);
        setPoolSize(64);
    }


    /**
     * This method of authentication is not supported by this implementation.
     *
     * @param gssContext
     * @param storeCred
     * @return this method never returns
     * @see RealmBase#authenticate(GSSContext, boolean)
     * @throws UnsupportedOperationException when called
     */
    @Override
    public Principal authenticate(final GSSContext gssContext, final boolean storeCred) throws UnsupportedOperationException {
        log.severe("authenticate(String): dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    /**
     * This method of authentication is not supported by this implementation.
     *
     * @param username
     * @return this method never returns
     * @see RealmBase#authenticate(String)
     * @throws UnsupportedOperationException when called
     */
    @Override
    public Principal authenticate(final String username) throws UnsupportedOperationException {
        log.severe("authenticate(String): username=" + username + " dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    /**
     * Authenticates a database user. This is the only method of authentication (username and password) that this realm
     * implementation supports. This is because of limitations imposed by the arguments needed to construct an
     * OrientGraph instance - one of the arguments must be the password in plain text.
     *
     * @param username the username to authenticate
     * @param password the password associated with the username
     * @return an OdbPrincipal instance if authentication is successful, null otherwise
     * @see RealmBase#authenticate(String, String)
     */
    @Override
    public Principal authenticate(final String username, final String password) {
        log.info("authenticate(String, String): username=" + username + " dbUrl=" + dbUrl);
        if (username == null) {
            containerLog.warn("username is null");
            return null;
        }
        if (password == null) {
            containerLog.warn("credentials is null");
            return null;
        }

        ODatabaseDocumentTx db = null;
        try {
            db = getDb();
            final ODocument document = getODocument(db, username);

            if (getCredentialHandler().matches(password, getPassword(document))) {
                final List<String> roles = getRoles(document);
                return new OdbPrincipal(username, password, roles, dbUrl);
            }
        } catch (final Exception e) {
            containerLog.error("Authentication failed: dbUrl=" + dbUrl, e);
            log.warning("authenticate(String, String): username=" + username + " dbUrl=" + dbUrl);
            log.throwing(OdbRealm.class.getName(), "authenticate(String, String)", e);
        } finally {
            if (db != null) {
                db.close();
            }
        }
        return null;
    }


    /**
     * This method of authentication is not supported by this implementation.
     *
     * @param username
     * @param clientDigest
     * @param nonce
     * @param nc
     * @param cnonce
     * @param qop
     * @param realm
     * @param md5a2
     * @return this method never returns
     * @see RealmBase#authenticate(String, String, String, String, String, String, String, String)
     * @throws UnsupportedOperationException when called
     */
    @Override
    public Principal authenticate(final String username, final String clientDigest, final String nonce, final String nc, final String cnonce, final String qop, final String realm, final String md5a2) throws UnsupportedOperationException {
        log.severe("authenticate(String, String, String, String, String, String, String, String): username=" + username
                + " dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    /**
     * This method of authentication is not supported by this implementation.
     *
     * @param certs
     * @return this method never returns
     * @see RealmBase#authenticate(X509Certificate[])
     * @throws UnsupportedOperationException when called
     */
    @Override
    public Principal authenticate(final X509Certificate certs[]) throws UnsupportedOperationException {
        log.severe("authenticate(X509Certificate[]): dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    /**
     * The password for the generic user to connect to the database with so that we can look up the principal.
     *
     * @param dbPass the password for the generic user
     */
    public void setDbPass(final String dbPass) {
        this.dbPass = dbPass;
    }


    /**
     * Sets the name of an arbitrary database resource class. The resource is never used, but may need to be discovered
     * via the JNDI service to prompt some initialisation before any user queries are made. This is the case with the
     * OdbResource OrientGraph pool.
     *
     * @param dbResource the JNDI name of the resource
     */
    public void setDbResource(final String dbResource) {
        this.dbResource = dbResource;
    }


    /**
     * Sets the URL for the OrientDB database.
     *
     * @param dbUrl the URL for the OrientDB database
     */
    public void setDbUrl(final String dbUrl) {
        this.dbUrl = dbUrl;
    }


    /**
     * Sets the generic username to connect to the database with so that we can look up the principal. It needs read
     * permissions on the OUser class.
     *
     * @param dbUser the generic username to connect to the database
     */
    public void setDbUser(final String dbUser) {
        this.dbUser = dbUser;
    }


    /**
     * Sets the database pool size for the realm user only. This pool is only used for authentication. It has no effect
     * on the OrientGraphPool instance that the web application will interact with.
     *
     * @param poolSize the database pool size
     */
    public void setPoolSize(final int poolSize) {
        this.poolSize = poolSize;
    }


    /**
     * Return a short name for this Realm implementation, for use in log messages.
     *
     * @return a short name for this Realm implementation
     * @see RealmBase#getName()
     */
    @Override
    protected String getName() {
        return name;
    }


    /**
     * This method is not supported by this implementation.
     *
     * @param username
     * @return his method never returns
     * @see RealmBase#getPassword(String)
     * @throws UnsupportedOperationException when called
     */
    @Override
    protected String getPassword(final String username) throws UnsupportedOperationException {
        log.severe("getPassword(String): username=" + username + " dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    /**
     * This method is not supported by this implementation.
     *
     * @param username
     * @return his method never returns
     * @see RealmBase#getPrincipal(String)
     * @throws UnsupportedOperationException when called
     */
    @Override
    protected Principal getPrincipal(final String username) throws UnsupportedOperationException {
        log.severe("getPrincipal(String): username=" + username + " dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    private final ODatabaseDocumentTx getDb() throws NamingException {
        if (dbResourceImpl == null && dbResource != null) {
            // We never use dbResourceImpl but only look it up once.
            final Context initCtx = new InitialContext();
            final Context envCtx = (Context) initCtx.lookup("java:comp/env");
            dbResourceImpl = envCtx.lookup(dbResource);
        }
        if (pool == null) {
            // Set the internal realm connection pool.
            synchronized (poolLock) {
                if (pool == null) {
                    pool = new OPartitionedDatabasePool(dbUrl, dbUser, dbPass, poolSize);
                }
            }
        }
        return pool.acquire();
    }


    private final ODocument getODocument(final ODatabaseDocumentTx db, final String username) {
        final OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(SELECT, 1);
        final List<ODocument> list = db.command(query).execute(username);
        if (list.isEmpty()) {
            containerLog.warn(username + " not found in database " + dbUrl);
            return null;
        }
        return list.get(0);
    }


    private String getPassword(final ODocument document) throws NoSuchAlgorithmException {
        if (document != null) {
            final String password = document.field(PASSWORD);
            if (password != null && password.startsWith(SHA256_PREFIX)) {
                // MessageDigestCredentialHandler cannot handle the prefix.
                return password.substring(SHA256_PREFIX.length());
            }
            return password;
        }
        return null;
    }


    private final List<String> getRoles(final ODocument document) {
        final List<String> roles = new ArrayList<String>();
        if (document != null) {
            final Set<ODocument> roleDocs = document.field(ROLES);
            if (roleDocs != null) {
                // If there are no roles, add an empty collection to the
                // principal. Let the chips fall where they may.
                for (final ODocument roleDoc : roleDocs) {
                    final String role = roleDoc.field(NAME);
                    roles.add(role);
                }
            }
        }
        return roles;
    }
}

package com.ashtonit.odb.realm;

import static com.ashtonit.odb.realm.Version.VERSION;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.catalina.realm.RealmBase;
import org.ietf.jgss.GSSContext;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.security.OSecurityManager;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;


/**
 * A Tomcat Realm class for OrientDB databases.
 * <p>
 * OdbRealm allows a web application to authenticate users against an embedded
 * or standalone OrientDB database. It was originally written to authenticate
 * only against the built-in <code>OUser</code> and <code>ORole</code> classes
 * but the authentication is now configurable by a query.
 * </p>
 * <p>
 * It takes an OSQL query string as a attribute named <code>query</code> which
 * must return the password hash and the roles for a user.
 * </p>
 * <p>
 * The query must:
 * </p>
 * <ul>
 * <li>Take one parameter (the user name or identifier)</li>
 * <li>Return the password hash as a {@link String} for the user with a
 * parameter name of, "password"</li>
 * <li>Return the role names for the user as a {@link List} of {@link String}s
 * with a parameter name of, "roles"</li>
 * </ul>
 * <p>
 * The password is checked using the method
 * {@link OSecurityManager#checkPassword(String, String)}. It checks for three
 * different types of password hashes by looking at the prefix of the string.
 * They are:
 * </p>
 * <ul>
 * <li>SHA-256 (A string prefix of <code>{SHA-256}-</code>)</li>
 * <li>PBKDF2WithHmacSHA1 (A string prefix of
 * <code>{PBKDF2WithHmacSHA1}-</code>)</li>
 * <li>PBKDF2WithHmacSHA256 (A string prefix of
 * <code>{PBKDF2WithHmacSHA256}-</code>)</li>
 * </ul>
 * <p>
 * The simplest way to create a password hash in the correct format is to use
 * the method
 * {@link com.orientechnologies.orient.core.metadata.security.OUser#encryptPassword(String)}.
 * </p>
 * <p>
 * Important things to note for OdbRealm configuration are:
 * <ol>
 * <li>The <code>className</code> attribute must have a value of
 * "<code>com.ashtonit.odb.realm.OdbRealm</code>".</li>
 * <li>The value of the <code>dbUser</code> attribute must be the name of a
 * database user with read access to the user class for this realm. The "admin"
 * user can be used for this for development and testing purposes.</li>
 * <li>The value of the <code>dbResource</code> attribute must match the value
 * of the "<code>name</code>" attribute in your OdbResource configuration. If it
 * is not present the realm creates its own instance of
 * {@link OPartitionedDatabasePool} with the default capacity of 100.</li>
 * <li>The value of the <code>dbUrl</code> attribute must be a valid OrientDB
 * URI.</li>
 * <li>The value of the <code>query</code> attribute must be an OSQL query
 * string that takes one parameter (a user identifier) and returns the password
 * hash and roles.</li>
 * </ol>
 * <p>
 * An example OdbRealm definition:
 *
 * <pre>
 *   &lt;Realm
 *     className="com.ashtonit.odb.realm.OdbRealm"
 *     dbPass="admin"
 *     dbResource="opdpfactory"
 *     dbUrl="plocal:/opt/odb/mydb"
 *     dbUser="admin"
 *     query="SELECT password, roles.name AS roles FROM OUser WHERE status = 'ACTIVE' AND name = ?"
 *   /&gt;
 * </pre>
 * <p>
 * The
 * <a href="https://tomcat.apache.org/tomcat-8.0-doc/config/realm.html" target=
 * "_blank">Tomcat guide on realm configuration is here</a>.
 * </p>
 *
 * @author Bruce Ashton
 */
public class OdbRealm extends RealmBase {

    protected static final String info = OdbRealm.class.getName() + "/" + VERSION;
    protected static final String name = "OdbRealm";

    private static final Logger log = Logger.getLogger(OdbRealm.class.getName());
    private static final String PASSWORD = "password";
    private static final String ROLES = "roles";

    private volatile OPartitionedDatabasePool pool;
    private final Object poolLock = new Object();

    private String dbPass;
    private String dbResource;
    private String dbUrl;
    private String dbUser;
    private String query;


    /**
     * This method of authentication is not supported by this implementation.
     *
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
     * Authenticates a database user. This is the only method of authentication
     * (username and password) that this realm implementation supports. This is
     * because of limitations imposed by the arguments needed to construct an
     * OrientGraph instance - one of the arguments must be the password in plain
     * text.
     *
     * @param username the username to authenticate
     * @param password the password associated with the username
     * @return an OdbPrincipal instance if authentication is successful, null
     *         otherwise
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

        try (ODatabaseDocument document = getPool().acquire()) {

            final OResult result = getOResult(document, username);
            if (result == null) {
                return null;
            }

            final String hash = result.getProperty(PASSWORD);
            if (hash == null) {
                throw new NullPointerException("The password field of the query returned null");
            }

            final List<String> roles = result.getProperty(ROLES);
            if (roles == null) {
                throw new NullPointerException("The roles field of the query returned null");
            }

            if (OSecurityManager.instance().checkPassword(password, hash)) {
                return new OdbPrincipal(username, password, roles, dbUrl);
            }
        } catch (final Exception e) {
            containerLog.error("Authentication failed: dbUrl=" + dbUrl, e);
            log.warning("authenticate(String, String): username=" + username + " dbUrl=" + dbUrl);
            log.throwing(OdbRealm.class.getName(), "authenticate(String, String)", e);
        }
        return null;
    }


    /**
     * This method of authentication is not supported by this implementation.
     *
     * @return this method never returns
     * @see RealmBase#authenticate(String, String, String, String, String,
     *      String, String, String)
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
     * The password for the generic user to connect to the database with so that
     * we can look up the principal.
     *
     * @param dbPass the password for the generic user
     */
    public void setDbPass(final String dbPass) {
        this.dbPass = dbPass;
    }


    /**
     * Sets the name of an arbitrary database resource instance.
     * <p>
     * If present the realm will use it to look up the
     * {@link OPartitionedDatabasePoolFactory} in the Tomcat JNDI service and
     * obtain a pool from it.
     * </p>
     * <p>
     * If it is not present the realm will create a new
     * {@link OPartitionedDatabasePool} with the default capacity of 100.
     * </p>
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
     * Sets the generic username to connect to the database with so that we can
     * look up the principal. It needs read permissions on the OUser class.
     *
     * @param dbUser the generic username to connect to the database
     */
    public void setDbUser(final String dbUser) {
        this.dbUser = dbUser;
    }


    /**
     * Sets the SQL query used to select the password and roles for the given
     * user name.
     *
     * @param query the query used to select the password and roles for the
     *        given user name
     */
    public void setQuery(final String query) {
        this.query = query;
    }


    /**
     * Return a short name for this Realm implementation, for use in log
     * messages.
     *
     * @return a short name for this Realm implementation
     */
    protected String getName() {
        return name;
    }


    /**
     * This method is not supported by this implementation.
     *
     * @return this method never returns
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
     * @return this method never returns
     * @see RealmBase#getPrincipal(String)
     * @throws UnsupportedOperationException when called
     */
    @Override
    protected Principal getPrincipal(final String username) throws UnsupportedOperationException {
        log.severe("getPrincipal(String): username=" + username + " dbUrl=" + dbUrl);
        throw new UnsupportedOperationException();
    }


    private final OResult getOResult(final ODatabaseDocument document, final String username) {
        OResult result = null;
        try (final OResultSet set = document.query(query, username)) {
            if (set.hasNext()) {
                result = set.next();
                if (set.hasNext()) {
                    throw new IndexOutOfBoundsException("More than one result: username=" + username);
                }
            }
        }
        return result;
    }


    private final OPartitionedDatabasePool getPool() throws NamingException {
        if (pool == null) {
            synchronized (poolLock) {
                if (pool == null) {
                    // Set the internal realm connection pool.
                    if (dbResource != null) {
                        // Use the factory if dbResource is set.
                        final Context initCtx = new InitialContext();
                        final Context envCtx = (Context) initCtx.lookup("java:comp/env");
                        final OPartitionedDatabasePoolFactory factory = (OPartitionedDatabasePoolFactory) envCtx.lookup(dbResource);
                        pool = factory.get(dbUrl, dbUser, dbPass);
                    } else {
                        pool = new OPartitionedDatabasePool(dbUrl, dbUser, dbPass);
                    }
                }
            }
        }
        return pool;
    }
}

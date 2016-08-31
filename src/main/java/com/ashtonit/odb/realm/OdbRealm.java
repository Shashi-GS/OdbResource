package com.ashtonit.odb.realm;

import static com.ashtonit.odb.realm.Version.VERSION;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.catalina.realm.RealmBase;
import org.ietf.jgss.GSSContext;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.security.OSecurityManager;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;


/**
 * A Tomcat Realm class for OrientDB databases.
 * <p>
 * OdbRealm allows a web application to authenticate users against an embedded or standalone OrientDB database. It was
 * originally written to authenticate against the built-in <code>OUser</code> and <code>ORole</code> classes but the
 * authentication classes used are now configurable.
 * </p>
 * <p>
 * It makes certain assumptions about the structure of the classes containing user and role records:
 * </p>
 * <ul>
 * <li>There will be a class holding user records with a unique username field.</li>
 * <li>Each user record will have a password field.</li>
 * <li>The password field will be encrypted in the same manner as the <code>password</code> field for
 * <code>OUser.</code> (Or it may be plain text.)</li>
 * <li>Each user record will contain a collection of roles represented by either linked documents or strings.</li>
 * </ul>
 * <p>
 * The following is an example class for user records holding role names as strings:
 * </p>
 * 
 * <pre>
 * _________________________
 * | User                   |
 * |------------------------|
 * | name:     String       |
 * | password: String       |
 * | roles:    List&lt;String&gt; |
 * |________________________|
 * </pre>
 * 
 * Here the attributes would be:
 * <ul>
 * <li><code>userClass="User"</code></li>
 * <li><code>userNameField="name"</code></li>
 * <li><code>userPassField="password"</code></li>
 * <li><code>rolesField="roles"</code></li>
 * </ul>
 * <p>
 * An alternative example where the roles are a separate linked document:
 * </p>
 * 
 * <pre>
 * _______________________ 
 * | MyUser               |
 * |----------------------|
 * | username: String     |
 * | password: String     |   ___________________ 
 * | roles:    List&lt;Role&gt; |==&gt;| MyRole           |
 * |______________________|   |------------------|
 *                            | rolename: String |
 *                            |__________________|
 * </pre>
 * 
 * Here the attributes would be:
 * <ul>
 * <li><code>userClass="MyUser"</code></li>
 * <li><code>userNameField="username"</code></li>
 * <li><code>userPassField="password"</code></li>
 * <li><code>rolesField="roles"</code></li>
 * <li><code>roleNameField="rolename"</code></li>
 * </ul>
 * <p>
 * The latter example is essentially the same structure as the built-in OUser and ORole classes. This realm can be used
 * to authenticate against those classes, using actual database users.
 * </p>
 * <p>
 * For the former structure, set the <code>rolesField</code> attribute on the realm but not the
 * <code>roleNameField</code> attribute. <code>OdbRealm</code> will then assume the field is a collection of strings.
 * </p>
 * <p>
 * For the latter structure, with a separate Role class:
 * </p>
 * <ul>
 * <li>Set the <code>rolesField</code> attribute to the name of the collection field on the user class</li>
 * <li>Set the <code>roleNameField</code> attribute to the name of the unique name field on the Role class.</li>
 * </ul>
 * <p>
 * In this case <code>OdbRealm</code> will then assume the field is a collection of {@link ODocument}s.
 * </p>
 * <p>
 * The password is checked using the method {@link OSecurityManager#checkPassword(String, String)}. It checks for three
 * different types of password hashes by looking at the prefix of the string. They are:
 * </p>
 * <ul>
 * <li>SHA-256 (A string prefix of <code>{SHA-256}-</code>)</li>
 * <li>PBKDF2WithHmacSHA1 (A string prefix of <code>{PBKDF2WithHmacSHA1}-</code>)</li>
 * <li>PBKDF2WithHmacSHA256 (A string prefix of <code>{PBKDF2WithHmacSHA256}-</code>)</li>
 * </ul>
 * <p>
 * The simplest way to create a password hash in the correct format is to use the method
 * {@link com.orientechnologies.orient.core.metadata.security.OUser#encryptPassword(String)}.
 * </p>
 * <p>
 * Important things to note for OdbRealm configuration are:
 * <ol>
 * <li>The <code>className</code> attribute must have a value of "<code>com.ashtonit.odb.realm.OdbRealm</code>".</li>
 * <li>The value of the <code>dbUser</code> attribute must be the name of a database user with read access to the user
 * class for this realm. The "admin" user can be used for this for development and testing purposes.</li>
 * <li>The value of the <code>dbResource</code> attribute must match the value of the "<code>name</code>" attribute in
 * your OdbResource configuration. If it is not present the realm creates its own instance of
 * {@link OPartitionedDatabasePool} with the default capacity of 100.</li>
 * <li>The value of the <code>dbUrl</code> attribute must be a valid OrientDB URI.</li>
 * <li>The <code>roleNameField</code> attribute is optional and indicates the roles filed on the user class is a
 * collection of documents.</li>
 * <li>The value of the <code>rolesField</code> attribute is field on the user class referencing the roles collection.</li>
 * <li>The value of the <code>userClass</code> attribute is the name of the class holding user records.</li>
 * <li>The value of the <code>userNameField</code> attribute is the user class field holding the unique user name or
 * identifier.</li>
 * <li>The value of the <code>userPassField</code> attribute is the user class field holding the encrypted password or
 * credentials for the user.</li>
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
 *     roleNameField="name"
 *     rolesField="roles"
 *     userClass="OUser"
 *     userNameField="name"
 *     userPassField="password"
 *   /&gt;
 * </pre>
 * <p>
 * The <a href="https://tomcat.apache.org/tomcat-8.0-doc/config/realm.html" target="_blank">Tomcat guide on realm
 * configuration is here</a>.
 * </p>
 * 
 * @author Bruce Ashton
 */
public class OdbRealm extends RealmBase {

    protected static final String info = OdbRealm.class.getName() + "/" + VERSION;
    protected static final String name = "OdbRealm";

    private static final Logger log = Logger.getLogger(OdbRealm.class.getName());

    private String dbPass;
    private String dbResource;
    private String dbUrl;
    private String dbUser;
    private OPartitionedDatabasePool pool;
    private String roleNameField;
    private String rolesField;
    private String select;
    private String userClass;
    private String userNameField;
    private String userPassField;


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
            if (document == null) {
                return null;
            }
            if (userPassField == null) {
                throw new NullPointerException("userPassField must be set");
            }
            final String hash = document.field(userPassField);
            if (hash == null) {
                return null;
            }
            if (OSecurityManager.instance().checkPassword(password, hash)) {
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
     * Sets the name of an arbitrary database resource instance.
     * <p>
     * If present the realm will use it to look up the {@link OPartitionedDatabasePoolFactory} in the Tomcat JNDI
     * service and obtain a pool from it.
     * </p>
     * <p>
     * If it is not present the realm will create a new {@link OPartitionedDatabasePool} with the default capacity of
     * 100.
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
     * Sets the generic username to connect to the database with so that we can look up the principal. It needs read
     * permissions on the OUser class.
     *
     * @param dbUser the generic username to connect to the database
     */
    public void setDbUser(final String dbUser) {
        this.dbUser = dbUser;
    }


    /**
     * Sets the field on the role class that references the role name.
     *
     * @param roleNameField the field that references the role name
     */
    public void setRoleNameField(final String roleNameField) {
        this.roleNameField = roleNameField;
    }


    /**
     * Sets the field on the user class that references the roles collection
     *
     * @param rolesField the field that references the roles collection
     */
    public void setRolesField(final String rolesField) {
        this.rolesField = rolesField;
    }


    /**
     * Sets the name of the user class, that is the class that holds username and password records.
     *
     * @param userClass the name of the user class
     */
    public void setUserClass(final String userClass) {
        this.userClass = userClass;
    }


    /**
     * Sets the field on the user class that references the user name.
     *
     * @param userNameField the field that references the user name
     */
    public void setUserNameField(final String userNameField) {
        this.userNameField = userNameField;
    }


    /**
     * Sets the field on the user class that references the password.
     *
     * @param userPassField the field that references the password
     */
    public void setUserPassField(final String userPassField) {
        this.userPassField = userPassField;
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


    private final ODatabaseDocumentTx getDb() throws NamingException {
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
        return pool.acquire();
    }


    private final ODocument getODocument(final ODatabaseDocumentTx db, final String username) {
        final OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<ODocument>(getSelect(), 1);
        final List<ODocument> list = db.command(query).execute(username);
        if (list.isEmpty()) {
            containerLog.warn(username + " not found in database " + dbUrl);
            return null;
        }
        return list.get(0);
    }


    private final List<String> getRoles(final ODocument document) {
        final List<String> roles = new ArrayList<String>();
        if (document != null) {
            if (rolesField == null) {
                throw new NullPointerException("rolesField must be set");
            }
            if (roleNameField == null) {
                final Collection<String> roleNames = document.field(rolesField);
                if (roleNames != null) {
                    // If there are no roles, add an empty collection to the
                    // principal. Let the chips fall where they may.
                    roles.addAll(roleNames);
                }
            } else {
                final Collection<ODocument> roleDocs = document.field(rolesField);
                if (roleDocs != null) {
                    // As above.
                    for (final ODocument roleDoc : roleDocs) {
                        final String role = roleDoc.field(roleNameField);
                        roles.add(role);
                    }
                }
            }
        }
        return roles;
    }


    private String getSelect() {
        if (select == null) {
            if (userClass == null) {
                throw new NullPointerException("userClass must be set");
            }
            if (userNameField == null) {
                throw new NullPointerException("userNameField must be set");
            }
            final StringBuilder builder = new StringBuilder(64);
            builder.append("select from ");
            builder.append(userClass);
            builder.append(" where ");
            builder.append(userNameField);
            builder.append(" = ?");
            select = builder.toString();
        }
        return select;
    }
}

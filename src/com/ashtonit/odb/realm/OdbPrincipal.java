package com.ashtonit.odb.realm;

import static com.ashtonit.odb.Version.VERSION;

import java.security.Principal;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.login.LoginContext;

import org.apache.catalina.realm.GenericPrincipal;
import org.ietf.jgss.GSSCredential;


/**
 * This class extends a GenericPrincipal and also contains a dbUrl property the
 * value of which must be the URL of the OrientDB database.
 * 
 * @author Bruce Ashton
 * @date 2014-07-03
 */
public class OdbPrincipal extends GenericPrincipal {

    protected static final String info = OdbPrincipal.class.getName() + "/" + VERSION;

    private static final Logger log = Logger.getLogger(OdbPrincipal.class.getName());

    private static final long serialVersionUID = 1L;

    private final String dbUrl;


    /**
     * @param name The username of the user represented by this Principal
     * @param password Credentials used to authenticate this user
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal
     * @param loginContext
     * @param gssCredential
     * @param dbUrl the URL of the database
     */
    OdbPrincipal(final String name, final String password, final List<String> roles, final Principal userPrincipal, final LoginContext loginContext, final GSSCredential gssCredential, final String dbUrl) {
        super(name, password, roles, userPrincipal, loginContext, gssCredential);
        if (log.isLoggable(Level.FINE)) {
            log.fine(info);
            log.fine(toString());
        }
        this.dbUrl = dbUrl;
    }


    /**
     * @param name The username of the user represented by this Principal
     * @param password Credentials used to authenticate this user
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal
     * @param loginContext
     * @param dbUrl the URL of the database
     */
    OdbPrincipal(final String name, final String password, final List<String> roles, final Principal userPrincipal, final LoginContext loginContext, final String dbUrl) {
        super(name, password, roles, userPrincipal, loginContext);
        if (log.isLoggable(Level.FINE)) {
            log.fine(info);
            log.fine(toString());
        }
        this.dbUrl = dbUrl;
    }


    /**
     * @param name The username of the user represented by this Principal
     * @param password Credentials used to authenticate this user
     * @param roles List of roles (must be Strings) possessed by this user
     * @param userPrincipal
     * @param dbUrl the URL of the database
     */
    OdbPrincipal(final String name, final String password, final List<String> roles, final Principal userPrincipal, final String dbUrl) {
        super(name, password, roles, userPrincipal);
        if (log.isLoggable(Level.FINE)) {
            log.fine(info);
            log.fine(toString());
        }
        this.dbUrl = dbUrl;
    }


    /**
     * @param name The username of the user represented by this Principal
     * @param password Credentials used to authenticate this user
     * @param roles List of roles (must be Strings) possessed by this user
     * @param dbUrl the URL of the database
     */
    OdbPrincipal(final String name, final String password, final List<String> roles, final String dbUrl) {
        super(name, password, roles);
        if (log.isLoggable(Level.FINE)) {
            log.fine(info);
            log.fine(toString());
        }
        this.dbUrl = dbUrl;
    }


    public String getDbUrl() {
        return dbUrl;
    }


    /**
     * Returns an informational string for this class.
     * 
     * @return an informational string for this class
     */
    public String getInfo() {
        return info;
    }


    /**
     * Returns the name and roles for this Principal.
     * 
     * @return the name and roles for this Principal
     * @see GenericPrincipal#toString()
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ImapPrincipal[");
        sb.append(name);
        sb.append("(");
        if (roles != null && roles.length > 0) {
            sb.append(roles[0]);
            for (int i = 1; i < roles.length; i++) {
                sb.append(",");
                sb.append(roles[i]);
            }
        }
        sb.append(")]");
        return sb.toString();
    }
}

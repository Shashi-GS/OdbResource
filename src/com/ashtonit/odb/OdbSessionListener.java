package com.ashtonit.odb;

import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import com.ashtonit.odb.pool.OrientGraphPool;
import com.ashtonit.odb.realm.OdbPrincipal;


/**
 * <p>
 * Because OrientGraph instances always have an associated user, instances for a user who has logged out can clog up the
 * OrientGraph pool. This class cleans up old OrientGraph instances associated with a user when that user logs out.
 * </p>
 * <p>
 * The JNDI name of the pool must be set as a context parameter with the param name, "orientGraphPool", on the web
 * application. Typically this is done with a context-param element in the web.xml file, e.g.
 * </p>
 * 
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;orientGraphPool&lt;/param-name&gt;
 *     &lt;param-value&gt;odbp&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 * <p>
 * This class also expects the principal instance to be an {@link OdbPrincipal} object , and set as an attribute on the
 * session object. The name of the attribute is the class name of {@link OdbPrincipal} as returned by
 * OdbPrincipal.class.getName(). If you use {@link OdbFilter} to set your graph object for each request, this
 * requirement is automatically fulfilled.
 * </p>
 * 
 * @author Bruce Ashton
 * @date 2015-07-21
 */
public class OdbSessionListener implements HttpSessionListener, ServletContextListener {

    private static final Logger log = Logger.getLogger(OdbSessionListener.class.getName());

    private String orientGraphPool;


    /**
     * Does nothing.
     * 
     * @param event the event object
     * @see ServletContextListener#contextDestroyed(ServletContextEvent)
     */
    @Override
    public void contextDestroyed(final ServletContextEvent event) {
        log.fine("contextDestroyed(ServletContextEvent)");
    }


    /**
     * Sets the JNDI name of the graph database pool instance relative to, "java:comp/env". This is taken from the
     * servlet context and can be configured in the web.xml file with a context-param element, as below;
     * 
     * <pre>
     * &lt;context-param&gt;
     *     &lt;param-name&gt;orientGraphPool&lt;/param-name&gt;
     *     &lt;param-value&gt;odbp&lt;/param-value&gt;
     * &lt;/context-param&gt;
     * </pre>
     * 
     * @param event the event object
     * @see ServletContextListener#contextInitialized(ServletContextEvent)
     */
    @Override
    public void contextInitialized(final ServletContextEvent event) {
        log.fine("contextInitialized(ServletContextEvent)");
        orientGraphPool = event.getServletContext().getInitParameter(OdbFilter.ORIENT_GRAPH_POOL);
    }


    /**
     * Does nothing.
     * 
     * @param event the event object
     * @see HttpSessionListener#sessionCreated(HttpSessionEvent)
     */
    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        log.fine("sessionCreated(HttpSessionEvent)");
    }


    /**
     * This method removes all OrientGraph instance from the pool that are associated with the session principal when
     * the session is destroyed. It relies on the principal being set as an attribute on the session object. The name of
     * the attribute is the class name of {@link OdbPrincipal} as returned by OdbPrincipal.class.getName(). If this
     * attribute is not set this method does nothing. The {@link OdbFilter} class sets the attribute automatically.
     * 
     * @param event the event object
     * @see HttpSessionListener#sessionDestroyed(HttpSessionEvent)
     */
    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        log.fine("sessionDestroyed(HttpSessionEvent)");
        final HttpSession session = event.getSession();
        final OdbPrincipal principal = (OdbPrincipal) session.getAttribute(OdbPrincipal.class.getName());
        if (principal != null) {
            log.fine("sessionDestroyed(HttpSessionEvent): principal != null");
            try {
                final Context initCtx = new InitialContext();
                final Context envCtx = (Context) initCtx.lookup("java:comp/env");
                final OrientGraphPool pool = (OrientGraphPool) envCtx.lookup(orientGraphPool);
                pool.removeAll(principal.getDbUrl(), principal.getName());
                session.removeAttribute(OdbPrincipal.class.getName()); // Probably redundant really
            } catch (final NamingException e) {
                log.throwing(OdbSessionListener.class.getName(), "sessionDestroyed(HttpSessionEvent)", e);
                log.severe(e.getMessage());
            }
        }
    }
}

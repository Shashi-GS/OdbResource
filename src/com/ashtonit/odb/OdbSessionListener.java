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
 * This class cleans up old OrientGraph instance associated with a user when that user logs out.
 * 
 * @author Bruce Ashton
 * @date 21/07/2015
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
     * Gets the JNDI name of the graph database pool instance relative to, "java:comp/env".
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

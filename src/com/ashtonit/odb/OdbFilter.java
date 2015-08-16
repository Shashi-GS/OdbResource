package com.ashtonit.odb;

import java.io.IOException;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.ashtonit.odb.pool.OrientGraphPool;
import com.ashtonit.odb.realm.OdbPrincipal;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;


/**
 * TODO
 *
 * @author Bruce Ashton
 * @date 2/07/2014
 */
public class OdbFilter implements Filter {

    static final String ORIENT_GRAPH_POOL = "orientGraphPool";

    private static final Logger log = Logger.getLogger(OdbFilter.class.getName());

    private static final String ORIENT_GRAPH = "orientGraph";

    private String orientGraphKey;
    private OrientGraphPool pool;


    /**
     * Does nothing.
     *
     * @see Filter#destroy()
     */
    @Override
    public void destroy() {
    }


    /**
     * Get the database parameters from the user principal, create an OrientGraph object and set it as an attribute on
     * the request. Clean up the OrientGraph object once the Servlet service method has completed. Set the principal as
     * an attribute on the session so we can clean up database connections later.
     *
     * @param request the HTTP Servlet request object
     * @param response the HTTP Servlet response object
     * @param chain the chain object for the chain of command pattern.
     * @throws IOException might be thrown by chain.doFilter()
     * @throws ServletException might be thrown by chain.doFilter()
     * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
     */
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final OdbPrincipal principal = (OdbPrincipal) httpRequest.getUserPrincipal();
        if (principal != null) {
            final HttpSession session = httpRequest.getSession();
            if (session.getAttribute(OdbPrincipal.class.getName()) == null) {
                session.setAttribute(OdbPrincipal.class.getName(), principal);
            }
            OrientGraph graph = null;
            try {
                graph = pool.get(principal.getDbUrl(), principal.getName(), principal.getPassword());
                request.setAttribute(orientGraphKey, graph);
                chain.doFilter(request, response);
            } finally {
                if (graph != null && !graph.isClosed()) {
                    graph.shutdown();
                }
            }
        } else {
            httpRequest.logout();
        }
    }


    /**
     * Set the database URL.
     *
     * @param config the container object for filter parameters
     * @see Filter#init(FilterConfig)
     */
    @Override
    public void init(final FilterConfig config) {
        try {
            orientGraphKey = config.getServletContext().getInitParameter(ORIENT_GRAPH);
            final String orientGraphPoolKey = config.getServletContext().getInitParameter(ORIENT_GRAPH_POOL);
            final Context initCtx = new InitialContext();
            final Context envCtx = (Context) initCtx.lookup("java:comp/env");
            pool = (OrientGraphPool) envCtx.lookup(orientGraphPoolKey);
        } catch (final NamingException e) {
            log.throwing(OdbFilter.class.getName(), "init(FilterConfig)", e);
            log.severe(e.getMessage());
        }
    }
}

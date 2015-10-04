package com.ashtonit.odb;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.ashtonit.odb.realm.OdbPrincipal;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;


/**
 * <p>
 * This class is responsible for obtaining an {@link OrientGraph} instance from {@link OrientGraphPool} and setting it
 * as an attribute on the request object.
 * </p>
 * <p>
 * It pulls two context parameters from the servlet context;
 * </p>
 * <p>
 * <strong>orientGraph</strong> is used to set the name of the request attribute that will contain the instance of
 * {@link OrientGraph} as its value.
 * </p>
 * <p>
 * <strong>orientGraphPool</strong> tells this OdbFilter instance the JNDI name of the graph database pool instance
 * relative to, "java:comp/env". It must match your resource configuration.
 * </p>
 * <p>
 * Both of these values can be configured in the web.xml file with a context-param element, as below;
 *
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;orientGraph&lt;/param-name&gt;
 *     &lt;param-value&gt;graph&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * 
 * &lt;context-param&gt;
 *     &lt;param-name&gt;orientGraphPool&lt;/param-name&gt;
 *     &lt;param-value&gt;odbp&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 *
 * @author Bruce Ashton
 * @date 2/07/2014
 */
public class OdbFilter implements Filter {

    private static final Logger log = Logger.getLogger(OdbFilter.class.getName());

    private static final String ORIENT_GRAPH = "orientGraph";
    private static final String ORIENT_POOL = "orientPool";
    private static final String ORIENT_POOL_CAPACITY = "orientPoolCapacity";

    private OPartitionedDatabasePoolFactory factory;
    private String orientGraph;
    private String orientPool;


    /**
     * Does nothing.
     *
     * @see Filter#destroy()
     */
    @Override
    public void destroy() {
        log.fine("destroy()");
    }


    /**
     * <p>
     * Get the database parameters from the user principal, create an {@link OrientGraph} object and set it as an
     * attribute on the request. Clean up the {@link OrientGraph} object once the Servlet service method has completed.
     * Set the principal as an attribute on the session so we can clean up database connections later.
     * </p>
     * <p>
     * It uses the <strong>orientGraph</strong> and <strong>orientGraphPool</strong> context parameters.
     * </p>
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
        log.fine("doFilter()");
        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final OdbPrincipal principal = (OdbPrincipal) httpRequest.getUserPrincipal();
        log.fine("doFilter(): principal=" + principal);
        if (principal != null) {
            OrientGraph graph = null;
            try {
                final HttpSession session = httpRequest.getSession();
                OPartitionedDatabasePool pool = (OPartitionedDatabasePool) session.getAttribute(ORIENT_POOL);
                if (pool == null) {
                    log.fine("doFilter(): pool = factory.get()");
                    pool = factory.get(principal.getDbUrl(), principal.getName(), principal.getPassword());
                }
                graph = new OrientGraph(pool.acquire());
                request.setAttribute(orientGraph, graph);
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
     * <p>
     * This method pulls two context parameters from the servlet context;
     * </p>
     * <p>
     * <strong>orientGraph</strong> is used to set the name of the session attribute that will contain the instance of
     * {@link OrientGraph} as its value.
     * </p>
     * <p>
     * <strong>orientGraphPool</strong> sets the JNDI name of the graph database pool instance relative to,
     * "java:comp/env".
     * </p>
     * <p>
     * Both of these values can be configured in the web.xml file with a context-param element, as below;
     *
     * <pre>
     * &lt;context-param&gt;
     *     &lt;param-name&gt;orientGraph&lt;/param-name&gt;
     *     &lt;param-value&gt;graph&lt;/param-value&gt;
     * &lt;/context-param&gt;
     * 
     * &lt;context-param&gt;
     *     &lt;param-name&gt;orientGraphPool&lt;/param-name&gt;
     *     &lt;param-value&gt;odbp&lt;/param-value&gt;
     * &lt;/context-param&gt;
     * </pre>
     *
     * @param config the container object for filter parameters
     * @see Filter#init(FilterConfig)
     */
    @Override
    public void init(final FilterConfig config) {
        log.fine("init()");

        final String orientGraph = config.getServletContext().getInitParameter(ORIENT_GRAPH);
        if (orientGraph != null) {
            this.orientGraph = orientGraph;
        }
        log.fine("init(): orientGraph=" + this.orientGraph);

        final String orientPool = config.getServletContext().getInitParameter(ORIENT_POOL);
        if (orientPool != null) {
            this.orientPool = orientPool;
        }
        log.fine("init(): orientPool=" + this.orientPool);

        final String capacityStr = config.getServletContext().getInitParameter(ORIENT_POOL_CAPACITY);
        int capacity = 100;
        if (capacityStr != null) {
            capacity = Integer.parseInt(capacityStr);
        }
        log.fine("init(): capacity=" + capacity);

        factory = new OPartitionedDatabasePoolFactory(capacity);
    }
}

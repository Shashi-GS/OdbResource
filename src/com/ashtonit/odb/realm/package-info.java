/**
 * Contains the Tomcat realm implementation and the {@link OdbPrincipal} class.
 * <p>
 * {@link OdbRealm} is configured in the web application or server context. {@link OdbPrincipal} is required for
 * {@link OrientGraphPool} to work.
 * </p>
 * 
 * @author Bruce Ashton
 */
package com.ashtonit.odb.realm;

import com.ashtonit.odb.pool.OrientGraphPool;

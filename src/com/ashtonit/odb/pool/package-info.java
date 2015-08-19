/**
 * Contains the classes that implement the {@link OrientGraph} pool.
 * <p>
 * {@link OrientGraphPool} and {@link OrientGraphPoolFactory} are referenced when configuring the pool as a resource
 * in the web application or server context. {@link OrientGraphPool} can be looked up via a JNDI naming service.
 * </p>
 * 
 * @author Bruce Ashton
 */
package com.ashtonit.odb.pool;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;

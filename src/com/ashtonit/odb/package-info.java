/**
 * Contains the classes used to manage an {@link OrientGraph} pool in a web application.
 * <p>
 * {@link OdbFilter} takes care of obtaining an {@link OrientGraph} instance from the pool at the start of a request
 * and releasing it again at the end of request processing.
 * </p>
 * <p>
 * {@link OdbSessionListener} cleans up pooled instances associated with a user when that user logs out.
 * </p>
 * 
 * @author Bruce Ashton
 */
package com.ashtonit.odb;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;

/**
 * Contains one class used to manage {@link OPartitionedDatabasePool} pools of {@link OrientGraph} instances in a
 * Tomcat web application.
 * <p>
 * {@link OPDPFObjectFactory} implements {@link ObjectFactory} and allows lookup of an
 * {@link OPartitionedDatabasePoolFactory} instance from a JNDI service.
 * </p>
 * 
 * @author Bruce Ashton
 */
package com.ashtonit.odb;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;

import javax.naming.spi.ObjectFactory;



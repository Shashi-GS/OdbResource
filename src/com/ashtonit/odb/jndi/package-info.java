/**
 * Contains one class which provides a factory to look up {@link OPartitionedDatabasePool} pools of OrientDB instances
 * via a JNDI service.
 * <p>
 * {@link OPDPFObjectFactory} implements {@link ObjectFactory} and allows lookup of an
 * {@link OPartitionedDatabasePoolFactory} instance from a JNDI service.
 * </p>
 * 
 * @author Bruce Ashton
 */
package com.ashtonit.odb.jndi;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;

import javax.naming.spi.ObjectFactory;



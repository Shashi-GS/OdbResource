/**
 * This package provides two classes that extend {@link ObjectFactory} for use with a JNDI service.
 * {@link OPDPFObjectFactory} provides an {@link OPartitionedDatabasePoolFactory} singleton instance and
 * {@link OServerObjectFactory} provides an {@link OServer} singleton instance.
 * <p>
 * If running <a href="http://orientdb.com/">OrientDB</a> embedded, it is important to declare the OServer resource and
 * configure {@link OServer#shutdown()} as the close method. This allows the database to be taken down cleanly whenever
 * the web application is redeployed or stopped. 
 * </p>
 * 
 * @author Bruce Ashton
 */
package com.ashtonit.odb.jndi;

import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.server.OServer;

import javax.naming.spi.ObjectFactory;



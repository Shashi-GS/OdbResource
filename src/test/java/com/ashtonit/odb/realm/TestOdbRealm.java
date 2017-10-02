package com.ashtonit.odb.realm;

import java.io.File;
import java.io.IOException;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Tomcat;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ashtonit.odb.jndi.OPDPFObjectFactory;
import com.ashtonit.odb.jndi.OServerObjectFactory;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePoolFactory;
import com.orientechnologies.orient.server.OServer;


public class TestOdbRealm {

    private static final String BASIC = "BASIC";
    private static final String CLOSE = "close";
    private static final String CONFIG_FILE = "configFile";
    private static final String CONTAINER = "Container";
    private static final String DB_URL = ""; // TODO
    private static final String EMBEDDED = "Embedded";
    private static final String FACTORY = "factory";
    private static final String HOST = "localhost";
    private static final String OPDP_FACTORY = "opdpfactory";
    private static final String ORIENTDB_SERVER_CONFIG = "src/test/resources/orientdb-server-config.xml";
    private static final String OSERVER = "oserver";
    private static final String PASSWORD = "passsword";
    private static final String QUERY = "SELECT password, roles.name AS roles FROM OUser WHERE status = 'ACTIVE' AND name = ?";
    private static final String ROLE = "admin";
    private static final String ROOT = "root";
    private static final String SERVER = "server";
    private static final String SHUTDOWN = "shutdown";
    private static final int PORT = 8080;

    private static final String URL = "http://" + HOST + ":" + PORT + "/";

    private static final TestResponseHandler handler = new TestResponseHandler();

    private static Tomcat tomcat;


    @AfterClass
    public static void afterClass() throws LifecycleException {
        tomcat.stop();
    }


    @BeforeClass
    public static void beforeClass() throws LifecycleException {
        tomcat = new Tomcat();
        tomcat.enableNaming();
        tomcat.setHostname(HOST);
        tomcat.setPort(PORT);
        tomcat.getConnector();

        final Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());

        // TODO Why is this not working
        final LoginConfig config = new LoginConfig();
        config.setAuthMethod(BASIC);
        ctx.setLoginConfig(config);
        ctx.addSecurityRole(ROLE);
        final SecurityConstraint constraint = new SecurityConstraint();
        constraint.addAuthRole(ROLE);
        final SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");
        constraint.addCollection(collection);
        ctx.addConstraint(constraint);

        Tomcat.addServlet(ctx, EMBEDDED, new TestServlet());
        ctx.addServletMappingDecoded("/", EMBEDDED);

        final Realm realm = getRealm(); 
        ctx.setRealm(realm);
//        tomcat.getEngine().setRealm(realm);

        final Server server = tomcat.getServer();
        final NamingResourcesImpl impl = server.getGlobalNamingResources();
        impl.addResource(getOServer());
        impl.addResource(getOpdpFactory());

        server.getCatalina();

        tomcat.start();
    }


    private static final ContextResource getOpdpFactory() {
        final ContextResource opdpFactory = new ContextResource();
        opdpFactory.setAuth(CONTAINER);
        opdpFactory.setCloseMethod(CLOSE);
        opdpFactory.setName(OPDP_FACTORY);
        opdpFactory.setSingleton(true);
        opdpFactory.setType(OPartitionedDatabasePoolFactory.class.getName());
        opdpFactory.setProperty(FACTORY, OPDPFObjectFactory.class.getName());
        opdpFactory.setProperty(SERVER, OSERVER);
        return opdpFactory;
    }


    private static final ContextResource getOServer() {
        final ContextResource opdpFactory = new ContextResource();
        opdpFactory.setAuth(CONTAINER);
        opdpFactory.setCloseMethod(SHUTDOWN);
        opdpFactory.setName(OSERVER);
        opdpFactory.setSingleton(true);
        opdpFactory.setType(OServer.class.getName());
        opdpFactory.setProperty(CONFIG_FILE, ORIENTDB_SERVER_CONFIG);
        opdpFactory.setProperty(FACTORY, OServerObjectFactory.class.getName());
        return opdpFactory;
    }


    private static Realm getRealm() {
        final OdbRealm realm = new OdbRealm();
        realm.setDbUser(ROOT);
        realm.setDbPass(PASSWORD);
        realm.setDbResource(OPDP_FACTORY);
        realm.setDbUrl(DB_URL);
        realm.setQuery(QUERY);
        return realm;
    }


    @Before
    public void setUp() {
        // TODO
        Thread.yield();
    }


    @After
    public void tearDown() {
        // TODO
        Thread.yield();
    }


    @Test
    public void testSomething() throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpGet get = new HttpGet(URL);

            final String response = client.execute(get, handler);
            System.out.println(response);
            // TODO
        }
    }
}

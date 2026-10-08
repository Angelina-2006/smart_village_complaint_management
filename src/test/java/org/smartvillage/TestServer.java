package org.smartvillage;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;
import org.apache.catalina.webresources.*;
import java.nio.file.*;

final class TestServer {
    static Tomcat start(int port, String database, String adminPassword) throws Exception {
        System.setProperty("DB_URL",database); System.setProperty("DB_USER","sa"); System.setProperty("DB_PASSWORD","");
        System.setProperty("ADMIN_EMAIL","admin@village.local"); System.setProperty("ADMIN_PASSWORD",adminPassword);
        Tomcat tomcat=new Tomcat(); tomcat.setBaseDir(Files.createTempDirectory(Path.of("target"),"tomcat-").toString());
        tomcat.setPort(port); tomcat.getConnector().setProperty("address","127.0.0.1");
        Context context=tomcat.addWebapp("",Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(TestServer.class.getClassLoader());
        StandardRoot resources=new StandardRoot(context);
        resources.addPreResources(new DirResourceSet(resources,"/WEB-INF/classes",Path.of("target/classes").toAbsolutePath().toString(),"/"));
        context.setResources(resources); tomcat.start(); return tomcat;
    }
}

package org.smartvillage;
import org.apache.catalina.startup.Tomcat;
import java.nio.file.*;
/** Optional local preview using a persistent H2 database, never included in the WAR. */
public final class DemoServer {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(".tools/demo"));
        Tomcat tomcat=TestServer.start(8080,"jdbc:h2:file:"+Path.of(".tools/demo/village").toAbsolutePath().toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","LocalPreview123!");
        System.out.println("Local preview: http://localhost:8080 | admin@village.local / LocalPreview123!");
        Runtime.getRuntime().addShutdownHook(new Thread(()->{ try { tomcat.stop(); tomcat.destroy(); } catch(Exception ignored) {} }));
        tomcat.getServer().await();
    }
}

package org.smartvillage.web;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import org.smartvillage.db.*;
@WebListener
public final class Startup implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent event) {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); Database.initialize(); new UserRepository().bootstrapAdmin(); }
        catch(Exception e) { throw new IllegalStateException("Database startup failed. Check DB_* and ADMIN_* settings.",e); }
    }
}

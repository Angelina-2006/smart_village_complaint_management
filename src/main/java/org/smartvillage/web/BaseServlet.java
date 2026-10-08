package org.smartvillage.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.smartvillage.model.User;
import java.io.IOException;

/** Shared abstract controller; concrete servlets override HTTP methods polymorphically. */
public abstract class BaseServlet extends HttpServlet {
    protected User user(HttpServletRequest req) { return (User)req.getSession().getAttribute("user"); }
    protected boolean requireUser(HttpServletRequest req,HttpServletResponse res) throws IOException {
        if(user(req)!=null) return true; redirect(req,res,"/login"); return false;
    }
    protected void view(HttpServletRequest req,HttpServletResponse res,String name) throws ServletException,IOException { req.getRequestDispatcher("/WEB-INF/views/"+name+".jsp").forward(req,res); }
    protected void redirect(HttpServletRequest req,HttpServletResponse res,String path) throws IOException { res.sendRedirect(req.getContextPath()+path); }
    protected String field(HttpServletRequest req,String name,int min,int max) {
        String value=req.getParameter(name); value=value==null?"":value.trim();
        if(value.length()<min||value.length()>max) throw new IllegalArgumentException(name+" must contain "+min+"–"+max+" characters."); return value;
    }
}

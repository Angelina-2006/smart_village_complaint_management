package org.smartvillage.web;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.smartvillage.db.UserRepository;
import org.smartvillage.model.User;
import java.io.IOException;
import java.sql.SQLException;
@WebServlet(urlPatterns={"/login","/register","/logout"})
public final class AuthServlet extends BaseServlet {
    private final UserRepository users=new UserRepository();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        if(user(req)!=null) { redirect(req,res,"/dashboard"); return; } view(req,res,"auth");
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        if(req.getServletPath().equals("/logout")) { req.getSession().invalidate(); redirect(req,res,"/login"); return; }
        try {
            String email=field(req,"email",3,190);
            if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Enter a valid email address.");
            String password=req.getParameter("password");
            if(password==null || password.length()<8 || password.length()>128) throw new IllegalArgumentException("Password must contain 8–128 characters.");
            if(req.getServletPath().equals("/register")) users.create(field(req,"name",2,100),email,password,"VILLAGER");
            User user=users.authenticate(email,password);
            if(user==null) throw new IllegalArgumentException("Email or password is incorrect.");
            req.changeSessionId(); req.getSession().setAttribute("user",user); req.getSession().setAttribute("csrf",java.util.UUID.randomUUID().toString()); redirect(req,res,"/dashboard");
        } catch(IllegalArgumentException e) { req.setAttribute("error",e.getMessage()); view(req,res,"auth"); }
        catch(SQLException e) {
            getServletContext().log("Account operation failed",e);
            req.setAttribute("error",e.getSQLState()!=null&&e.getSQLState().startsWith("23")?"That email is already registered. Please sign in.":"The account service is unavailable. Please try again."); view(req,res,"auth");
        }
    }
}

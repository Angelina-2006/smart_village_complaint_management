package org.smartvillage.web;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
@WebFilter("/*")
public final class SecurityFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException {
        HttpServletRequest req=(HttpServletRequest)request; HttpServletResponse res=(HttpServletResponse)response;
        req.setCharacterEncoding("UTF-8"); res.setCharacterEncoding("UTF-8");
        res.setHeader("X-Content-Type-Options","nosniff"); res.setHeader("X-Frame-Options","DENY");
        res.setHeader("Referrer-Policy","same-origin");
        res.setHeader("Content-Security-Policy","default-src 'self'; img-src 'self' blob:; style-src 'self'; script-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
        if(!req.getRequestURI().startsWith(req.getContextPath()+"/assets/")) {
            res.setHeader("Cache-Control","no-store");
            HttpSession session=req.getSession();
            if(session.getAttribute("csrf")==null) session.setAttribute("csrf",UUID.randomUUID().toString());
            if("POST".equals(req.getMethod())) {
                String token;
                try { token=req.getParameter("csrf"); } catch(IllegalStateException e) { res.sendError(413,"Upload is too large. Maximum photo size is 5 MB."); return; }
                if(!session.getAttribute("csrf").equals(token)) { res.sendError(403,"This form expired. Refresh the page and try again."); return; }
            }
        }
        chain.doFilter(req,res);
    }
}

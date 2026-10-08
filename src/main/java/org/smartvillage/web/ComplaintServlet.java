package org.smartvillage.web;
import jakarta.servlet.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import org.smartvillage.db.ComplaintRepository;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@WebServlet(urlPatterns={"/dashboard","/complaints/new","/complaints/view","/complaints/update","/complaints/feedback","/complaints/photo"})
@MultipartConfig(maxFileSize=5242880,maxRequestSize=5500000,fileSizeThreshold=1048576)
public final class ComplaintServlet extends BaseServlet {
    private final ComplaintRepository repository=new ComplaintRepository();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        if(!requireUser(req,res)) return;
        try {
            switch(req.getServletPath()) {
                case "/dashboard" -> {
                    String status=field(req,"status",0,20),search=field(req,"q",0,150);
                    if(!status.isEmpty()&&!ComplaintRepository.STATUSES.contains(status)) throw new IllegalArgumentException("Invalid status filter.");
                    req.setAttribute("complaints",repository.list(user(req),status,search)); req.setAttribute("counts",repository.counts(user(req))); view(req,res,"dashboard");
                }
                case "/complaints/new" -> view(req,res,"new");
                case "/complaints/view", "/complaints/photo" -> {
                    String id=field(req,"id",1,40); Map<String,Object> complaint=repository.find(user(req),id);
                    if(complaint==null) { res.sendError(404); return; }
                    if(req.getServletPath().endsWith("photo")) {
                        byte[] photo=repository.photo(user(req),id); if(photo==null) { res.sendError(404); return; }
                        res.setContentType((String)complaint.get("photo_type")); res.setContentLength(photo.length); res.getOutputStream().write(photo); return;
                    }
                    req.setAttribute("complaint",complaint); req.setAttribute("history",repository.history(id)); req.setAttribute("feedback",repository.feedback(id)); view(req,res,"detail");
                }
                default -> res.sendError(405);
            }
        } catch(IllegalArgumentException e) { res.sendError(400,e.getMessage()); }
        catch(SQLException e) { getServletContext().log("Complaint read failed",e); res.sendError(503); }
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        if(!requireUser(req,res)) return;
        String path=req.getServletPath();
        try {
            String id;
            switch(path) {
                case "/complaints/new" -> {
                    String title=field(req,"title",5,150),category=field(req,"category",1,40),description=field(req,"description",20,5000),location=field(req,"location",5,250);
                    if(!ComplaintRepository.CATEGORIES.contains(category)) throw new IllegalArgumentException("Choose a valid category.");
                    Double lat=coordinate(req,"latitude",90),lon=coordinate(req,"longitude",180);
                    if((lat==null)!=(lon==null)) throw new IllegalArgumentException("Provide both latitude and longitude.");
                    byte[] photo=null; String type=null; Part part=req.getPart("photo");
                    if(part!=null && part.getSize()>0) {
                        try(InputStream in=part.getInputStream()) { photo=in.readNBytes(5242881); }
                        if(photo.length>5242880) throw new IllegalArgumentException("Photo must be 5 MB or smaller.");
                        type=validatePhoto(photo);
                    }
                    id=repository.create(user(req),title,category,description,location,lat,lon,photo,type);
                    req.getSession().setAttribute("flash","Complaint registered. Save your complaint ID to track progress.");
                }
                case "/complaints/update" -> {
                    if(!user(req).isAdmin()) { res.sendError(403); return; }
                    id=field(req,"id",1,40);
                    repository.update(user(req),id,field(req,"status",1,20),"on".equals(req.getParameter("verified")),field(req,"assignee",0,100),field(req,"note",5,700));
                    req.getSession().setAttribute("flash","Complaint updated and the progress history saved.");
                }
                case "/complaints/feedback" -> {
                    id=field(req,"id",1,40); int rating;
                    try { rating=Integer.parseInt(field(req,"rating",1,1)); } catch(NumberFormatException e) { throw new IllegalArgumentException("Choose a rating from 1 to 5."); }
                    repository.addFeedback(user(req),id,rating,field(req,"comment",5,1000)); req.getSession().setAttribute("flash","Thank you. Your feedback has been recorded.");
                }
                default -> { res.sendError(405); return; }
            }
            redirect(req,res,"/complaints/view?id="+id);
        } catch(IllegalArgumentException|IllegalStateException e) { failure(req,res,path,e.getMessage()); }
        catch(SQLException e) { getServletContext().log("Complaint write failed",e); failure(req,res,path,e.getSQLState()!=null&&e.getSQLState().startsWith("23")?"Feedback has already been submitted.":"Unable to save right now. Please try again."); }
    }
    private void failure(HttpServletRequest req,HttpServletResponse res,String path,String message) throws ServletException,IOException {
        if(path.equals("/complaints/new")) { req.setAttribute("error",message); view(req,res,"new"); }
        else {
            req.getSession().setAttribute("error",message);
            String id=req.getParameter("id");
            redirect(req,res,id!=null&&id.matches("SV-[A-F0-9-]{36}")?"/complaints/view?id="+id:"/dashboard");
        }
    }
    private Double coordinate(HttpServletRequest req,String name,int max) {
        String value=field(req,name,0,30); if(value.isEmpty()) return null;
        try { double number=Double.parseDouble(value); if(!Double.isFinite(number)||Math.abs(number)>max) throw new NumberFormatException(); return number; }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Invalid "+name+"."); }
    }
    private String validatePhoto(byte[] bytes) throws IOException {
        try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext()) throw new IllegalArgumentException("Upload a valid JPEG or PNG photo.");
            ImageReader reader=readers.next();
            try {
                reader.setInput(in); String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!List.of("jpeg","png").contains(format)||reader.getWidth(0)>8000||reader.getHeight(0)>8000||((long)reader.getWidth(0)*reader.getHeight(0)>24000000)) throw new IllegalArgumentException("Use a JPEG or PNG photo of at most 24 megapixels and 8000 pixels per side.");
                if(reader.read(0)==null) throw new IllegalArgumentException("Unable to read this photo.");
                return "image/"+format;
            } catch(javax.imageio.IIOException e) { throw new IllegalArgumentException("The photo is damaged. Choose another JPEG or PNG."); }
            finally { reader.dispose(); }
        }
    }
}

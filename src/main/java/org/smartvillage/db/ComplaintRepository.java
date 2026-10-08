package org.smartvillage.db;

import org.smartvillage.model.User;
import java.sql.*;
import java.util.*;

/** JDBC boundary: prepared statements, ownership checks and atomic history writes. */
public final class ComplaintRepository {
    public static final List<String> CATEGORIES = List.of("Water Supply", "Roads & Transport", "Electricity", "Sanitation", "Public Facilities", "Other");
    public static final List<String> STATUSES = List.of("Pending", "Under Process", "Resolved");
    private static final String COLUMNS = "c.id,c.user_id,c.title,c.category,c.description,c.location,c.latitude,c.longitude,c.status,c.verified,c.assignee,c.created_at,c.updated_at,c.photo_type,u.name AS reporter";
    public List<Map<String,Object>> list(User user, String status, String search) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM complaints c JOIN users u ON c.user_id=u.id WHERE (?=TRUE OR c.user_id=?) AND (?='' OR c.status=?) AND (?='' OR LOWER(c.title) LIKE ? OR LOWER(c.id) LIKE ?) ORDER BY c.created_at DESC LIMIT 200";
        try (Connection c = Database.connect(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setBoolean(1,user.isAdmin()); s.setLong(2,user.getId()); s.setString(3,status); s.setString(4,status); s.setString(5,search);
            s.setString(6,"%"+search.toLowerCase(Locale.ROOT)+"%"); s.setString(7,"%"+search.toLowerCase(Locale.ROOT)+"%");
            return rows(s);
        }
    }
    public Map<String,Long> counts(User user) throws SQLException {
        Map<String,Long> result = new LinkedHashMap<>(); result.put("Total",0L); STATUSES.forEach(s -> result.put(s,0L));
        try (Connection c=Database.connect(); PreparedStatement s=c.prepareStatement("SELECT status,COUNT(*) AS total FROM complaints WHERE (?=TRUE OR user_id=?) GROUP BY status")) {
            s.setBoolean(1,user.isAdmin()); s.setLong(2,user.getId());
            try(ResultSet r=s.executeQuery()) { while(r.next()) { long n=r.getLong("total"); result.put(r.getString("status"),n); result.put("Total",result.get("Total")+n); } }
        } return result;
    }
    public Map<String,Object> find(User user, String id) throws SQLException {
        try(Connection c=Database.connect(); PreparedStatement s=c.prepareStatement("SELECT "+COLUMNS+" FROM complaints c JOIN users u ON c.user_id=u.id WHERE c.id=? AND (?=TRUE OR c.user_id=?)")) {
            s.setString(1,id); s.setBoolean(2,user.isAdmin()); s.setLong(3,user.getId());
            List<Map<String,Object>> result=rows(s); return result.isEmpty()?null:result.get(0);
        }
    }
    public String create(User user, String title, String category, String description, String location, Double lat, Double lon, byte[] photo, String type) throws SQLException {
        String id="SV-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        try(Connection c=Database.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement s=c.prepareStatement("INSERT INTO complaints(id,user_id,title,category,description,location,latitude,longitude,photo,photo_type) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                s.setString(1,id); s.setLong(2,user.getId()); s.setString(3,title); s.setString(4,category); s.setString(5,description); s.setString(6,location); s.setObject(7,lat); s.setObject(8,lon); s.setBytes(9,photo); s.setString(10,type); s.executeUpdate();
                event(c,id,user,"Pending","Complaint submitted. Awaiting Panchayat verification."); c.commit();
            } catch(SQLException e) { c.rollback(); throw e; }
        } return id;
    }
    public List<Map<String,Object>> history(String id) throws SQLException {
        try(Connection c=Database.connect(); PreparedStatement s=c.prepareStatement("SELECT e.status,e.note,e.created_at,u.name FROM complaint_events e JOIN users u ON e.actor_id=u.id WHERE complaint_id=? ORDER BY e.id DESC")) { s.setString(1,id); return rows(s); }
    }
    public Map<String,Object> feedback(String id) throws SQLException {
        try(Connection c=Database.connect(); PreparedStatement s=c.prepareStatement("SELECT rating,comment,created_at FROM feedback WHERE complaint_id=?")) { s.setString(1,id); List<Map<String,Object>> r=rows(s); return r.isEmpty()?null:r.get(0); }
    }
    public void update(User actor, String id, String status, boolean verified, String assignee, String note) throws SQLException {
        if (!actor.isAdmin()) throw new IllegalArgumentException("Administrator access required.");
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Choose a valid status.");
        if (!status.equals("Pending") && (!verified || assignee.isBlank())) throw new IllegalArgumentException("Verify and assign the complaint before processing or resolving it.");
        try(Connection c=Database.connect()) {
            c.setAutoCommit(false);
            try {
                String previous;
                try(PreparedStatement s=c.prepareStatement("SELECT status FROM complaints WHERE id=? FOR UPDATE")) {
                    s.setString(1,id); try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Complaint not found."); previous=r.getString(1); }
                }
                if (previous.equals("Resolved")) throw new IllegalArgumentException("Resolved complaints are closed.");
                if (previous.equals("Pending") && status.equals("Resolved")) throw new IllegalArgumentException("Move the complaint to Under Process before resolving it.");
                if (previous.equals("Under Process") && status.equals("Pending")) throw new IllegalArgumentException("A complaint under process cannot return to Pending.");
                try(PreparedStatement s=c.prepareStatement("UPDATE complaints SET status=?,verified=?,assignee=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
                    s.setString(1,status); s.setBoolean(2,verified); s.setString(3,assignee); s.setString(4,id); s.executeUpdate();
                }
                event(c,id,actor,status,note+" | Verified: "+(verified?"Yes":"No")+" | Assigned to: "+(assignee.isBlank()?"Unassigned":assignee)); c.commit();
            } catch(SQLException|RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public void addFeedback(User user, String id, int rating, String comment) throws SQLException {
        if(rating<1 || rating>5) throw new IllegalArgumentException("Choose a rating from 1 to 5.");
        try(Connection c=Database.connect(); PreparedStatement s=c.prepareStatement("INSERT INTO feedback(complaint_id,rating,comment) SELECT id,?,? FROM complaints WHERE id=? AND user_id=? AND status='Resolved'")) {
            s.setInt(1,rating); s.setString(2,comment); s.setString(3,id); s.setLong(4,user.getId());
            if(s.executeUpdate()!=1) throw new IllegalArgumentException("Feedback is available only for your resolved complaints.");
        }
    }
    public byte[] photo(User user, String id) throws SQLException {
        try(Connection c=Database.connect(); PreparedStatement s=c.prepareStatement("SELECT photo FROM complaints WHERE id=? AND (?=TRUE OR user_id=?)")) {
            s.setString(1,id); s.setBoolean(2,user.isAdmin()); s.setLong(3,user.getId());
            try(ResultSet r=s.executeQuery()) { return r.next()?r.getBytes(1):null; }
        }
    }
    private void event(Connection c,String id,User user,String status,String note) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT INTO complaint_events(complaint_id,actor_id,status,note) VALUES(?,?,?,?)")) { s.setString(1,id); s.setLong(2,user.getId()); s.setString(3,status); s.setString(4,note); s.executeUpdate(); }
    }
    private List<Map<String,Object>> rows(PreparedStatement s) throws SQLException {
        List<Map<String,Object>> result=new ArrayList<>();
        try(ResultSet r=s.executeQuery()) { ResultSetMetaData m=r.getMetaData(); while(r.next()) { Map<String,Object> row=new HashMap<>(); for(int i=1;i<=m.getColumnCount();i++) row.put(m.getColumnLabel(i).toLowerCase(Locale.ROOT),r.getObject(i)); result.add(row); } }
        return result;
    }
}

package org.smartvillage;

import org.junit.jupiter.api.*;
import org.smartvillage.db.*;
import org.smartvillage.model.User;
import org.smartvillage.security.Passwords;
import org.smartvillage.web.Html;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {
    private ComplaintRepository complaints;
    private User resident, other, admin;
    @BeforeEach void setup() throws Exception {
        System.setProperty("DB_URL","jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER","sa"); System.setProperty("DB_PASSWORD","");
        Database.initialize(); UserRepository users=new UserRepository();
        users.create("Resident One","one@example.com","password123","VILLAGER");
        users.create("Resident Two","two@example.com","password123","VILLAGER");
        users.create("Administrator","admin@example.com","password123","ADMIN");
        resident=users.authenticate("one@example.com","password123"); other=users.authenticate("two@example.com","password123"); admin=users.authenticate("admin@example.com","password123");
        complaints=new ComplaintRepository();
    }
    private String submit() throws SQLException { return complaints.create(resident,"Broken water pipe","Water Supply","A water pipe is leaking outside the school.","Outside the primary school",12.9,77.5,new byte[]{1,2,3},"image/png"); }
    @Test void completeLifecycleAndFeedback() throws Exception {
        String id=submit(); assertTrue(id.startsWith("SV-"));
        assertEquals("Pending",complaints.find(resident,id).get("status"));
        complaints.update(admin,id,"Under Process",true,"Water team","Repairs have started.");
        complaints.update(admin,id,"Resolved",true,"Water team","The pipe has been repaired.");
        complaints.addFeedback(resident,id,5,"Thank you for the quick repair.");
        assertEquals(5,((Number)complaints.feedback(id).get("rating")).intValue());
        assertEquals(3,complaints.history(id).size());
        assertEquals(1L,complaints.counts(resident).get("Resolved"));
        assertThrows(SQLException.class,()->complaints.addFeedback(resident,id,4,"A second submission."));
        assertThrows(IllegalArgumentException.class,()->complaints.update(admin,id,"Pending",false,"","Reopening attempt."));
    }
    @Test void enforcesOwnershipForDetailsListsAndPhotos() throws Exception {
        String id=submit(); assertNull(complaints.find(other,id)); assertNull(complaints.photo(other,id));
        assertTrue(complaints.list(other,"","").isEmpty()); assertEquals(0L,complaints.counts(other).get("Total"));
        assertNotNull(complaints.find(admin,id)); assertArrayEquals(new byte[]{1,2,3},complaints.photo(admin,id));
        assertThrows(IllegalArgumentException.class,()->complaints.update(resident,id,"Under Process",true,"Team","Unauthorized attempt."));
    }
    @Test void validatesTransitionsAndRollsBackHistory() throws Exception {
        String id=submit();
        assertThrows(IllegalArgumentException.class,()->complaints.update(admin,id,"Resolved",true,"Team","Skipping processing."));
        assertThrows(IllegalArgumentException.class,()->complaints.update(admin,id,"Under Process",false,"Team","Not verified yet."));
        assertThrows(IllegalArgumentException.class,()->complaints.update(admin,id,"Under Process",true,"","Not assigned yet."));
        assertThrows(IllegalArgumentException.class,()->complaints.addFeedback(resident,id,5,"Too early for feedback."));
        assertEquals("Pending",complaints.find(resident,id).get("status")); assertEquals(1,complaints.history(id).size());
        complaints.update(admin,id,"Under Process",true,"Team","Starting work.");
        assertThrows(IllegalArgumentException.class,()->complaints.update(admin,id,"Pending",false,"","Invalid reversal."));
    }
    @Test void rejectsNonOwnerFeedbackAndOutOfRangeRatings() throws Exception {
        String id=submit(); complaints.update(admin,id,"Under Process",true,"Team","Starting work."); complaints.update(admin,id,"Resolved",true,"Team","Finished work.");
        assertThrows(IllegalArgumentException.class,()->complaints.addFeedback(other,id,5,"Not my complaint."));
        assertThrows(IllegalArgumentException.class,()->complaints.addFeedback(resident,id,6,"Invalid rating."));
        assertNull(complaints.feedback(id));
    }
    @Test void searchUsesPreparedStatements() throws Exception {
        String id=submit(); assertEquals(1,complaints.list(resident,"Pending","water").size());
        assertEquals(1,complaints.list(resident,"",id).size());
        assertTrue(complaints.list(resident,"Resolved","").isEmpty());
        assertTrue(complaints.list(resident,"","' OR 1=1 --").isEmpty());
    }
    @Test void authenticationAndOutputEscaping() throws Exception {
        assertNull(new UserRepository().authenticate("one@example.com","incorrect"));
        assertNotNull(new UserRepository().authenticate("ONE@EXAMPLE.COM","password123"));
        String a=Passwords.hash("secret123"),b=Passwords.hash("secret123"); assertNotEquals(a,b); assertTrue(Passwords.verify("secret123",a)); assertFalse(Passwords.verify("wrong",a));
        assertEquals("&lt;script&gt;&amp;&quot;&#39;",Html.e("<script>&\"'"));
    }
}

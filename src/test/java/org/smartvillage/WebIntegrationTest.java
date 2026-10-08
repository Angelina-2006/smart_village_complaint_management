package org.smartvillage;
import org.junit.jupiter.api.*;
import org.apache.catalina.startup.Tomcat;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

class WebIntegrationTest {
    static Tomcat server; static String base;
    @BeforeAll static void start() throws Exception {
        server=TestServer.start(0,"jdbc:h2:mem:web;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","IntegrationAdmin123!");
        base="http://127.0.0.1:"+server.getConnector().getLocalPort();
    }
    @AfterAll static void stop() throws Exception { if(server!=null) { server.stop(); server.destroy(); } }
    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build(); }
    private HttpResponse<String> get(HttpClient c,String path) throws Exception { return c.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> post(HttpClient c,String path,Map<String,String> data) throws Exception {
        String body=data.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return c.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String html) { Matcher m=Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(html); assertTrue(m.find(),html); return m.group(1); }
    @Test void rendersJspAndCompletesHttpWorkflow() throws Exception {
        HttpClient resident=client(),admin=client(),outsider=client();
        assertEquals(302,get(resident,"/dashboard").statusCode());
        var register=get(resident,"/register"); assertEquals(200,register.statusCode()); assertTrue(register.body().contains("Join your community")); assertTrue(register.body().contains("A voice for every resident")); assertFalse(register.body().contains("â")); assertFalse(register.body().contains("Â·"));
        assertEquals(403,post(resident,"/register",Map.of("email","x@example.com")).statusCode());
        var signup=post(resident,"/register",Map.of("csrf",token(register.body()),"name","HTTP Resident","email","http@example.com","password","Resident123!")); assertEquals(302,signup.statusCode(),signup.body());
        var dashboard=get(resident,"/dashboard"); assertEquals(200,dashboard.statusCode(),dashboard.body()); assertTrue(dashboard.body().contains("A fresh start"));
        var form=get(resident,"/complaints/new"); assertEquals(200,form.statusCode());
        String boundary="SmartVillageTestBoundary";
        Map<String,String> fields=new LinkedHashMap<>(Map.of("csrf",token(form.body()),"title","Broken school streetlight","category","Electricity","description","The streetlight near our school has been broken for three days.","location","Main road near primary school"));
        StringBuilder multipart=new StringBuilder(); fields.forEach((k,v)->multipart.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(k).append("\"\r\n\r\n").append(v).append("\r\n")); multipart.append("--").append(boundary).append("--\r\n");
        var submit=resident.send(HttpRequest.newBuilder(URI.create(base+"/complaints/new")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(multipart.toString())).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(302,submit.statusCode(),submit.body()); String detailPath=submit.headers().firstValue("location").orElseThrow(); String id=detailPath.substring(detailPath.indexOf("id=")+3);
        var detail=get(resident,detailPath); assertEquals(200,detail.statusCode(),detail.body()); assertTrue(detail.body().contains("Awaiting verification"));
        var otherForm=get(outsider,"/register"); post(outsider,"/register",Map.of("csrf",token(otherForm.body()),"name","Other Resident","email","other-http@example.com","password","Resident123!"));
        assertEquals(404,get(outsider,detailPath).statusCode());
        assertEquals(403,post(resident,"/complaints/update",Map.of("csrf",token(detail.body()),"id",id)).statusCode());
        var login=get(admin,"/login"); assertEquals(302,post(admin,"/login",Map.of("csrf",token(login.body()),"email","admin@village.local","password","IntegrationAdmin123!")).statusCode());
        for(String status:List.of("Under Process","Resolved")) {
            var adminDetail=get(admin,detailPath); assertEquals(200,adminDetail.statusCode(),adminDetail.body());
            assertEquals(302,post(admin,"/complaints/update",Map.of("csrf",token(adminDetail.body()),"id",id,"status",status,"verified","on","assignee","Electrical team","note","Work update: "+status)).statusCode());
        }
        var resolved=get(resident,detailPath); assertTrue(resolved.body().contains("How did we do?"));
        assertEquals(302,post(resident,"/complaints/feedback",Map.of("csrf",token(resolved.body()),"id",id,"rating","5","comment","The repair was completed quickly.")).statusCode());
        assertTrue(get(admin,detailPath).body().contains("The repair was completed quickly."));
        assertEquals(200,get(resident,"/assets/app.css").statusCode());
        assertEquals(302,post(resident,"/logout",Map.of("csrf",token(resolved.body()))).statusCode());
        assertEquals(302,get(resident,"/dashboard").statusCode());
    }
}

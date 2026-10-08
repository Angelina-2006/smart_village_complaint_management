package org.smartvillage.web;
public final class Html {
    private Html() {}
    public static String e(Object value) {
        return value==null?"":value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    public static String badge(Object status) { return "Resolved".equals(status)?"resolved":"Under Process".equals(status)?"processing":"pending"; }
}

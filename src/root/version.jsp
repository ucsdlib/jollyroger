<%
    String version = application.getInitParameter("version-number");
    if ( version == null || version.trim().equals("") )
    {
        version = "0.0.0";
    }
    String build = application.getInitParameter("build-date");
    if ( build == null || build.trim().equals("") )
    {
        build = "unknown";
    }
    String msg = "Jolly Roger, Version " + version + ", Build " + build;
%><html>
    <head>
        <title><%=msg%></title>
    </head>
    <body>
        <p><%=msg%></p>
    </body>
</html>

<%@page import="java.util.*"%>
<html>
  <head>
    <title>jolly roger</title>
    <style type="text/css">
      body { background: #000000; color: #ffffff; font-family: sans-serif; }
      h1 { size: large; }
      h2 { size: medium; }
    </style>
  </head>
  <body>
    <h1>jolly roger</h1>
    <p>retrieve a record by:</p>
    <form action="get">
      <blockquote>
        <select name="type">
          <option value="bib">bib number</option>
          <option value="isbn">isbn</option>
          <option value="title">title</option>
        </select>
        <input type="text" name="value" value=""/>
        <input type="submit" value="go"/>
      </blockquote>
      <p>output:</p>
      <blockquote>
        <input type="radio" name="mods" value="false" checked="checked">
          marc<br/>
        <input type="radio" name="mods" value="true"> mods (requires namespaces)
      </blockquote>
      <p>use namespaces:</p>
      <blockquote>
        <input type="radio" name="ns" value="true" checked="checked"> yes<br/>
        <input type="radio" name="ns" value="false"> no
      </blockquote>
    </form>
    <h2>examples</h2>
    <ul>
      <li><a href="/jollyroger/get?type=bib&value=b3906609">bib number: b3906609</a></li>
      <li><a href="/jollyroger/get?type=isbn&value=0585202648">isbn: 0585202648</a></li>
      <li><a href="/jollyroger/get?type=title&value=franny+and+zooey">title: franny and zooey</a></li>
      <li><a href="version.jsp">version</a></li>
    </ul>
  </body>
</html>

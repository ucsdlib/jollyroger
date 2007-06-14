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
      <p>
        <select name="type">
          <option value="bib">bib number</option>
          <option value="isbn">isbn</option>
          <option value="title">title</option>
        </select>
        <input type="text" name="value" value=""/>
        <input type="submit" value="go"/>
      </p>
      <p>use namespaces:
        <input type="radio" name="ns" value="true" default> yes
        <input type="radio" name="ns" value="false"> no
      </p>
    </form>
    <h2>examples</h2>
    <ul>
      <li><a href="/jollyroger/get?type=bib&value=b3906609">bib number: b3906609</li>
      <li><a href="/jollyroger/get?type=isbn&value=0585202648">isbn: 0585202648</li>
      <li><a href="/jollyroger/get?type=title&value=franny+and+zooey">title: franny and zooey</li>
    </ul>
  </body>
</html>

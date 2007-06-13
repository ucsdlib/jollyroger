<%@page import="java.util.*"%>
<html>
  <head>
    <title>jolly roger</title>
    <style type="text/css">
      body { background: #000000; }
      h1 { size: large; }
      h1, p { color: #ffffff; font-family: sans-serif; }
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
</form>
</body>
</html>

package edu.ucsd.library.jollyroger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.Namespace;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;

/**
 * Servlet interface to III Catalog that converts output to MARCXML.
 * @author escowles
 * @see http://libxess.sourceforge.net/
 * Based on InnopacWeb.php v0.2 (2007-05-08) by David Walker
 * (http://xerxes.calstate.edu), licensed under GNU GPL 2 or later
 * (http://www.gnu.org/copyleft/gpl.html)
**/
public class JollyRoger extends HttpServlet
{
	private String baseURL = null;

	// declare namespaces
	private static final Namespace MARC_NS = new Namespace(
		"","http://www.loc.gov/MARC21/slim"
	);
	//private static final Namespace XSI_NS = new Namespace(
	//	"xsi","http://www.w3.org/2001/XMLSchema-instance"
	//);

	/**
	 * Servlet initialization.
	**/
	public void init( ServletConfig config )
	{
		ServletContext context = config.getServletContext();
		baseURL = context.getInitParameter("catalog-url");
	}

	/**
	 * Servlet GET implementation.
	**/
	public void doGet(HttpServletRequest request, HttpServletResponse response)
	{
		// get parameters
		String type = request.getParameter("type"); // { bib, isbn, title }
		String value = request.getParameter("value");

		if ( type == null || type.equals("") ||
				value == null || value.equals("") )
		{
			// complain about missing params
			sendError(
				response, response.SC_BAD_REQUEST,
				"Both type and value must be specified"
			);
		}

		// construct III query URL
		String query = "";
		if ( type.equals("bib") )
		{
			// "/search/.b1234567/.b1234567/1,1,1,B/detlmarc~1234567&FF=&1,0,"
			query = "/search/." + value + "/." + value + "/1,1,1,B/detlmarc~"
				+ value.substring(1) + "&FF=&1,0,";
		}
		else if ( type.equals("isbn") || type.equals("title") )
		{
			// "/search/i006073132X/i006073132X/1,1,1,E/marc&FF=i006073132X"
			value = type.substring(0,1) + value;
			value = value.replaceAll(" ","+");
			value = value.toLowerCase();
			query = "/search/" + value + "/" + value + "/1,1,1,E/marc&FF="
				+ value;
		}
		else
		{
			// complain about bogus type
			sendError(
				response, response.SC_BAD_REQUEST,
				"Type \"" + type + "\t not supported"
			);
		}

		// retrieve and parse content
		try
		{
			// get response
			String html = getContentFromURL( baseURL + query );

			// extract marc and convert to XML
			String marctext = html.substring(
				html.indexOf("<pre>")+5, html.indexOf("</pre>")
			);
			Document doc = convertToXml( marctext );

			// extract holdings and add to marcxml
			extractHoldings( html, doc.getRootElement() );

			// output XML
			response.setContentType("text/xml; charset=UTF-8");
			PrintWriter out = response.getWriter();
			output( doc, out );
		}
		catch ( Exception ex )
		{
			sendError(
				response, response.SC_INTERNAL_SERVER_ERROR, ex.toString()
			);
			ex.printStackTrace();
		}
	}

	/**
	 * Retrieve a URL and return the response body as a string.
	**/
	private static String getContentFromURL(String url) throws IOException
	{
		HttpClient client = new HttpClient();
		GetMethod getMethod = new GetMethod(url);
		String response = null;

		try
		{
			int statusCode = client.executeMethod(getMethod);
			if ( statusCode == HttpStatus.SC_OK )
			{
				response = new String(getMethod.getResponseBody());
			}
		}
		catch (HttpException he)
		{
			he.printStackTrace();
			throw new IOException(
				"Http error connecting to '" + url + "':" + he.getMessage()
			);
		}
		finally
		{
			getMethod.releaseConnection();
		}

		return response;
	}

	/**
	 * Convert tagged MARC to MARCXML.
	**/
	private Document convertToXml( String marctext ) throws IOException
	{

		Document doc = DocumentHelper.createDocument();
		Element record = DocumentHelper.createElement(
			new QName("record",MARC_NS)
		);
		doc.setRootElement(record);
		//record.addAttribute( new QName("schemaLocation", XSI_NS), "http://www.loc.gov/MARC21/slim http://www.loc.gov/standards/marcxml/schema/MARC21slim.xsd" );

		// wrap continuing lines
		marctext = marctext.replaceAll( "\\n       ", " " );
		
		BufferedReader buf = new BufferedReader(
			new StringReader(marctext.trim())
		);
		for ( String line = null; (line=buf.readLine()) != null; )
		{
            String tag  = line.substring( 0, 3 );
            String ind1 = line.substring( 4, 5 );
            String ind2 = line.substring( 5, 6 );
            String val  = line.substring( 7 ).trim();

			if ( tag.equals("LEA") )
			{
				Element leader = record.addElement(new QName("leader",MARC_NS));
				leader.setText(val);
			}
			else if ( Integer.parseInt(tag) <= 8 )
			{
				Element control = record.addElement(new QName("controlfield",MARC_NS));
				control.addAttribute("tag",tag);
				control.setText(val);
			}
			else
			{
				Element datafield = record.addElement(new QName("datafield",MARC_NS));
				datafield.addAttribute( "tag",  tag  );
				datafield.addAttribute( "ind1", ind1 );
				datafield.addAttribute( "ind2", ind2 );

                // if first character is not a pipe symbol, then this is the
				// default |a subfield so make that explicit for the array
				if ( !val.startsWith("|") )
				{
					val = "|a" + val;
				}

                // split the subfield data on the pipe and add them in using
				// the first character after the delimiter as the subfield code
				String[] subfields = val.split("\\|");
				for ( int i = 0; i < subfields.length; i++ )
				{
					if ( !subfields[i].equals("") )
					{
						String code = subfields[i].substring(0,1);
						String text = subfields[i].substring(1).trim();
						text = text.replaceAll("  "," ");
						subfield( datafield, code, text );
					}
				}
			}
		}

		return doc;
	}

	/**
	 * Parse III HTML and extract holdings info, and add it to the MARCXML.
	**/
	private void extractHoldings( String html, Element record )
	{
        int marker = -1;
        if ( html.indexOf("class=\"bibItems\">") != -1 )
        {
            // most library innopac systems
            marker = html.indexOf("class=\"bibItems\">");
        }
        else if ( html.indexOf("BGCOLOR=#DDEEFF>") != -1 )
        {
            // old innreach system
            marker = html.indexOf("BGCOLOR=#DDEEFF>");
        }
        else if ( html.indexOf("class=\"centralHolding") != -1 )
        {
            // newer innreach system
            marker = html.indexOf("class=\"centralHolding");
        }

        String table = html.substring(marker);
        table = table.substring( 0, table.indexOf("</table>") );

        while ( table.indexOf("<tr") != -1 )
        {
            // get the current row and remove it from main variable
            int rowMarker = table.indexOf("</tr>");
            String row = table.substring(0,rowMarker);
            table = table.substring(rowMarker+5);

            // make sure this isn't the header row
            if ( row.indexOf("<th") == -1 )
            {
                // remove header
                row = row.substring( row.indexOf("<td") );

                // remove markup
                row = row.replaceAll("<.+?>","");
                row = row.replaceAll("&nbsp;","");
                row = row.replaceAll("  "," ");

                // split into fields
                String[] fields = row.split("\\n+");

				// add to doc
				Element holding = record.addElement(
					new QName("datafield", MARC_NS)
				);
				holding.addAttribute("tag","852");
				subfield( holding, "b", fields[0].trim() );
				subfield( holding, "c", fields[1].trim() );
				subfield( holding, "z", fields[2].trim() );
            }
        }
	}
	private void subfield( Element elem, String code, String value )
	{
		Element subfield = elem.addElement( new QName("subfield",MARC_NS) );
		subfield.addAttribute( "code", code );
		subfield.setText( value );
	}

	private void output( Document doc, PrintWriter out )
	{
		out.println( doc.asXML() );
	}

	private void sendError( HttpServletResponse response, int code, String msg )
	{
		try
		{
			response.sendError( code, msg );
		}
		catch ( IOException ex2 )
		{
			ex2.printStackTrace();
		}
	}
}

package edu.ucsd.library.jollyroger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerException;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.Namespace;
import org.dom4j.io.DocumentSource;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

import org.apache.log4j.Logger;

import edu.ucsd.library.util.XsltUtil;

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
	private static Logger log = Logger.getLogger(JollyRoger.class);
	private String[] baseURLs = null;
	private static URL modsXslUrl = null;
	private static URL testXslUrl = null;

	// declare namespaces
	private static final Namespace MARC_NS = new Namespace(
		"","http://www.loc.gov/MARC21/slim"
	);
	private static final Namespace XSI_NS = new Namespace(
		"xsi","http://www.w3.org/2001/XMLSchema-instance"
	);

	/**
	 * Servlet initialization.
	**/
	public void init( ServletConfig config )
	{
		ServletContext context = config.getServletContext();
		baseURLs = context.getInitParameter("catalog-url").split(" ");
		try
		{
			modsXslUrl = context.getResource("/WEB-INF/MARC21slim2MODS.xsl");
			testXslUrl = context.getResource("/WEB-INF/test.xsl");
		}
		catch ( Exception ex )
		{
			log.warn("Unable to load stylesheets", ex );
		}
	}

	/**
	 * Servlet GET implementation.
	**/
	public void doGet(HttpServletRequest request, HttpServletResponse response)
	{
		// get parameters
		String type = request.getParameter("type"); // { bib, isbn, title }
		String value = request.getParameter("value");
		boolean useNS = getBoolean( request, "ns", true );
		boolean outputMods = getBoolean( request, "mods", false );
		boolean outputTest = getBoolean( request, "test", false );

		if ( type == null || type.equals("") ||
				value == null || value.equals("") )
		{
			// complain about missing params
			sendError(
				response, response.SC_BAD_REQUEST,
				"Both type and value must be specified"
			);
			return;
		}

		// construct III query URL
		String query = "";
		String roger = null;
		if ( type.equals("bib") )
		{
			if ( value.length() > 8 ) { value = value.substring(0,8); }
			roger = value;
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
			return;
		}

		// retrieve and parse content
		try
		{
			// get response
			String html = getContentFromURL( baseURLs, query );

			// extract marc and convert to XML
			int idx1 = html.indexOf("<pre>")+5;
			int idx2 = html.indexOf("</pre>");

			// error retrieving record
			if ( idx1 < 0 || idx2 < 0 || idx1 > idx2 || idx2 > html.length() )
			{
				sendError(
					response, response.SC_INTERNAL_SERVER_ERROR,
					"Error retrieving record"
				);
				return;
			}
			String marctext = html.substring( idx1, idx2 );
			Document doc = convertToXml( marctext, useNS );

			// extract holdings and add to marcxml
			extractHoldings( html, doc.getRootElement(), useNS );

			if ( outputTest )
			{
				// plaintext status output
				response.setContentType("text/plain");
			}
			else
			{
				// output XML
				response.setContentType("text/xml; charset=UTF-8");
			}
			PrintWriter out = response.getWriter();
			if ( outputMods )
			{
				// marc2mods output
				outputXsl( doc, modsXslUrl, roger, out );
			}
			else if ( outputTest )
			{
				// marc2mods output
				outputXsl( doc, testXslUrl, null, out );
			}
			else
			{
				// unprocessed marcxml
				output( doc, out );
			}
		}
		catch ( Exception ex )
		{
			sendError(
				response, response.SC_INTERNAL_SERVER_ERROR, ex.toString()
			);
			ex.printStackTrace();
			//log( "Error retrieving or formatting content", ex );
		}
	}

	/**
	 * Convert MARC XML to MODS and send to client.
	**/
	private static void outputXsl( Document doc, URL url, String roger,
		PrintWriter out ) throws IOException, TransformerException
	{
		StreamSource xsl = new StreamSource( url.toString() );
		DocumentSource xml = new DocumentSource(doc);
		StreamResult result = new StreamResult( out );
		Map<String,String> params = null;
		if ( roger != null )
		{
			params = new HashMap<>();
			params.put("roger",roger);
		}
		XsltUtil.xslt( params, xsl, xml, result );
	}

	/**
	 * Utility method to get a boolean value from a request parameter.
	**/
	private static boolean getBoolean( HttpServletRequest request, String param,
		boolean defaultValue )
	{

		// get string value
		String s = request.getParameter( param );
		if ( s != null && s.equals("false") )
		{
			return false;
		}
		else if ( s != null && s.equals("true") )
		{
			return true;
		}
		else
		{
			return defaultValue;
		}
	}

    /**
     * Accesses a URL and returns the content from that URL.
     *
     * @param url
     *            String representation of URL.
     * @throws IOException
     *             URL not available.
     * @return The content of the accessed URL.
     */
    public static String getContentFromURL(String[] base, String urlpart)
		throws IOException
    {
        HttpClient client = new HttpClient();
        StringBuffer response = null;
		GetMethod method = null;

		boolean success = false;
		for ( int i = 0; i < base.length && !success; i++ )
		{
        	try
        	{
        		method = new GetMethod( base[i] + urlpart );
            	int statusCode = client.executeMethod(method);
            	if ( statusCode == HttpStatus.SC_OK )
            	{
                	InputStream is = method.getResponseBodyAsStream();
                	if ( is != null )
                	{
                    	BufferedReader buf = new BufferedReader(
                        	new InputStreamReader(is)
                    	);
                    	response = new StringBuffer();
                    	for ( String line = null; (line=buf.readLine()) != null; )
                    	{
                        	response.append( line + "\n" );
                    	}
                	}
					success = true;
            	}
        	}
        	catch (HttpException he)
        	{
               	String msg = "Http error connecting to '" + method.getURI().toString();
            	log.warn( msg, he ); 
            	throw new IOException(
                	msg + "':" + he.getMessage()
            	);
        	}
        	catch (IOException ioex)
			{
				if ( (i+1) < base.length )
				{
					// if there are other servers to try, just report the error
					log.warn("Error retrieving " + base[i] + urlpart + ", trying other servers: " + ioex.toString());
				}
				else
				{
					// no more servers, just throw the exception
					throw ioex;
				}
			}
        	finally
        	{
            	method.releaseConnection();
        	}
		}

        if ( response == null )
        {
            return null;
        }
        else
        {
            return response.toString();
        }
    }

	/**
	 * Convert tagged MARC to MARCXML.
	**/
	private Document convertToXml( String marctext, boolean useNS )
		throws IOException
	{

		Document doc = DocumentHelper.createDocument();
		Element record = null;
		if ( useNS )
		{
			record = DocumentHelper.createElement(new QName("record",MARC_NS));
			record.addAttribute(
				new QName("schemaLocation", XSI_NS),
				"http://www.loc.gov/MARC21/slim " +
				"http://www.loc.gov/standards/marcxml/schema/MARC21slim.xsd"
			);
		}
		else
		{
			record = DocumentHelper.createElement("record");
		}
		doc.setRootElement(record);

		// wrap continuing lines
		marctext = marctext.replaceAll( "\\n       ", " " );
		
		BufferedReader buf = new BufferedReader(
			new StringReader(marctext.trim())
		);
		for ( String line = null; (line=buf.readLine()) != null; )
		{
			try
			{
            	String tag  = line.substring( 0, 3 );
            	String ind1 = line.substring( 4, 5 );
            	String ind2 = line.substring( 5, 6 );
            	String val  = line.substring( 7 ).trim();
	
				if ( tag.equals("LEA") )
				{
					Element leader = null;
					if ( useNS )
					{
						leader = record.addElement(new QName("leader",MARC_NS));
					}
					else
					{
						leader = record.addElement("leader");
					}
					leader.setText(val);
				}
				else if ( Integer.parseInt(tag) <= 8 )
				{
					Element control = null;
					if ( useNS )
					{
						control = record.addElement(new QName("controlfield",MARC_NS));
					}
					else
					{
						control = record.addElement("controlfield");
					}
					control.addAttribute("tag",tag);
					control.setText(val);
				}
				else
				{
					Element datafield = null;
					if ( useNS )
					{
						datafield = record.addElement(new QName("datafield",MARC_NS));
					}
					else
					{
						datafield = record.addElement("datafield");
					}
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
							subfield( datafield, code, text, useNS );
						}
					}
				}
			}
			catch ( Exception ex )
			{
				log.warn( "Error converting MARC text to XML format", ex );
			}
		}
	
		return doc;
	}

	/**
	 * Parse III HTML and extract holdings info, and add it to the MARCXML.
	**/
	private void extractHoldings( String html, Element record, boolean useNS )
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

		if ( marker != -1 )
		{
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
					Element holding = null;
					if ( useNS )
					{
						holding = record.addElement( new QName("datafield", MARC_NS) );
					}
					else
					{
						holding = record.addElement( "datafield" );
					}
					holding.addAttribute("tag","852");
					if ( fields != null && fields.length > 0 )
					{
						subfield( holding, "b", fields[0].trim(), useNS );
					}
					if ( fields != null && fields.length > 1 )
					{
						subfield( holding, "c", fields[1].trim(), useNS );
					}
					if ( fields != null && fields.length > 2 )
					{
						subfield( holding, "z", fields[2].trim(), useNS );
					}
            	}
            }
        }
	}

	/**
	 * Utility method to create a subfield element.
	**/
	private void subfield( Element elem, String code, String value,
		boolean useNS )
	{
		Element subfield = null;
		if ( useNS )
		{
			subfield = elem.addElement( new QName("subfield",MARC_NS) );
		}
		else
		{
			subfield = elem.addElement( "subfield" );
		}
		subfield.addAttribute( "code", code );
		subfield.setText( value );
	}

	/**
	 * Convert XML to text and send to client.
	**/
	private void output( Document doc, PrintWriter out )
	{
		out.println( doc.asXML() );
	}

	/**
	 * Send an error message to client.
	**/
	private void sendError( HttpServletResponse response, int code, String msg )
	{
		try
		{
			response.sendError( code, msg );
		}
		catch ( IOException ex2 )
		{
			log.warn("Error sending error message", ex2 );
		}
	}
}

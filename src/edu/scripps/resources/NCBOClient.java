package edu.scripps.resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;



public class NCBOClient {

	public static final String annotatorUrl = "http://rest.bioontology.org/obs/annotator";
	
	public final String apiKey;
	
	/**
	 * Returns a list of DOIDs associated with the text
	 * @param text to annotate
	 * @return
	 * @throws HttpResponseException if a problem occurs communicating with the annotator
	 */
    public Set<String> annotate(String text) throws HttpResponseException {
        HttpClient client = new DefaultHttpClient();
        
        HttpPost method = new HttpPost(annotatorUrl);

        List<NameValuePair> postParams = new ArrayList<NameValuePair>();
        
        // Configure the form parameters
        postParams.add(new BasicNameValuePair("longestOnly","false"));
        postParams.add(new BasicNameValuePair("wholeWordOnly","true"));
        postParams.add(new BasicNameValuePair("filterNumber", "true"));
        postParams.add(new BasicNameValuePair("stopWords",""));
        postParams.add(new BasicNameValuePair("withDefaultStopWords","true"));
        postParams.add(new BasicNameValuePair("isTopWordsCaseSensitive","false"));
        postParams.add(new BasicNameValuePair("mintermSize","3"));
        postParams.add(new BasicNameValuePair("scored", "true"));
        postParams.add(new BasicNameValuePair("withSynonyms","true")); 
        postParams.add(new BasicNameValuePair("ontologiesToExpand", "45769"));
        postParams.add(new BasicNameValuePair("ontologiesToKeepInResult", "45769")); 
        postParams.add(new BasicNameValuePair("isVirtualOntologyId", "false"));
        postParams.add(new BasicNameValuePair("semanticTypes", ""));
        postParams.add(new BasicNameValuePair("levelMax", "0"));
        postParams.add(new BasicNameValuePair("mappingTypes", "null")); //null, Automatic 
        postParams.add(new BasicNameValuePair("textToAnnotate", text));
        postParams.add(new BasicNameValuePair("format", "tabDelimited")); //Options are 'text', 'xml', 'tabDelimited'   
        postParams.add(new BasicNameValuePair("apikey", apiKey));
        
        ResponseHandler<String> handler = new BasicResponseHandler();
        HttpResponse res 	= null;
        String 		 tdf 	= "";
        try {
            method.setEntity(new UrlEncodedFormEntity(postParams));	
            res = client.execute(method);
        	tdf	= handler.handleResponse(res);
        } catch (HttpResponseException e) {
        	System.err.printf("Server returned error: %d : %s\n", 
        			res.getStatusLine().getStatusCode(), 
        			res.getStatusLine().getReasonPhrase());
        	if (res.getStatusLine().getStatusCode() == 403) {
        		System.err.println("Are you using a valid API key?");
        	}
        	throw new HttpResponseException(res.getStatusLine().getStatusCode(), e.getMessage());
        	
        } catch (ClientProtocolException e) {
			e.printStackTrace();
			return Collections.emptySet();
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptySet();
		} finally {
			client.getConnectionManager().shutdown();
		}

    	// Extract DOIDs and terms from the tab-delimited response
    	Set<String> doids = new HashSet<String>();
    	
    	String 	regex = "\t45769\\/" +			// matches the first tab and Disease Ontology identifier (45769)
    					"(DOID:[\\d]*)[\\s]" +	// matches the DOID of the form DOID:1909 (group 1)
    					"([\\w\\s^\\t]*)\t";	// matches the term associated with DOID  (group 2)
    	Matcher	match = Pattern.compile(regex).matcher(tdf);
    	
    	while (match.find()) {
    		String doid = 	match.group(1);
    		// Retrieves the term associated with the DOID if necessary
    		// String term = 	match.group(2); 
    		doids.add(doid);
    	}

    	return doids;

        
    }
    
    public NCBOClient() throws FileNotFoundException, IOException {

    	Properties props = new Properties();
    	props.load(new FileReader(new File("SyncService.conf")));
    	apiKey = props.getProperty("annotator.api.key");
    	
    }
    
    public NCBOClient(String apiKey) {
    	this.apiKey = apiKey;
    }
}


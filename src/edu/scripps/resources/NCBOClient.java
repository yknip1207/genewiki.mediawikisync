package edu.scripps.resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.genewiki.util.Serialize;


import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;



public class NCBOClient {

	public static final String annotatorUrl = "http://rest.bioontology.org/obs/annotator";
	
	public static final String URIPrefix = "http://purl.obolibrary.org/obo/";
	
	public final String apiKey;
	public final String doidFile;
	
	public OntModel fDoid;
	
	private boolean isClosed;
	
	/**
	 * 
	 * param text to annotate
	 * @return 
	 * @throws HttpResponseException if a problem occurs communicating with the annotator
	 * @throws RuntimeException if the client has been closed
	 */
    public Map<String, String> annotate(String text) throws HttpResponseException {
    	if (isClosed) {
    		throw new RuntimeException("This NCBO client has been closed.");
    	}
    	
    	// clean the wikitext before sending it
    	text = clean(text);
    	
        HttpClient client = new DefaultHttpClient();
        
        HttpPost method = new HttpPost(annotatorUrl);

        List<NameValuePair> postParams = new ArrayList<NameValuePair>();
        
        // Configure the form parameters
        postParams.add(new BasicNameValuePair("longestOnly","false"));
        postParams.add(new BasicNameValuePair("wholeWordOnly","true"));
        postParams.add(new BasicNameValuePair("filterNumber", "true"));
        postParams.add(new BasicNameValuePair("stopWords","protein,gene,disease,disorder,cell,syndrome,CAN"));
        postParams.add(new BasicNameValuePair("withDefaultStopWords","true"));
        postParams.add(new BasicNameValuePair("isTopWordsCaseSensitive","false"));
        postParams.add(new BasicNameValuePair("mintermSize","3"));
        postParams.add(new BasicNameValuePair("scored", "true"));
        postParams.add(new BasicNameValuePair("withSynonyms","true")); 
        postParams.add(new BasicNameValuePair("ontologiesToExpand", ""));
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
        	switch (res.getStatusLine().getStatusCode()) {
        	case 403:
        		System.err.println("Are you using a valid API key?");
        		break;
        	case 500:
        		System.err.println("There may be an error in your parameter settings.");
        		break;
        	default:
        		break;
        	}
        	throw new HttpResponseException(res.getStatusLine().getStatusCode(), e.getMessage());
        	
        } catch (ClientProtocolException e) {
			e.printStackTrace();
			return Collections.emptyMap();
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyMap();
		} finally {
			client.getConnectionManager().shutdown();
		}

    	// Extract DOIDs and terms from the tab-delimited response
    	Map<String, String> doids = new HashMap<String, String>();
    	
    	String 	regex = "\t45769\\/" +			// matches the first tab and Disease Ontology identifier (45769)
    					"(DOID:[\\d]*)[\\s]" +	// matches the DOID of the form DOID:1909 (group 1)
    					"([\\w\\s^\\t]*)\t";	// matches the term associated with DOID  (group 2)
    	Matcher	match = Pattern.compile(regex).matcher(tdf);
    	
    	while (match.find()) {
    		String doid = match.group(1).replace(':', '_');
    		String term = match.group(2); 
    		doids.put(doid, term);
    	}

    	return doids;
    }
    
    public Map<String,String> retainMostSpecific(Map<String, String> unfiltered) {
    	if (isClosed) {
    		throw new RuntimeException("This NCBO client has been closed.");
    	}
    	Set<String> ids = unfiltered.keySet();
    	// we'll be removing ids from the set and we don't want to do that
    	// while we iterate through it, so we should create a duplicate
    	Set<String> _ids = new HashSet<String>(ids);
    	for (String id : ids) {
    		Set<String> subs = new HashSet<String>();
    		String uri = URIPrefix + id;
    		String queryString = "PREFIX DOOWL: <http://www.geneontology.org/formats/oboInOwl#> "+ 	
    				"PREFIX RDFS: <http://www.w3.org/2000/01/rdf-schema#> "+
    				"SELECT ?sub ?label "+ 
    				"WHERE { "+
    				" ?sub RDFS:subClassOf <"+uri+"> . "+
    				" ?sub RDFS:label ?label " +
    				"} ";
    		
    		Query query = QueryFactory.create(queryString);
    		QueryExecution qe = QueryExecutionFactory.create(query, fDoid);
    		try {
    			ResultSet rs = qe.execSelect();
    			while (rs.hasNext()) {
    				QuerySolution rb = rs.nextSolution() ;
    				Resource superclass = rb.get("sub").as(Resource.class) ;
    				Literal labelnode = rb.getLiteral("label");
    				String label = null;
    				if(labelnode!=null){
    					String local = superclass.getLocalName();
    					String acc = local;
    					if(!acc.equals(id)){
    						label = labelnode.getValue().toString();
    						subs.add(acc);
    					}
    				}
    				
    			}
    		} finally {
    			qe.close();
    		}
    		for (String sub : subs) {
    			if (_ids.contains(sub)) {
    				_ids.remove(id);
    			}
    		}
    	}
    	
    	Set<String> disjoint = Sets.difference(ids, _ids);
    	
    	Map<String, String> filtered = new HashMap<String, String>(unfiltered);
    	for (String id : disjoint) {
    		filtered.remove(id);
    	}
    	return filtered;
    }
    
    public NCBOClient() throws FileNotFoundException, IOException {
    	Properties props = new Properties();
    	props.load(new FileReader(new File("SyncService.conf")));
    	apiKey = props.getProperty("annotator.api.key");
    	doidFile = "file:"+props.getProperty("doid.owl.file");
    	initializeDOIDFile();
    }
    
    public NCBOClient(String apiKey, String doidFile) {
    	this.apiKey = apiKey;
    	this.doidFile = doidFile;
    	initializeDOIDFile();
    }
    
    /**
     * Closes this client's Jena Model, releasing the resources associated with it.
     * The client cannot be used after being closed.
     */
    public void close() {
    	fDoid.close();
    	isClosed = true;
    }
    
    private String clean(String text) {
		String pretty = text;
		if(text!=null){
			//remove repeated spaces
			pretty = pretty.replaceAll("\\s+", " ");
			//remove whole templates
			pretty = pretty.replaceAll("\\{\\{.{1,100}\\}\\}", "");
			//remove comments
			pretty = pretty.replaceAll("<!--[^>]*-->", "");
			//remove dangling comments
			pretty = pretty.replaceAll("<!--[^>]*", "");
			pretty = pretty.replaceAll("[^>]*-->", "");
			//remove PBB template leftovers
			pretty = pretty.replaceAll("PBB\\|geneid=\\d+", "");
			pretty = pretty.replaceAll("PBB_Summary \\| section_title = \\| summary_text = ", "");
			pretty = pretty.replaceAll("orphan\\|date=\\w+ \\d+", "");
			//remove anything after we should be done..
			pretty = pretty.replaceAll("==", "");
		}
		return pretty;
    }
    
    private void initializeDOIDFile() {
    	fDoid = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);
    	fDoid.read(doidFile);
    	log("Successfully read DO OWL file into Jena Model with RDFS reasoning");
    	log("Model size: "+fDoid.size());
    }
    
    private void log(String message) {
    	System.out.println(message);
    }
}


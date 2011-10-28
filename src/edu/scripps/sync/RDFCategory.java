package edu.scripps.sync;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.login.LoginException;

import org.genewiki.api.Wiki;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.vocabulary.OWL;

public class RDFCategory {
	
	final String 		name;
	final String 		uri;
	final String		doid;
	final String		description;
	final Set<String> 	parents;
	final Set<String>	synonyms;
	
	/**
	 * Create a new category with the specified name, equivalent URI, and parent categories.
	 * If the category has no parents (is top-level), let parents == null.
	 * @param name
	 * @param uri
	 * @param parents parent categories. leave null if no parents (though should have at least one specifying DO term)
	 * @param synonyms synonyms for the current category. May be null or empty.
	 * @throws NullPointerException if doid, uri, or title is null
	 */
	public RDFCategory(String doid, String uri, String title, Set<String> parents, String description, Set<String> synonyms) {
		this.doid			= Preconditions.checkNotNull(doid);
		this.uri  			= Preconditions.checkNotNull(uri);
		this.name 			= Preconditions.checkNotNull(title);
		this.description	= description;
		this.parents 		= parents;
		this.synonyms		= synonyms;
	}
	
	/* ---- Getters ---- */
	public String Name() { return name; }
	public String fullName() { return "Category:"+name; }
	public String Uri()  { return uri;  }
	public String Doid() { return doid; }
	public String formattedDOID() {
		return "[[hasDOID::"+doid+"]]";
	}
	public String formattedUri() { 
		return "[[equivalent URI:="+uri+"]]";
	}
	public String Description() { 
		if (description == null) {
			return "";
		} else {
			return description; 
		}
	}	
	
	/**
	 * @return A list of parent categories. If the category has no parents, 
	 * an empty list is returned. This method never returns null.
	 */
	public Set<String> Parents() { 
		if (parents == null) {
			return Collections.emptySet();
		} else {
			return parents; 
		}
	}
	
	public Set<String> Synonyms() {
		if (synonyms == null) {
			return Collections.emptySet();
		} else {
			return synonyms;
		}
	}

	/**
	 * Returns an appropriate wikitext representation of the category, including the equivalent URI, description
	 * and any parent categories the category may belong to.
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder(this.Description()+"\n");
		sb.append(this.formattedUri()+"\n");
		for (String category : this.Parents()) {
			sb.append("[[Category:"+category+"]]\n"); // append all the category names to which this object belongs
		}
		return sb.toString();
	}

	/**
	 * Creates or modifies an existing category page on the target Wiki with the 
	 * @param category
	 * @param target
	 * @throws IOException
	 */
	public static void createCategoryOnWiki(RDFCategory category, Wiki target) throws IOException {
		String name = category.fullName();
		// Check to see if the category already exists on target; if it does,
		// add any missing categories and the equivalent URI. If not, create it 
		// from scratch.
		if (target.exists(name)[0]) {
			List<String> existingCats = Arrays.asList(target.getCategories(name));
			// Find the difference between the two sets; categories that are not part of the DO will
			// be ignored (i.e. in existingCats but not in Parents)
			Set<String> missing = Sets.difference(category.Parents(), new HashSet<String>(existingCats));
			String text = target.getPageText(name);
			// Add equivalent URI if it doesn't already exist
			if (!text.contains(category.formattedUri())) {
				text += category.formattedUri();
			}
			// Add any missing categories
			for (String mcat : missing) {
				text += "[[Category:"+mcat+"]]\n";
			}
			try {
				target.edit(name, text, "Added equivalent URI and missing DO categories (if any)", false);
				System.out.printf("Edited preexisting category '%s' to include information from DO.\n", category.Name());
			} catch (LoginException e) {
				System.out.println("Error: Unable to make edits (not logged in). Nothing further to do; exiting.");
				return;
			}
		} else {
			try {
				target.edit(name, category.toString(), "Added category imported from the Disease Ontology", false);
				System.out.printf("Added new category '%s'.\n", category.Name());
			} catch (LoginException e) {
				System.out.println("Error: Unable to make edits (not logged in). Nothing further to do; exiting.");
				return;
			}
		}
	}
	
	public static Set<RDFCategory> getCategories(String input_ont_file){
		Set<RDFCategory> categories = new HashSet<RDFCategory>();
		OntModel ont = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM); 
		ont.read(input_ont_file);
		System.out.println("read OWL file into Jena Model with no reasoning");
		ExtendedIterator<OntClass> classes = ont.listClasses();
		int c = 0;
		OntProperty dep = ont.getOntProperty(OWL.getURI()+"deprecated");

		while(classes.hasNext()){
			c++;
			OntClass oclass = classes.next();
			String title = oclass.getLabel(null);
			RDFNode d = oclass.getPropertyValue(dep);
			boolean isdep = false;
			if(d!=null){
				Literal l = d.as(Literal.class);
				isdep = l.getBoolean();
			}
			
			// If class is not deprecated, get the URI and description, then 
			// parse the parent categories and synonyms
			if(!isdep){
				String uri = oclass.getURI();
				String description = oclass.getComment(null);

				// Extract parents
				Set<String> directParents = new HashSet<String>();
				ExtendedIterator<OntClass> parents = oclass.listSuperClasses();
				while(parents.hasNext()){
					OntClass p = parents.next();
					directParents.add(p.getLabel(null));
				}
				// All categories pulled from here should be members of the category 
				// "Disease Ontology Term" 
				directParents.add("Disease Ontology Term");

				// Extract synonyms
				Set<String> synonyms = new HashSet<String>();
				ExtendedIterator<RDFNode> syns = oclass.listLabels(null);
				while(syns.hasNext()){
					Literal t = syns.next().as(Literal.class);
					String syn = t.getString();
					if(!syn.equals(title)){
						synonyms.add(syn);
					}
				}
				//FIXME no passing null as DOID!
				RDFCategory cat = new RDFCategory(null, uri, title, directParents, description, synonyms);
				categories.add(cat);
				System.out.println(c+"\t"+title+"\n"+cat);
			}else{
				System.out.println(c+"\t"+title+"\tDEPRECATED");
			}
		}

		return categories;
	}
	
	/**
	 * Read an OWL ontology and produce a simple list of category/class objects.  If OBO, it uses their properties.
	 * Ignores deprecated classes.
	 * @param input_ont_file
	 * @param fromobo
	 * @return
	 */
	public Set<RDFCategory> getCategories(String input_ont_file, boolean fromobo){
		Set<RDFCategory> cat_map = new HashSet<RDFCategory>();
		OntModel ont = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM); 
		ont.read(input_ont_file);
		System.out.println("read OWL file into Jena Model with no reasoning");
		ExtendedIterator<OntClass> classes = ont.listClasses();
		int c = 0;
		OntProperty dep = ont.getOntProperty(OWL.getURI()+"deprecated");
		OntProperty obo_def = null;
		OntProperty obo_syns = null;
		OntProperty obo_id = null;
		if(fromobo){
			obo_def = ont.getOntProperty("http://purl.obolibrary.org/obo/IAO_0000115");
			obo_syns = ont.getOntProperty("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
			obo_id = ont.getOntProperty("http://www.geneontology.org/formats/oboInOwl#id");
		}
		while(classes.hasNext()){
			c++;
			OntClass oclass = classes.next();
			String title = oclass.getLabel(null);
			RDFNode d = oclass.getPropertyValue(dep);
			boolean isdep = false;
			if(d!=null){
				Literal l = d.as(Literal.class);
				isdep = l.getBoolean();
			}
			if(!isdep){

				String uri = oclass.getURI();
				String definition = ""; String accession = "";
				Set<String> synonyms = new HashSet<String>();
				if(fromobo){
					//accession
					RDFNode o_id = oclass.getPropertyValue(obo_id);
					Literal l = o_id.as(Literal.class);
					accession = l.getString().replace(":", "_");
					//definition
					RDFNode o_def = oclass.getPropertyValue(obo_def);
					if(o_def!=null){
						l = o_def.as(Literal.class);
						definition = l.getString();
					}
					//synonyms
					ExtendedIterator<RDFNode> syns = oclass.listPropertyValues(obo_syns); //only 'exact'
					while(syns.hasNext()){
						Literal t = syns.next().as(Literal.class);
						String syn = t.getString();
						if(!syn.equals(title)){
							synonyms.add(syn);
						}
					}
				}else{//not obo 
					definition = oclass.getComment(null);
					accession = oclass.getLocalName();
					ExtendedIterator<RDFNode> syns = oclass.listLabels(null);
					while(syns.hasNext()){
						Literal t = syns.next().as(Literal.class);
						String syn = t.getString();
						if(!syn.equals(title)){
							synonyms.add(syn);
						}
					}
				}
				//plain class hierarchy (no reasoning or OWL or OBO 'subset' concept utilized)
				Set<String> directParents = new HashSet<String>();
				ExtendedIterator<OntClass> parents = oclass.listSuperClasses();
				while(parents.hasNext()){
					OntClass p = parents.next();
					directParents.add(p.getLabel(null));
				}			

				RDFCategory cat = new RDFCategory(accession, uri, title, directParents, definition, synonyms);
				cat_map.add(cat);
				System.out.println(c+"\t"+cat);
			}else{
				System.out.println(c+"\t"+title+"\tDEPRECATED");
			}
		}

		return cat_map;

	}
	
}

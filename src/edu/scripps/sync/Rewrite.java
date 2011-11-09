package edu.scripps.sync;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import org.genewiki.api.Wiki;

import com.google.common.base.Preconditions;

import edu.scripps.resources.AnnotationDatabase;

/**
 * Static methods for re-writing content en route to GeneWiki+.
 * The general contract is that the method will return the altered
 * text, unless it fails, in which case it will return the original
 * text that was passed to it. 
 * @author eclarke
 *
 */
public class Rewrite {

	/**
	 * Prepends a template specifying that the article is mirrored
	 * from Wikipedia. The method does not insert the template if
	 * it is already present; instead, it returns the unaltered
	 * text. 
	 * @param src
	 * @return article text with a {{Mirrored}} template appended
	 */
	public static String prependMirroredTemplate(String src){
		if (src.startsWith("{{Mirrored")) {
			return src;
		} else {
			return "{{Mirrored | {{PAGENAME}} }} \n" + src;
		}
	}
	
	/**
	 * Appends a {{GW+}} template to the page, replacing any template that 
	 * currently may exist on the page. The template has a type parameter added
	 * if it is a gene page or a disease page; if it is a gene page, associated
	 * SNPs are also appended as additional parameters to the template. If it is
	 * neither a gene nor a disease, a generic {{GW+}} template is added.
	 * @param src source text
	 * @param title title of the page
	 * @param wiki the target wiki (to check for SNPs associated with the gene, if gene)
	 * @return src with template appended
	 */
	public static String appendGWPTemplate(String src, String title, Wiki wiki) {
		// First, remove any {{GW+}} template that may already be on the page
		if (src.contains("{{GW+")) {
			int a = src.indexOf("{{GW+");
			int b = src.indexOf("}}", a) + 2;
			src = src.substring(0, a) + src.substring(b, src.length());
		}
		// Second, identify the page as a gene page, a disease page,
		// or neither
		if (src.contains("{{PBB|gene")){
			// If it's a gene page, find the SNPs associated with it
			Set<String> snps = null;
			try {
				snps = wiki.askQuery("in_gene", title).keySet();
			} catch (IOException e) {
				e.printStackTrace();
				return src;
			}
			StringBuilder gw = new StringBuilder("\n{{GW+|gene");
			for (String snp : snps) {
				gw.append('|').append(snp);
			}
			gw.append("}}\n");
			return src + gw.toString();
		} else {
			// Test to see if it's a recognized disease term
			AnnotationDatabase anno = new AnnotationDatabase("annotations.db");
			String disease = null;
			try {
				disease = anno.getAssociatedDisease(title);
			} catch (SQLException e) {
				// We can let this pass; disease remains null and a default template is appended. 
			}
			// If it is a disease, write the GW+ template accordingly; if not,
			// write a generic (no-parameter) GW+ template
			if (disease != null) {
				return src + "\n{{GW+|disease}}\n";
			} else {
				return src + "\n{{GW+}}\n";
			}
		}
	}
	
	/**
	 * Modifies {{SWL}} templates to be semantic wikilinks.
	 * @param src source text 
	 * @return source text with SWLs converted
	 */
	public static String convertSWLTemplates(String src) {
		while (src.contains("{{SWL")) {
			try {
				/* ---- Definitions ---- */
				int a = src.indexOf("{{SWL") + 5;
				int b = src.indexOf("}}",a);
				String swl = src.substring(a, b);
				String target 	= null;
				String label	= null;
				String type		= null;
				
				/* ---- Parsing the SWL template (order-agnostic) ---- */
				// find the target field's value (skip if not specified; indicates malformed template)
				try {
					target = Preconditions.checkNotNull(extractValueForField("target", swl));
				} catch (NullPointerException e) {
					continue;
				}
				
				// find the type field's value (skip if not specified; indicates malformed template)
				try {
					type = Preconditions.checkNotNull(extractValueForField("type", swl));
				} catch (NullPointerException e) {
					continue;
				}
				
				// find the label field's value (okay if not specified)
				label = extractValueForField("label", swl); 
				
				/* ---- Constructing the Semantic Mediawiki link ---- */
				String sml;
				if (label != null) {
					sml = String.format("[[%s::%s|%s]]", type, target, label);
				} else {
					sml = String.format("[[%s::%s]]", type, target);
				}
				
				/* ---- Replacing SWL template with Semantic Mediawiki link ---- */
				src = src.substring(0, a-5) + sml + src.substring(b+2, src.length());
				
				// rinse and repeat
			} catch (StringIndexOutOfBoundsException e) {
				continue; // if there's a parsing error, we'd rather ignore and continue than fail
			}
		}
		
		return src;
	}
	
	/**
	 * Returns the value for a field inside a properly-formatted template. If the field does
	 * not exist, returns null. Properly formatted means that the next field starts at the next
	 * occurrence of the '|' character; this does not work for nested templates or links with labels.
	 * @param field the field name
	 * @param template the inside text of the template
	 * @return the value for the field, or null if field not found
	 */
	private static String extractValueForField(String field, String templateText) {
		// the regex accounts for varying amount of whitespace between the '|', fieldname, and '='
		Matcher m = Pattern.compile("\\|[\\s]*"+field+"[\\s]*=").matcher(templateText);
		if (m.find()) {
			int a = m.end();
			// find the end of the 
			int b = (templateText.indexOf('|', a) != -1) 
						? templateText.indexOf('|', a) 
						: templateText.length();
			return templateText.substring(a, b);
		} else {
			return null;
		}
	}
	
	/**
	 * Returns a copy of the supplied text with alterations made to the embedded wikilinks.
	 * <br/>Rules:
	 * <br/>1. If the wikilink does not contain '::' or 'Category:' and does not exist on the target,
	 * we append "wikipedia:" to have it point back to Wikipedia.
	 * <br/>2. If the wikilink does not contain ':" and transcludes the {{PBB}} template, we append
	 * "is_associated_with::" to make it a generic semantic link.
	 * <br/>Thus, a link remains completely untouched if it contains "::", ':', or "Category:"
	 * @param src article text
	 * @param target target Wiki, to check for page existence
	 * @return altered text if successful, original text if failed
	 */
	public static String fixLinks(String src, Wiki target) {
		
		// add a marker to all the links so we can iterate through them (except the ones that point to wikipedia)
		String src2 = src.replaceAll("\\[\\[(?!(wikipedia))", "[[%");
		
		try {
			while (src2.contains("[[%")) {
				
				// extract the link text
				int a = src2.indexOf("[[%")+3; 	// left inner bound
				int b = src2.indexOf("]]", a);	// right inner bound
				String link = src2.substring(a, b);
				
				// remove the label, if present
				int pipe = link.indexOf('|');
				link = (pipe == -1) ? link : src2.substring(a, a+pipe);

				// If the link does not exist (and is not a semantic wikilink or category), append 'wikipedia:'
				if (!link.contains("::") && !link.contains(":") && !link.contains("Category:") && !target.exists(link)[0]) {
					src2 = src2.substring(0, a-1) + "wikipedia:" + src2.substring(a);
				// else if it's part of the Gene Wiki and not a special link (like File: or en:), make it a semantic link
				} else if (src.contains("{{PBB") && !link.contains(":")) {
					src2 = src2.substring(0, a-1) + "is_associated_with::" + src2.substring(a);
				// otherwise just remove the marker and move on
				} else {
					src2 = src2.substring(0, a-1) + src2.substring(a);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return src;
		}
		return src2;
	}
	
	/**
	 * Returns a copy of the text with any [[wikipedia:File: or [[wikipedia:Image tags fixed (should not contain wikipedia:)
	 * and also strips the is_associated_with property from links inside the descriptors of these tags. This method should be
	 * run every time the bot converts wikilinks to basic semantic links, to be safe.
	 * @param src
	 * @return
	 */
	public static String cleanUp(String src) {
		// Matches only "{{GW+" on its own, an artifact of a broken extraction process
		String src2 = src.replaceAll("\\{\\{GW\\+[^}|]", "");
		
		/* *
		 * Finds wikilinks that have wikipedia: appended incorrectly, 
		 * and also contain is_associated_with properties (also incorrect) 
		 * */
		// This grabs everything too much, we only want what's between the opening and closing tags (group 2)
		String 	malformedTagRegex	= "\\[\\[wikipedia:([Ff]ile:|[Ii]mage:)(.*)\\]\\]";
		// This extracts is_associated_with tags inside a string (must be used on substrings from malformedTagRegex)
		String 	problemTagRegex		= "\\[\\[is_associated_with::[\\w\\s\\|]*\\]\\]"; 	
		Matcher malformedRegions 	= Pattern.compile(malformedTagRegex).matcher(src2);
		Pattern problemTagPattern	= Pattern.compile(problemTagRegex);
		
		while (malformedRegions.find()) {
			String whole = malformedRegions.group();
			String insideTag = malformedRegions.group(2);

			Matcher problemTags = problemTagPattern.matcher(insideTag);
			while (problemTags.find()) {
				String tag = problemTags.group().replace("is_associated_with::", "");
				insideTag = insideTag.replace(problemTags.group(), tag);
			}
			whole =	whole.replaceAll("wikipedia:[Ff]ile:", "File:")
						.replaceAll("wikipedia:[Ii]mage:", "Image:")
						.replace(malformedRegions.group(2), insideTag);
			src2 = src2.replace(malformedRegions.group(), whole);
			
		}
		
		return src2;
	}
	
	/**
	 * Appends any categories related to the article subject from the Disease Ontology that do
	 * not already exist on the page.
	 * @param src source text
	 * @param title page title
	 * @param annodb absolute path (with filename) of the annotations database
	 * @param target target wiki (for looking up corresponding DOIDs (must have SMW & SMWAskQuery installed))
	 * @param isGene true if article is a gene page, false if article is a SNP
	 * @return text of the article with any missing categories appended
	 * @throws IllegalArgumentException if the annotation database can't be found
	 * @deprecated Use appendDetachedAnnotations instead; this is like categorizing cars as subclasses of parking garages
	 */
	public static String appendCategories(String src, String title, String annodb, Wiki target, boolean isGene) {
		try {

			Set<String> diseaseCategories = getPagesForAssocTerms(title, annodb, target, isGene);
			// Filter out any existing categories
			List<String> existingCategories = Arrays.asList(target.getCategories(title));
			diseaseCategories.removeAll(existingCategories);
			for (String disease : diseaseCategories) {
				src += "[["+disease+"]]\n";
			}
			return src;

		} catch (IOException e) {
			e.printStackTrace();
			return src;
		}
	}
	
	private static Set<String> getPagesForAssocTerms(String title, String annodb, Wiki target, boolean isGene) {
		try {
			if (!new File(annodb).exists()) 
				throw new IllegalArgumentException("Specified annotation database file not found.");
			
			// Select the DO terms related to this article from the database
			AnnotationDatabase anno = new AnnotationDatabase(annodb);
			Map<String,String> diseases = (isGene) 	? anno.getDiseasesAssociatedWithGene(null, title, true) 
													: anno.getDiseaseAssociatedWithSNP(title, true);
			Set<String> diseaseCategories = new HashSet<String>(diseases.size());
			
			// Match the DO terms with the corresponding category pages on the Gene Wiki
			for (String doid : diseases.keySet()) {
				// We only expect there to be one match for any given DOID. If there's multiple, we only 
				// select the first one (but again, there shouldn't be multiple unless we've mislabeled).
				String dTitle = null;
				try {
					dTitle = new ArrayList<String>(target.askQuery("hasDOID", doid).keySet()).get(0);
					diseaseCategories.add(dTitle);
				} catch (IndexOutOfBoundsException e) {	
					System.err.printf("No page found for term: '%s' (%s). Omitting...\n", diseases.get(doid), doid);
				}
			}
			return diseaseCategories;
		} catch (SQLException e) {
			e.printStackTrace();
			return Collections.emptySet();
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptySet();
		}
	}
	
	public static String appendDetachedAnnotations(String src, String title, String annodb, Wiki target, boolean isGene) {
		Set<String> annotations = getPagesForAssocTerms(title, annodb, target, isGene);
		if (annotations.size() == 0) {
			return src;
		}
		StringBuffer sb = new StringBuffer();
		sb.append("\n{{CAnnotationsStart}}\n");
		for (String anno : annotations) {
			String _anno = anno.replace("Category:", "");
			sb.append("*  [[is_associated_with_disease::"+_anno+"]]\n");
			// Categorize or create the page corresponding to the disease
			categorizeDiseasePage(_anno, target);
		}
		sb.append("{{CAnnotationsEnd}}\n");
		// excise old CAnnotations list before appending new (if present)
		if (src.contains("\n{{CAnnotationsStart}}") && src.contains("{{CAnnotationsEnd}}\n")) {
			Matcher m = Pattern.compile("\n\\{\\{CAnnotations.*CAnnotationsEnd\\}\\}\n", Pattern.DOTALL).matcher(src);
			if (m.find()) {
				src = src.replace(m.group(), "");
			}
		}
		src += sb.toString(); 

		return src; 
	}
	
	/**
	 * This method creates or edits a disease page corresponding to a disease ontology term specified
	 * and categorizes it under the corresponding category. Also adds the GW+|disease template to show
	 * inline query.
	 * @param _anno disease ontology term
	 * @param target 
	 * @return true if success, false if error encountered
	 */
	public static boolean categorizeDiseasePage(String _anno, Wiki target) {
		String do_page = "";
		String do_page_original = "";
		try {
			if (target.exists(_anno)[0]) {
				do_page = target.getPageText(_anno);
				do_page_original = do_page;
				do_page = do_page.replace("#REDIRECT", "");
			} else {
				log("!! Creating new disease ontology page for "+_anno);
			}
			log("Categorizing disease ontology page "+_anno);
			if (!do_page.contains("\n[[Category:"+_anno+"]]\n"))
					do_page += "\n[[Category:"+_anno+"]]\n";
			log("Adding GW+ template to disease ontology page...");
			do_page = do_page.replaceAll("\\{\\{GW\\+[\\|][\\w\\|]*\\}\\}\n", "");
			do_page += "{{GW+|disease}}\n"; 
			if (!(do_page.equals(do_page_original))) {
				target.edit(_anno, do_page, "Altered/created Disease Ontology page for "+_anno, false);
			} else {
				log("No changes were made to source text; moving on.");
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (LoginException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public static String removeDiseaseOntologyCategoryMembership(String src, String title, String annodb, Wiki target, boolean isGene) {
		Set<String> annotations = getPagesForAssocTerms(title, annodb, target, isGene);
		for (String anno : annotations) {
			src = src.replace("[["+anno+"]]\n", "");
		}
		return src;
	}
	
	public static String appendDiseaseQuery(String src) {
		String dq = "{{DiseaseQuery}}";
		if (!src.contains(dq)) {
			return src + dq+"\n";
		} else {
			return src;
		}
	}
	
	private static void log(String message) {
		System.out.println(message);
	}
	
}

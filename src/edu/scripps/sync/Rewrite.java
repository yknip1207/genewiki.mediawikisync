package edu.scripps.sync;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.genewiki.api.Wiki;

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
			int a = src.indexOf("{{GW+") + 5;
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
			StringBuilder gw = new StringBuilder("\n{{GW+|gene|");
			for (String snp : snps) {
				gw.append(snp).append("|");
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
				Matcher mTarget = Pattern.compile("\\|[\\s]*target=").matcher(swl);
				Matcher mLabel 	= Pattern.compile("\\|[\\s]*label=").matcher(swl);
				Matcher mType 	= Pattern.compile("\\|[\\s]*type=").matcher(swl);
				String target 	= null;
				String label	= null;
				String type		= null;
				
				/* ---- Parsing the SWL template (order-agnostic) ---- */
				// find the target field's value
				if (mTarget.find()) {
					// target start index
					int tgt = mTarget.end();
					// find the end, if it's bordered by another field (the '|' char) or is at the end of the template
					int end = (swl.indexOf('|', tgt) != -1) ? swl.indexOf('|', tgt) : swl.length();
					// extract the text after the '=' (the value of the field)
					target = swl.substring(tgt, end);		
				} else { continue; } // if there's no target defined, it's a malformed SWL, so skip it
				
				// find the type field's value
				if (mType.find()) {
					// same procedure as above
					int typ = mType.end();
					int end = (swl.indexOf('|', typ) != -1) ? swl.indexOf('|', typ) : swl.length();
					type = swl.substring(typ, end);	
				} else { continue; }
				
				// find the label field's value
				if (mLabel.find()) {
					// same procedure as above
					int lbl = mLabel.end();
					int end = (swl.indexOf('|', lbl) != -1) ? swl.indexOf('|', lbl) : swl.length();
					label = swl.substring(lbl, end);
				}
				
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
				if (!link.contains("::") && !link.contains("Category:") && !target.exists(link)[0]) {
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
	
}

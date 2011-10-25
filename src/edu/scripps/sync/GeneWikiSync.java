package edu.scripps.sync;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.genewiki.api.Wiki;

/**
 * GeneWikiSync updates GeneWiki+ with the edits made to Wikipedia on specified pages in the specified
 * period. It converts the SWL template to a semantic link for Semantic Mediawiki, and converts
 * any interwiki links in the page text (that do not already exist on GW+) into links back to WP.
 * @author eclarke@scripps.edu
 *
 */
public class GeneWikiSync extends Sync {
	
	/**
	 * Creates a new GeneWikiSync object that extends
	 * the base Sync class. See superclass constructor Sync()
	 * for more details.
	 * @param source
	 * @param target
	 * @param period
	 * @param rewrite
	 */
	public GeneWikiSync(Wiki source, Wiki target, int period, boolean rewrite) {
		super(source, target, period, rewrite);
	}

	@Override
	public String rewriteArticleContent(String originalText) {
		try {
			return run(originalText);
		} catch (IOException e) {
			return originalText;
		}
	}
	
	public String run(String src) throws IOException {
		log("Appending {{Mirrored}} template...");
		if (src.startsWith("{{Mirrored")) {
			// pass
		} else if (src.contains("{{PBB")) {
			src = "{{Mirrored | {{PAGENAME}} }}\n"+src;
		} 
		
		log("Fixing links...");
		src = src.replaceAll("\\[\\[(?!(wikipedia))", "[[%");
		while (src.contains("[[%")) {
			int a = src.indexOf("[[%")+3; // identify the left bound + # character
			int b = src.indexOf("]]", a); // and right bound
			String link = src.substring(a, b);	
			// wp links may contain an alt text separated from the linked page title by a '|' char
			// if so, we only want the title
			int c = link.indexOf("|");	// this will be -1 if there's no alt text
			String linkTitle = (c == -1) ? link : src.substring(a, a+c);
			if (!link.contains("::") &&!target.exists(linkTitle)[0]) {
				src = src.substring(0, a-1)+			// omits the # marker 
						"wikipedia:"+					// adds the prefix to point us back to wikipedia (i.e. not an internal link)
						src.substring(a);				// and add the rest of the text back in
			} else if (src.contains("{{PBB") && !link.contains(":")) {
				src = src.substring(0, a-1) + "is_associated_with::" + src.substring(a);
			} else {
				src = src.substring(0, a-1) + src.substring(a);
			}
			
		}
		
		while (src.contains("{{SWL")) {
			log("Converting {{SWL}} templates...");
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
				if (mTarget.find()) {
					// target start index
					int tgt = mTarget.start() + mTarget.group().length();	
					// find the end, if it's bordered by another field (the '|' char) or is at the end of the template
					int end = (swl.indexOf('|', tgt) != -1) ? swl.indexOf('|', tgt) : swl.length();
					// extract the text after the '=' (the value of the field)
					target = swl.substring(tgt, end);
					
				} else { continue; } // if there's no target defined, it's a malformed SWL, so skip it
				if (mType.find()) {
					// same procedure as above
					int typ = mType.start() + mType.group().length();
					int end = (swl.indexOf('|', typ) != -1) ? swl.indexOf('|', typ) : swl.length();
					type = swl.substring(typ, end);	
				} else { continue; }
				if (mLabel.find()) {
					// same procedure as above
					int lbl = mLabel.start() + mLabel.group().length();
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
	
	private String appendMirroredTemplate(String src) {
		log("Appending {{Mirrored}} template...");
		if (src.startsWith("{{Mirrored")) {
			// pass
		} else if (src.contains("{{PBB")) {
			src = "{{Mirrored | {{PAGENAME}} }}\n"+src;
		} 
		return src;
	}
	
	private String prependDBPediaLink(String src) {
		log("Prepending DBPedia link...");
		if (!src.contains("[[equivalent URI:=http://dbpedia")) {
			src = src + "[[equivalent URI:=http://dbpedia.org/resource/{{PAGENAME}}]]";
		}
		return src;
	}
	
	/**
	 * Returns a copy of the source text with the links that do not exist on 
	 * the target modified to point back to their original articles on Wikipedia,
	 * thus avoiding large numbers of redlinks on secondary articles.
	 * @param src source article text
	 * @return copy of text with fixed outgoing links
	 */
	private String fixOutboundLinks(String src) {
		
		src = src.replace("[[", "[[%");	// identify the links we need to process
		try {
			while (src.contains("[[%") && src.contains("]]")) {
				int a = src.indexOf("[[%")+3; // identify the left bound + # character
				int b = src.indexOf("]]", a); // and right bound
				String link = src.substring(a, b);	
				// wp links may contain an alt text separated from the linked page title by a '|' char
				// if so, we only want the title
				int c = link.indexOf("|");	// this will be -1 if there's no alt text
				String linkTitle = (c == -1) ? link : src.substring(a, a+c);
				if (!target.exists(linkTitle)[0]) {
					src = src.substring(0, a-1)+			// omits the # marker 
							"wikipedia:"+					// adds the prefix to point us back to wikipedia (i.e. not an internal link)
							src.substring(a);	// and add the rest of the text back in
				} else if (src.contains("{{PBB") && !link.contains("::")){
					src = src.substring(0, a-1)+"is_associated_with::"+src.substring(a);
				} else {
					src = src.substring(0, a-1) + src.substring(a);
				}
					
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return src;
	}
	
	/**
	 * Finds all internal links and replaces them with semantic links specifying type "is_associated_with".
	 * @param source article text
	 * @return article text with rewritten links
	 */
	private String convertWikiLinks(String source) {
		return source.replaceAll("\\[\\[(?!(wikipedia))", "[[is_associated_with::");
	}
	
	/**
	 * Converts SWL templates on Wikipedia of the form {{SMW | target = x | type = y | label = z}} to 
	 * Semantic Mediawiki style [[type::target|label]]. The 
	 * @param src article text
	 * @return copy of text with {{SWL}} templates converted to [[sml::links]]
	 */
	private String convertSemanticLinks(String src) {
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
				if (mTarget.find()) {
					// target start index
					int tgt = mTarget.start() + mTarget.group().length();	
					// find the end, if it's bordered by another field (the '|' char) or is at the end of the template
					int end = (swl.indexOf('|', tgt) != -1) ? swl.indexOf('|', tgt) : swl.length();
					// extract the text after the '=' (the value of the field)
					target = swl.substring(tgt, end);
					
				} else { continue; } // if there's no target defined, it's a malformed SWL, so skip it
				if (mType.find()) {
					// same procedure as above
					int typ = mType.start() + mType.group().length();
					int end = (swl.indexOf('|', typ) != -1) ? swl.indexOf('|', typ) : swl.length();
					type = swl.substring(typ, end);	
				} else { continue; }
				if (mLabel.find()) {
					// same procedure as above
					int lbl = mLabel.start() + mLabel.group().length();
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
	
	private void log(String s) {
		System.out.println(s);
	}

}

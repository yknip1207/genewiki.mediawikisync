package edu.scripps.sync;

import java.io.IOException;

import org.genewiki.api.Wiki;

import static edu.scripps.sync.Rewrite.*;

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
	public String rewriteArticleContent(String originalText, String title) {
		try {
			return alteredContent(originalText, title);
		} catch (IOException e) {
			return originalText;
		}
	}
	
	/**
	 * Performs a litany of content alterations on the source text and returns the
	 * modified copy.
	 * @param src source text
	 * @param title page title
	 * @return altered source text
	 * @throws IOException if errors occur in working with the target Wiki
	 */
	public String alteredContent(String src, String title) throws IOException {
		log("Prepending mirrored template...");
		src = prependMirroredTemplate(src);
		log("Appending custom GW+ template...");
		src = appendGWPTemplate(src, title, target);
		log("Altering interwiki links...");
		src = fixLinks(src, target);
		log("Converting any SWL templates to semantic wikilinks...");
		src = convertSWLTemplates(src);
		log("Appending found disease annotations using NCBO Annotator...");
		src = appendDetachedAnnotations(src, target);
		log("Done.");
		return src;
	}

	
	private void log(String s) {
		System.out.println(s);
	}

}

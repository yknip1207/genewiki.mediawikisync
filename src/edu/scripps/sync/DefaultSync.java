package edu.scripps.sync;

import org.genewiki.api.Wiki;

public class DefaultSync extends Sync {

	/**
	 * DefaultSync constructor simply calls the superclass constructor.
	 * Override this for custom constructor functionality.
	 * @param source
	 * @param target
	 * @param period
	 * @param rewrite
	 */
	public DefaultSync(Wiki source, Wiki target, int period, boolean rewrite) {
		super(source, target, period, rewrite);
	}

	@Override
	String rewriteArticleContent(String originalText) {
		// TODO Add your rewrite rules here!
		return originalText;
	}

}

package edu.scripps.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.security.auth.login.LoginException;

import org.genewiki.api.Wiki;
import org.genewiki.api.Wiki.Revision;

/**
 * Sync updates GeneWiki+ with the edits made to Wikipedia on specified pages in the specified
 * period. It converts the SWL template to a semantic link for Semantic Mediawiki, and converts
 * any interwiki links in the page text (that do not already exist on GW+) to links back to WP.
 * <p>
 * Sync is designed as a thread to run under a ScheduledExecutorService (though it could be run
 * in a Timer by changing 'implements Runnable' to 'extends TimerTask') so we can view its exit
 * state. See SyncService for more details.
 * @author eclarke
 *
 */
public abstract class Sync implements Runnable {
	
	private 	final Wiki		source;
	protected 	final Wiki 		target;
	private 	final int 		period;
	private 	final boolean 	rewrite;
	
	/**
	 * Create a new Sync object with the specified MediaWiki installations
	 * and the synchronization period (in minutes).
	 * @param source source MediaWiki
	 * @param target target MediaWiki
	 * @param period synchronization period (in minutes)
	 * @param rewrite if the Sync object should change any content before writing
	 */
	public Sync(Wiki source, Wiki target, int period, boolean rewrite) {
		
		this.source = source;
		this.target = target;
		this.period = period;
		this.rewrite = rewrite;
	
	}
	
	/**
	 * Gathers recent changes from the watchlist and writes them to the target 
	 * Mediawiki installation.
	 * @throws RuntimeException if network error occurs
	 */
	public void run() {
		log("Syncing...");
		try {
			List<String> changed = getRecentChanges(period);
			log(String.format("Found %d new changes...", changed.size()));
			writeChangedArticles(changed);
		} catch (IOException e) {
			log("Network error retrieving changes from watchlist.");
			throw new RuntimeException("Network error retrieving changes from watchlist.");
		}
	}
	
	/**
	 * Returns a list of titles corresponding to pages changed in the 
	 * time between the moment the method is called and the specified
	 * number of minutes in the past.
	 * @param minutesAgo number of minutes ago to form the lower bound of changes
	 * @return list of page titles
	 * @throws IOException if network error occurs
	 */
	private List<String> getRecentChanges(int minutesAgo) throws IOException {
		Calendar past = Calendar.getInstance();
		past.add(Calendar.MINUTE, -minutesAgo);
		List<Revision> live = source.getChangesFromWatchlist(past, true);
		Set<String> changed = new HashSet<String>(live.size());
		for (Revision rev : live) {
			changed.add(rev.getTitle());
		}
		return new ArrayList<String>(changed);	
	}
	
	/**
	 * Writes the supplied list of articles to the target MediaWiki, optionally
	 * performing custom re-writes before uploading. 
	 * @param changed
	 * @return
	 */
	private List<String> writeChangedArticles(List<String> changed) {
		List<String> completed = new ArrayList<String>(changed.size());
		for (String title : changed) {
			try {
				String text = source.getPageText(title);
				Revision rev = source.getTopRevision(title);
				if (rewrite) {
					text = rewriteArticleContent(text);
				}
				String summary = rev.getSummary();
				String author = rev.getUser();
				String revid = rev.getRevid()+"";
				summary = String.format("{[SYNC | user = %s | revid = %s | summary = %s]}", author, revid, summary); 
				target.edit(title, text, summary, false);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (LoginException e) {
				e.printStackTrace();
			}
			
		}
		return completed;
	}
	
	/**
	 * Subclasses can override this method to provide custom rewriting text, such as link
	 * modification, or addition of an attribution clause (necessary for Wikipedia mirrors).
	 * @param originalText original article text
	 * @return article text modified
	 */
	abstract String rewriteArticleContent(String originalText);
	

	
	private void log(String message) {
		System.out.println(message);
	}
}
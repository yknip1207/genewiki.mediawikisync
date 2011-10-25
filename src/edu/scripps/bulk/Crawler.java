package edu.scripps.bulk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.CredentialNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import org.genewiki.api.Wiki;
import org.genewiki.util.FileHandler;
import org.genewiki.util.Serialize;

import edu.scripps.sync.Rewrite;

public class Crawler {
	
	final Wiki source;
	final Wiki target;
	List<String> completed;
	List<String> failed;
	
	public Crawler(Wiki sourceWiki, Wiki targetWiki, List<String> completed, List<String> failed) {
		source = sourceWiki;
		target = targetWiki;
		this.completed = completed;
		this.failed = failed;
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws FailedLoginException, IOException, CredentialNotFoundException {
		Wiki target = new Wiki("genewikiplus.org", "");
		Wiki source = new Wiki();
		Wiki edited = new Wiki();
		source.setMaxLag(0);
		source.setUsingCompressedRequests(false);
		source.login("genewikiplus", args[0].toCharArray());
		target.setUsingCompressedRequests(false);
		target.login("SyncBot", args[1].toCharArray());
		target.setThrottle(0);
		edited.login("genewikiplus_extended", args[2].toCharArray());
		List<String> previouslyEdited = new ArrayList<String>();
//		try {previouslyEdited = (ArrayList<String>) Serialize.in("completed.list.string.ser"); }
//		catch (Exception e) { previouslyEdited = new ArrayList<String>(); }
		List<String> failed = new ArrayList<String>();
//		try {failed = (ArrayList<String>) Serialize.in("failed.list.string.ser"); }
//		catch (Exception e) { failed = new ArrayList<String>(); }
		Crawler crawler = new Crawler(source, target, previouslyEdited, failed);
		ArrayList<String> all = new ArrayList<String>(crawler.findAllArticles(target));
		List<String> watchlist = Arrays.asList(source.getRawWatchlist());
		all.removeAll(watchlist);
		all.removeAll(Arrays.asList(target.whatTranscludesHere("Template:GW+")));
//		StringBuilder sb = new StringBuilder();
//		for (String title : all) {
//			sb.append(title+"\n");
//			log("adding "+title+"to watchlist");
//			edited.watch(title);
//		}
//		FileHandler fh = new FileHandler("/Users/eclarke/");
//		fh.write(sb.toString(), "all.pages.nonwatchlist", 'o');
//		Collections.reverse(watchlist);
//		int start = watchlist.indexOf("PTPN1");
//		watchlist = watchlist.subList(start, watchlist.size());
		
		crawler.execute(all);

		
	}
	
	public List<String> findAllArticles(Wiki wiki) {
		try {
			// 0 is article namespace
			return Arrays.asList(wiki.listPages("", Wiki.NO_PROTECTION, 0));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public List<String> findAllTemplates(Wiki wiki) {
		try {
			// 10 is template namespace
			return Arrays.asList(wiki.listPages("", Wiki.NO_PROTECTION, 10));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public List<String> findAllCategories(Wiki wiki) {
		try {
			// 14 is category namespace
			return Arrays.asList(wiki.listPages("", Wiki.NO_PROTECTION, 14));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	@SuppressWarnings("unchecked")
	public List<String> execute(List<String> titles) {
		for (String title : titles) {
			try {
				if (completed.contains(title))
					continue;
				if (failed.contains(title))
					continue;
				
				String src = "";
				try { src = target.getPageText(title); }
				catch (FileNotFoundException e) { e.printStackTrace(); continue; }
				
				//---- actual rewrite logic goes here ----
				src = Rewrite.prependGWPTemplate(src);
				//----
				
				try { target.edit(title, src, "Adding {{GW+}} template", true); }
				catch (LoginException e) { e.printStackTrace(); continue; }
				log("Successfully edited "+title+": "+(titles.indexOf(title)+1)+"/"+titles.size());
				
				// we should read-in the latest version in case a different process has edited it
//				try { completed = (List<String>) Serialize.in("completed.list.string.ser"); }
//				catch (Exception e) { /* do nothing */ }
				completed.add(title);
				Serialize.out("completed.list.string.ser", new ArrayList<String>(completed));
				
			} catch (Exception e) { 
				e.printStackTrace();
				
				// as above, we should read-in the latest version
//				try { failed = (List<String>) Serialize.in("failed.list.string.ser"); }
//				catch (Exception e2) {/* do nothing */};
				failed.add(title);
				Serialize.out("failed.list.string.ser", new ArrayList<String>(failed));
				continue;
			}
		}
		return completed;
	}
	
	@Deprecated
	public List<String> run(List<String> titles) {
		for (String title : titles) {
			try {
				if (completed.contains(title))
					continue;
				if (failed.contains(title))
					continue;
				
				String src = "";
				try { src = target.getPageText(title); }
				catch (FileNotFoundException e) { e.printStackTrace(); continue; }
				
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
					if (!link.contains("::") && !link.contains("Category:") && !target.exists(linkTitle)[0]) {
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
				try { target.edit(title, src, "Link fixes and maintenance.", true); }
				catch (LoginException e) { e.printStackTrace(); continue; }
				log("Successfully edited "+title+": "+(titles.indexOf(title)+1)+"/"+titles.size());
				completed.add(title);
				Serialize.out("edited.list.string.ser", new ArrayList<String>(completed));
				
			} catch (Exception e) {
				failed.add(title);
				Serialize.out("failed.list.string.ser", new ArrayList<String>(completed));
				continue;
			}
		} 
		return completed;
	}

	private static void log(String message) {
		System.out.println(message);
	}

}

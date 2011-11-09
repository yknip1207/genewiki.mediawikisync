package edu.scripps.bulk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.CredentialNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import org.genewiki.api.Wiki;
import org.genewiki.util.Serialize;

import edu.scripps.resources.AnnotationDatabase;
import edu.scripps.sync.RDFCategory;
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
	public static void main(String[] args) throws FailedLoginException, IOException, CredentialNotFoundException, SQLException {
		Wiki target = new Wiki("genewikiplus.org", "");
		target.setLogLevel(Level.WARNING);
		Wiki source = new Wiki();
		source.setLogLevel(Level.WARNING);
		Wiki edited = new Wiki();
		source.setMaxLag(0);
		source.setUsingCompressedRequests(false);
		source.login("genewikiplus", args[0].toCharArray());
		target.setUsingCompressedRequests(false);
		target.login("SyncBot", args[1].toCharArray());
		target.setThrottle(0);
		edited.setMaxLag(0);
		edited.login("genewikiplus_extended", args[2].toCharArray());
		List<String> previouslyEdited = new ArrayList<String>();
		try {previouslyEdited = (ArrayList<String>) Serialize.in("completed.list.string.ser"); }
		catch (Exception e) { previouslyEdited = new ArrayList<String>(); }
		List<String> failed = new ArrayList<String>();
		try {failed = (ArrayList<String>) Serialize.in("failed.list.string.ser"); }
		catch (Exception e) { failed = new ArrayList<String>(); }
		Crawler crawler = new Crawler(source, target, previouslyEdited, failed);
//		ArrayList<String> all = new ArrayList<String>(crawler.findAllArticles(target));
//		List<String> watchlist = Arrays.asList(source.getRawWatchlist());
//		all.removeAll(watchlist);
//		all.removeAll(Arrays.asList(target.whatTranscludesHere("Template:GW+")));
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
//		Set<RDFCategory> imports = RDFCategory.getCategories("file:/Users/eclarke/Downloads/doid.owl", true);
//		Set<String> symbols = new AnnotationDatabase("annotations.db").getAllSymbols();
//		Set<String> modified = crawler.createRedirectsForGeneSymbols(symbols, -1);
//		Files.write(modified.toString(), new File("modified.set"), Charset.forName("UTF-8"));
//		List<String> SnpPages = findAllSNPediaPages(target);		
		List<String> GenePages = findAllGenePages(target);
		crawler.edit(GenePages, -1, true);
//		crawler.edit(SnpPages, -1, false);
//		List<String> diseaseCategories = findAllDiseaseOntologyCategories(target);
//		System.out.println(diseaseCategories.size());
		
	}
	
	public static List<String> findAllArticles(Wiki wiki) {
		try {
			// 0 is article namespace
			return Arrays.asList(wiki.listPages("", Wiki.NO_PROTECTION, 0));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public static List<String> findAllTemplates(Wiki wiki) {
		try {
			// 10 is template namespace
			return Arrays.asList(wiki.listPages("", Wiki.NO_PROTECTION, 10));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public static List<String> findAllCategories(Wiki wiki) {
		try {
			// 14 is category namespace
			return Arrays.asList(wiki.listPages("", Wiki.NO_PROTECTION, 14));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public static String getCategoryForDOID(String doid, Wiki wiki) {
		try {
			Map<String, List<String>> category = wiki.askQuery("hasDOID", doid);
			if (category.keySet().size() > 1) {
				throw new RuntimeException("More than one category maps to that DOID.");
			}
			String title = null;
			for (String cat : category.keySet()) {
				title = cat.replace("Category:", "");
			}
			return title;
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	public static List<String> findAllDiseaseOntologyCategories(Wiki wiki) {
		try {
			return Arrays.asList(wiki.getCategoryMembers("Disease_Ontology_Term", 14));			
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public static List<String> findAllGenePages(Wiki wiki) {
		try {
			return Arrays.asList(wiki.whatTranscludesHere("Template:GNF_Protein_box", 0));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	public static List<String> findAllSNPediaPages(Wiki wiki) {
		try {
			return Arrays.asList(wiki.whatTranscludesHere("Template:Snpedia_attrib", 0));
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}
	
	/**
	 * Creates a redirect page for all gene symbols that do not currently
	 * @param symbols
	 * @param optionalLimit
	 * @return
	 */
	public Set<String> createRedirectsForGeneSymbols(Set<String> symbols, int optionalLimit) {
		int completedThisRound = 0;
		// We record the pages that had the name of gene symbols but were not gene pages
		// to add this awareness to the ongoing synchronization re-writes
		Set<String> modifiedNonGeneArticles = new HashSet<String>();
		for (String sym : symbols) {
			// If we've attempted this before, skip it.
			if (completed.contains(sym)) {
				continue;
			} else if (failed.contains(sym)) {
				continue;
			}
			
			try {
				// If the page exists already and is a gene page (not a disambig page), 
				// do nothing. If it exists but isn't a gene page, make it a redirect. If
				// it doesn't exist, create a new redirect.			
				if (target.exists(sym)[0]) {
					String text = target.getPageText(sym);
					if (!text.contains("{{PBB|geneid=")) {
						AnnotationDatabase anno = new AnnotationDatabase("annotations.db");
						String title = anno.getPageTitle(sym); 
						if (title != null) {
							text = "#REDIRECT [["+title+"]]\n\n"+text;
							target.edit(sym, text, "Altered to become redirect to "+title+".", false);
							modifiedNonGeneArticles.add(sym);
						}
					}
					completedThisRound++;
				} else {
					AnnotationDatabase anno = new AnnotationDatabase("annotations.db");
					String title = anno.getPageTitle(sym); 
					// Make sure the article exists before redirecting.
					if (title != null) {
						String text = "#REDIRECT [["+title+"]]\n";
						target.edit(sym, text, "Created redirect to "+title+".", false);
					}
					completedThisRound++;
				}
				completed.add(sym);
				Serialize.out("completed.list.str.ser", new ArrayList<String>(completed));
				System.out.printf("Completed %d (%d this round) of %d edits.\n", completed.size(), completedThisRound, symbols.size());
				
				// If the optional limit is set and we've hit it, break out of the for loop
				if (optionalLimit != -1 && completedThisRound >= optionalLimit) {
					System.out.println("Optional limit reached, exiting.");
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
				failed.add(sym);
				Serialize.out("failed.list.str.ser", new ArrayList<String>(failed));
				System.out.printf("Failed to update gene symbol %s.", sym);
			} catch (SQLException e) {
				e.printStackTrace();
				failed.add(sym);
				Serialize.out("failed.list.str.ser", new ArrayList<String>(failed));
				System.out.printf("Failed to update gene symbol %s.", sym);
			} catch (LoginException e) {
				e.printStackTrace();
				failed.add(sym);
				Serialize.out("failed.list.str.ser", new ArrayList<String>(failed));
				System.out.printf("Failed to update gene symbol %s.", sym);
			}
		}
		return modifiedNonGeneArticles;
	}
	
	/**
	 * Create categories en masse from an imported ontology or other source. For testing,
	 * the user can specify an optional limit, which, once hit, will end the category creation.
	 * To disable the limit, set optionalLimit to -1.
	 * @param cats a set of categories to create
	 * @param optionalLimit number of categories to create this round. disabled if set to -1. 
	 * @return
	 */
	public List<String> createCategories(Set<RDFCategory> cats, int optionalLimit) {
		int completedThisRound = 0;
		for (RDFCategory cat : cats) {
			// If we've attempted this before, successfully or not, skip it
			if (completed.contains(cat.Name())) {
				continue;
			} else if (failed.contains(cat.Name())) {
				continue;
			}
			
			// Otherwise, proceed with category creation
			try {
				RDFCategory.createCategoryOnWiki(cat, target);
				completed.add(cat.Name());
				Serialize.out("completed.list.str.ser", new ArrayList<String>(completed));
				completedThisRound++;
				System.out.printf("Completed %d (%d this round) of %d edits.\n", completed.size(), completedThisRound, cats.size());
			} catch (IOException e) {
				e.printStackTrace();
				failed.add(cat.Name());
				Serialize.out("failed.list.str.ser", new ArrayList<String>(failed));
				System.out.printf("Failed to update category %s.", cat.Name());
			}
			
			// If the optional limit is set and we've hit it, break out of the for loop
			if (optionalLimit != -1 && completedThisRound >= optionalLimit) {
				System.out.println("Optional limit reached, exiting.");
				break;
			}
		}
		return completed;
	}
	
	public List<String> edit(List<String> titles, int optionalLimit, boolean isGene) {
		int completedThisRound = 0;
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
				log("Removing diseases from "+title+".");
				String src2 = Rewrite.removeDiseaseOntologyCategoryMembership(src, title, "annotations.db", target, isGene);
				log("Altering detached annotations in "+title+".");
				src2 = Rewrite.appendDetachedAnnotations(src2, title, "annotations.db", target, isGene);
				
				
				//----
				
				if (!src2.equals(src)) {
					try { target.edit(title, src2, "Removing misplaced categories and reconfiguring annotations on "+title, true); }
					catch (LoginException e) { e.printStackTrace(); continue; }
				} else {
					log("No changes were made to the source text; moving on.");
				}
				log(String.format("Completed %d (%d this round) of %d edits.\n", completed.size(), completedThisRound, titles.size()));
				
				// we should read-in the latest version in case a different process has edited it
//				try { completed = (List<String>) Serialize.in("completed.list.string.ser"); }
//				catch (Exception e) { /* do nothing */ }
				completed.add(title);
				Serialize.out("completed.list.string.ser", new ArrayList<String>(completed));
				completedThisRound++;
			} catch (Exception e) { 
				e.printStackTrace();
				
				// as above, we should read-in the latest version
//				try { failed = (List<String>) Serialize.in("failed.list.string.ser"); }
//				catch (Exception e2) {/* do nothing */};
				failed.add(title);
				Serialize.out("failed.list.string.ser", new ArrayList<String>(failed));
				completedThisRound++;
				continue;
			}
			
			// If the optional limit is set and we've hit it, break out of the for loop
			if (optionalLimit != -1 && completedThisRound >= optionalLimit) {
				System.out.println("Optional limit reached, exiting.");
				break;
			}
		}
		log("Finished.");
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

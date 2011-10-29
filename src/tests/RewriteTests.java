package tests;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.Charset;

import org.genewiki.api.Wiki;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import edu.scripps.sync.GeneWikiSync;
import edu.scripps.sync.Rewrite;

public class RewriteTests{
	
	Wiki 	source;
	Wiki 	target;
	int 	period;
	boolean rewrite;
	String 	defaultArticle, pre_testMirrored, post_testMirrored, pre_testGWPGene, post_testGWPGene,
			pre_testGWPDisease, post_testGWPDisease, pre_testGWP, post_testGWP, pre_testSWL, post_testSWL,
			pre_testLinks, post_testLinks;
	Charset utf8;

	@Before
	public void setUp() throws Exception {
		source = null;
		target = new Wiki("genewikiplus.org", "");
		target.setUsingCompressedRequests(false);
		period = 5;
		rewrite = true;
		utf8	= Charset.forName("UTF-8");
		defaultArticle		= Files.toString(new File("tests/pln.wiki"), utf8);
		pre_testMirrored 	= defaultArticle;
		post_testMirrored 	= Files.toString(new File("tests/pln-mirrored.wiki"), utf8); 
		pre_testGWPGene		= Files.toString(new File("tests/cdk2-gwp-pre.wiki"), utf8);
		post_testGWPGene	= Files.toString(new File("tests/cdk2-gwp-post.wiki"), utf8);
		pre_testGWPDisease	= Files.toString(new File("tests/diabetes-mellitus-type-2-pre.wiki"), utf8);
		post_testGWPDisease	= Files.toString(new File("tests/diabetes-mellitus-type-2-post.wiki"), utf8);
		pre_testGWP			= Files.toString(new File("tests/generic-pre.wiki"), utf8);
		post_testGWP		= Files.toString(new File("tests/generic-post.wiki"), utf8);
		pre_testSWL			= defaultArticle;
		post_testSWL		= Files.toString(new File("tests/pln-swl.wiki"), utf8);
		pre_testLinks		= defaultArticle;
		post_testLinks		= Files.toString(new File("tests/pln-links.wiki"), utf8);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void prependMirroredTemplateTest() {
		String result = Rewrite.prependMirroredTemplate(pre_testMirrored);
		assertEquals(post_testMirrored, result);
	}
	
	@Test
	public void appendGWPTemplateTest1() {
		String result = Rewrite.appendGWPTemplate(pre_testGWPGene, "Cyclin-dependent kinase 2", target);
		assertEquals(post_testGWPGene, result);
	}
	
	@Test
	public void appendGWPTemplateTest2() {
		String result = Rewrite.appendGWPTemplate(pre_testGWPDisease, "Diabetes mellitus type 2", target);
		assertEquals(post_testGWPDisease, result);
	}
	
	@Test
	public void appendGWPTemplateTest3() {
		String result = Rewrite.appendGWPTemplate(pre_testGWP, "1995 (year)", target);
		assertEquals(post_testGWP, result);
	}
	
	@Test
	public void convertSWLTemplatesTest() {
		String result = Rewrite.convertSWLTemplates(pre_testSWL);
		System.out.println(result);
		assertEquals(post_testSWL, result);
	}
	
	@Test
	public void fixLinksTest() {
		String testLink1 		= "[[this_is::already_semantic]]";
		String testLink2_pre 	= "[[this link|does not exist]]";
		String testLink2_post 	= "[[wikipedia:this link|does not exist]]";
		String testLink3		= "[[Category:ignore]]";
		String testLink4		= "[[Image:just kidding]]";
		assertEquals(testLink1, Rewrite.fixLinks(testLink1, target));
		assertEquals(testLink2_post, Rewrite.fixLinks(testLink2_pre, target));
		assertEquals(testLink2_post, Rewrite.fixLinks(testLink2_post, target));
		assertEquals(testLink3, Rewrite.fixLinks(testLink3, target));
		assertEquals(testLink4, Rewrite.fixLinks(testLink4, target));
	}

}

class MockWiki extends Wiki {
	
	private static final long serialVersionUID = 1L;

	@Override	
	public boolean[] exists(String... titles) {
		boolean[] results = new boolean[titles.length];
		for (int i=0; i<titles.length; i++) {
			String title = titles[i];
			if (title.startsWith("!")) {
				results[i] = false;
			} else {
				results[i] = true;
			}
		}
		return results;
	}
	
}
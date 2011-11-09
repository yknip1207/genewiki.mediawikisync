package tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.genewiki.api.Wiki;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import edu.scripps.sync.Rewrite;

public class RewriteTests{
	
	Wiki 	source;
	Wiki 	target;
	MockWiki	mockTarget;
	int 	period;
	boolean rewrite;
	String 	defaultArticle, pre_testMirrored, post_testMirrored, pre_testGWPGene, post_testGWPGene,
			pre_testGWPDisease, post_testGWPDisease, pre_testGWP, post_testGWP, pre_testSWL, post_testSWL,
			pre_testLinks, post_testLinks, pre_cleanUp;
	Charset utf8;

	@Before
	public void setUp() throws Exception {
		source = null;
		target = new Wiki("genewikiplus.org", "");
		target.setUsingCompressedRequests(false);
		mockTarget = new MockWiki("genewikiplus.org", "");
		mockTarget.setUsingCompressedRequests(false);
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
		pre_cleanUp			= Files.toString(new File("tests/cleanupText.wiki"), utf8);
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
		assertEquals(post_testSWL, result);
	}
	
	@Test
	public void appendDetachedAnnotationsTest() {
		String result 	= Rewrite.appendDetachedAnnotations("", "Rs6311", "annotations.db", mockTarget, false);
		String expected = 	"\n{{CAnnotationsStart}}\n" +
							"*  [[is_associated_with_disease::Rheumatoid arthritis]]\n" +
							"*  [[is_associated_with_disease::Chronic fatigue syndrome]]\n" +
							"*  [[is_associated_with_disease::Autistic disorder]]\n" +
							"*  [[is_associated_with_disease::Obesity]]\n" +
							"{{CAnnotationsEnd}}\n";
		assertEquals(expected, result);
		System.out.println(result);
		assertEquals(expected, Rewrite.appendDetachedAnnotations(result, "Rs6311", "annotations.db", mockTarget, false));
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
	
	@Test
	public void cleanUpTests() {
		String test 	= "[[wikipedia:File:something [[is_associated_with::something_else]]]] [[is_associated_with::something]]";
		String correct 	= "[[File:something [[something_else]]]] [[is_associated_with::something]]"; 
		assertEquals(correct, Rewrite.cleanUp(test));
		String postCleanUp = Rewrite.cleanUp(pre_cleanUp);
		Matcher GWPFragment = Pattern.compile("\\{\\{GW\\+[^}|]").matcher(postCleanUp);
		assertFalse(GWPFragment.find());
		assertFalse(postCleanUp.contains("wikipedia:"));
		assertTrue(postCleanUp.contains("is_associated_with::"));
		assertEquals(postCleanUp, Rewrite.cleanUp(postCleanUp));
	}
	
	@Test
	public void removeDiseaseOntologyCategoryMembershipTest() {
		String pre = "[[Category:NotDOTerm]]\n[[Category:Adenocarcinoma]]\n[[Category:Benign mesothelioma]]\n";
		assertEquals("[[Category:NotDOTerm]]\n", Rewrite.removeDiseaseOntologyCategoryMembership(pre, "Mesothelin", "annotations.db", mockTarget, true));
	}
	
	@Test
	public void categorizeDiseasePageTest() {
		mockTarget.clearEdits();
		assertTrue(Rewrite.categorizeDiseasePage("Congenital heart defect", mockTarget));
		System.out.println(mockTarget.lastEdit);
		assertTrue(mockTarget.lastEdit.contains("\n[[Category:Congenital heart defect]]\n{{GW+|disease}}\n"));
	}

}

class MockWiki extends Wiki {
	
	private static final long serialVersionUID = 1L;
	
	public String lastEdit;

	public MockWiki(String root, String scriptPath) {
		super(root, scriptPath);
		lastEdit = "";
	}
	
	@Override
	public String[] getCategories(String title) {
		return new String[0];
	}
	
	@Override	
	public boolean[] exists(String... titles) {
		boolean[] results = new boolean[titles.length];
		for (int i=0; i<titles.length; i++) {
			String title = titles[i];
			if (!title.startsWith("!")) {
				results[i] = false;
			} else {
				results[i] = true;
			}
		}
		return results;
	}
	
	@Override
	public void edit(String title, String src, String summary, boolean isMinor) {
		lastEdit = lastEdit+"\n*  "+src;
	}
	
	public void clearEdits() {
		lastEdit = "";
	}
	
}
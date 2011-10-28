package tests;

import static org.junit.Assert.*;

import org.genewiki.api.Wiki;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.scripps.sync.GeneWikiSync;

public class GeneWikiRewriteTests {
	
	Wiki 	source;
	Wiki 	target;
	int 	period;
	boolean rewrite;
	String preArticle, postArticle;

	@Before
	public void setUp() throws Exception {
		source = null;
		target = new MockWiki();
		period = 5;
		rewrite = true;
		
		preArticle = "Some article with [[links|that do exist]] as well as [[!links|that do not exist]], " +
				"as well as some {{SWL|target=something|type=relationship|label=SWL templates}} that vary " +
				"{{SWL|type=Type|label=Label|target=Target}} in order and {{SWL|type=has|target=no label}} parameters.";
		
		postArticle = "Some article with [[is_associated_with::links|that do exist]] as well as [[wikipedia:!links|that do not exist]], " +
				"as well as some [[relationship::something|SWL templates]] that vary " +
				"[[Type::Target|Label]] in order and [[has::no label]] parameters.";
		
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void rewriteTest() {
		GeneWikiSync gws = new GeneWikiSync(source, target, period, rewrite);
		assertEquals(postArticle, gws.rewriteArticleContent(preArticle, null));
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
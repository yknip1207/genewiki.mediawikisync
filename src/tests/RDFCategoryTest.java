package tests;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.genewiki.api.Wiki;
import org.junit.Before;
import org.junit.Test;

import edu.scripps.sync.RDFCategory;

public class RDFCategoryTest {
	
	static RDFCategory 	test;
	static MockWiki2	mockWiki;
	
	@Before
	public void setUp() {
		Set<String> parents = new HashSet<String>(Arrays.asList("Parent1", "Parent2", "Parent3"));
		Set<String> synonyms = new HashSet<String>(Arrays.asList("Syn1", "Syn2", "Syn3"));
		String description = "This is a description of a test category.";
		String doid = "DOID_1234";
		String uri	= "someURI.org/DOID_1234";
		String title = "TestCategory";
		test = new RDFCategory(doid, uri, title, parents, description, synonyms);
		mockWiki = new MockWiki2();
	}

	@Test
	public void testToString() {
		String expected	= "This is a description of a test category.\n[[equivalent URI:=someURI.org/DOID_1234]]\n" +
				"[[hasDOID::DOID_1234]]\n[[Category:Parent1]]\n[[Category:Parent3]]\n[[Category:Parent2]]\n";
		assertEquals(expected, test.toString());
	}
	
	@Test
	public void testToString2() throws IOException {
		RDFCategory.createCategoryOnWiki(test, mockWiki);
		String expected	= "This is a description of a test category.\n[[equivalent URI:=someURI.org/DOID_1234]]\n" +
				"[[Category:Parent1]]\n[[Category:Parent2]]\n[[Category:Parent3]]\n\n{{#set:hasDOID=DOID_1234}}\n";
		assertEquals(expected, mockWiki.editedContent);
	}

}

class MockWiki2 extends Wiki {
	
	private static final long serialVersionUID = 1L;
	public String editedContent;

	@Override
	public boolean[] exists(String... titles) throws IOException {
		boolean[] returned = new boolean[titles.length];
		for (int i=0; i<titles.length; i++) {
			returned[i] = true;
		}
		return returned;
	}
	
	@Override
	public String getPageText(String title) {
		return "This is a description of a test category.\n[[equivalent URI:=someURI.org/DOID_1234]]\n" +
				"[[Category:Parent1]]\n[[Category:Parent2]]\n";
	}
	
	@Override
	public String[] getCategories(String title) {
		return new String[]{"Parent1", "Parent2"};
	}
	
	@Override 
	public void edit(String title, String text, String summary, boolean minor) {
		editedContent = text;
	}
	
}
package tests;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.scripps.resources.AnnotationDatabase;

public class AnnotationDatabaseTests {

	static AnnotationDatabase anno;
	
	@Before
	public void setUp() throws Exception {
		anno = new AnnotationDatabase("annotations.db");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void getAssociatedDiseaseTest() throws SQLException {
		String expected = "Alzheimer's disease";
		String result = anno.getAssociatedDisease("Alzheimer's disease");
		assertEquals("Results should have been \"Alzheimer's disease\".", expected, result);
		
		String result2 = anno.getAssociatedDisease("Insulin");
		assertEquals("Results should be null for Insulin (not a disease).", null, result2);
	}

	@Test
	public void getDiseaseAssociatedWithGeneTest() throws SQLException {
		String geneId = "3630";
		String pageTitle = "Insulin";
		List<String> expected = Arrays.asList(
				"Alzheimer's disease",
				"hypertension",
				"polycystic ovary syndrome",
				"hypoglycemic coma",
				"diabetic ketoacidosis",
				"chronic rejection of renal transplant",
				"anovulation",
				"insulinoma",
				"hyperglycemia",
				"amyloidosis",
				"diabetes mellitus",
				"diabetes mellitus type 2",
				"diabetes mellitus type 1",
				"obesity",
				"hypoglycemia");
		List<String> actual_gene = new ArrayList<String>(anno.getDiseaseAssociatedWithGene(geneId, null));
		List<String> actual_page = new ArrayList<String>(anno.getDiseaseAssociatedWithGene(null, pageTitle));
		Collections.sort(expected);
		Collections.sort(actual_gene);
		Collections.sort(actual_page);
		assertEquals("Diseases returned from gene ID did not match expected.", expected, actual_gene);
		assertEquals("Diseases returned from gene ID and page title did not match.", actual_gene, actual_page);
	}
	
	@Test
	public void getDiseaseAssociatedWithSNPTest() throws SQLException {
		String snpAcc = "Rs10012";
		List<String> expected 	= Arrays.asList("colorectal cancer", "malignant neoplasm");
		List<String> actual		= new ArrayList<String>(anno.getDiseaseAssociatedWithSNP(snpAcc));
		Collections.sort(expected);
		Collections.sort(actual);
		assertEquals("Diseases returned from test SNP "+snpAcc+" did not match expected.", expected, actual);
	}
	
	@Test
	public void getAllLinkedDiseaseTermsTest() throws SQLException {
		Set<String> returned = anno.getAllLinkedDiseaseTerms();
		assertTrue("Expected size of set of all linked diseases to be at least 1180.", returned.size() >= 1180);
	}
	
}

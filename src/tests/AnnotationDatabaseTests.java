package tests;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
		List<String> actual_gene = new ArrayList<String>(anno.getDiseasesAssociatedWithGene(geneId, null, false).values());
		List<String> actual_page = new ArrayList<String>(anno.getDiseasesAssociatedWithGene(null, pageTitle, false).values());
		Set<String> filtered_actual_gene = new HashSet<String>(anno.getDiseasesAssociatedWithGene(geneId, null, true).values());
		Collections.sort(expected);
		Collections.sort(actual_gene);
		Collections.sort(actual_page);
		assertEquals("Diseases returned from gene ID did not match expected.", expected, actual_gene);
		assertEquals("Diseases returned from gene ID and page title did not match.", actual_gene, actual_page);
		assertFalse("Failed to filter out less-specific diseases: set contains 'diabetes mellitus' " +
				"when more specific terms should be available.", filtered_actual_gene.contains("diabetes mellitus"));
	}
	
	@Test
	public void getDiseaseAssociatedWithSNPTest() throws SQLException {
		String snpAcc = "Rs10012";
		List<String> expected 	= Arrays.asList("colorectal cancer", "malignant neoplasm");
		List<String> actual		= new ArrayList<String>(anno.getDiseaseAssociatedWithSNP(snpAcc, false).values());
		List<String> fActual	= new ArrayList<String>(anno.getDiseaseAssociatedWithSNP(snpAcc, true).values());
		Collections.sort(expected);
		Collections.sort(actual);
		Collections.sort(fActual);
		assertEquals("Diseases returned from test SNP "+snpAcc+" did not match expected.", expected, actual);
		assertFalse("Failed to filter out less-specific diseases: set contained 'malignant neoplasm' with " +
				"'colorectal cancer'.", fActual.contains("malignant neoplasm"));
	}
	
	@Test
	public void getAllLinkedDiseaseTermsTest() throws SQLException {
		Set<String> returned = anno.getAllLinkedDiseaseTerms();
		assertTrue("Expected size of set of all linked diseases to be at least 1180.", returned.size() >= 1180);
	}
	
	@Test
	public void getPageTitleTest() throws SQLException {
		String title = anno.getPageTitle("PLN");
		assertEquals("Phospholamban", title);
	}
	
	@Test
	public void getDiseasePagesTest() throws SQLException {
		Set<String> returned = anno.getDiseasePages();
		System.out.println(returned.toString());
		assertTrue(returned.size() == 915);
	}
	
}

package tests;

import static org.junit.Assert.*;

import java.sql.SQLException;

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
		assertEquals(expected, result);
		
		String result2 = anno.getAssociatedDisease("Insulin");
		assertEquals(null, result2);
	}

}

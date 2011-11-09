package tests;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import org.apache.http.client.HttpResponseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.scripps.resources.NCBOClient;

public class NCBOClientTests {
	
	static String text = "Melanoma is a malignant tumor of melanocytes which are found " +
						 "predominantly in skin but also in the bowel and the eye.";

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws FileNotFoundException, IOException {
		NCBOClient ncbo = new NCBOClient();
		Set<String> annos = ncbo.annotate(text);
		assertTrue(annos.contains("DOID:1909"));
	}
	
	@Test
	public void testInvalidAPIKey() {
		NCBOClient ncbo = new NCBOClient("123");
		try {
			ncbo.annotate(text);
			fail("Should have thrown exception for invalid API key.");
		} catch (HttpResponseException e) {}
	}

}

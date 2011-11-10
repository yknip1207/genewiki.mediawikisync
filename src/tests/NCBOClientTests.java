package tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.HttpResponseException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;

import edu.scripps.resources.NCBOClient;

public class NCBOClientTests {
	
	static String text 	= 	"Melanoma is a malignant tumor of melanocytes which are found " +
							"predominantly in skin but also in the bowel and the eye.";
	static String snp;
	static NCBOClient ncbo;

	@BeforeClass
	public static void setUp() throws Exception {
		snp = Files.toString(new File("tests/Rs6311.wiki"), Charset.forName("UTF-8"));
		ncbo = new NCBOClient();
	}

	@AfterClass
	public static void tearDown() throws Exception {
		try {
			ncbo.close();
		} finally {}
	}

	@Test
	public void test() throws FileNotFoundException, IOException {
		Set<String> annos = ncbo.annotate(text).keySet();
		assertTrue("Missing expected annotation; contains: "+annos.toString(), annos.contains("DOID_1909"));
	}
	
	@Test
	public void testRetainMostSpecific() throws HttpResponseException {
		Map<String, String> results = ncbo.annotate(snp);
		System.out.println(results);
		System.out.println(ncbo.retainMostSpecific(results));
		assertFalse("Found less specific term (arthritis) when more specific term (rheumatoid arthritis) was available.",
				(results.containsKey("DOID_848") && !results.containsKey("DOID_7148")));
	}
	
	@Test
	public void testNCBOClientClose() throws HttpResponseException {
		ncbo.close();
		try {
			ncbo.annotate(text);
			fail("NCBO client should not be accessible after being closed.");
		} catch (RuntimeException e) {
			// should throw an error
		}
	}

	@Test
	public void testInvalidAPIKey() {
		NCBOClient ncbo2 = new NCBOClient("123", "file:doid.owl");
		try {
			System.err.println("|----Testing Invalid API Key ----|");
			ncbo2.annotate(text);
			fail("Should have thrown exception for invalid API key.");
		} catch (HttpResponseException e) {
			System.err.println("|-----------End test-------------|");
		} finally {
			ncbo2.close();
		}
	}

}

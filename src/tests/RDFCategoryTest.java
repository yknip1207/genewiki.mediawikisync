package tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.scripps.sync.RDFCategory;

public class RDFCategoryTest {

	@Test
	public void testToString() {
		List<RDFCategory> categories = new ArrayList<RDFCategory>();
		categories.add(new RDFCategory("Disease", "someURI", null));
		categories.add(new RDFCategory("Some Other Parent", "someURI", null));
		RDFCategory cat = new RDFCategory("TestCategory", "someOtherURI", categories);
		String expected = "[[equivalent URI:=someOtherURI]]\n[[Category:Disease]]\n[[Category:Some Other Parent]]\n";
		assertEquals(expected, cat.toString());
	}
	
	@Test
	public void testToString2() {
		RDFCategory cat = new RDFCategory("TestCategory", "someOtherURI", null);
		String expected = "[[equivalent URI:=someOtherURI]]\n";
		assertEquals(expected, cat.toString());
	}

}

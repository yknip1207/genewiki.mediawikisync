package tests;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.genewiki.api.Wiki;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class askQueryTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws IOException {
		Wiki gwp = new Wiki("genewikiplus.org", "");
		gwp.setUsingCompressedRequests(false);
		Map<String, List<String>> results = gwp.askQuery("in_gene", "GSK3B");
		System.out.println(results.toString());
		assertTrue(true); // Not a real test.
	}

}

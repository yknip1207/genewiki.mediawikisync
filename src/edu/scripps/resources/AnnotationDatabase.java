package edu.scripps.resources;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

public class AnnotationDatabase {
	
	final String dbName;
	
	/**
	 * Creates a new AnnotationDatabase utility that connects to 
	 * the specified database.
	 * @param dbpath the path to the database, including the database name.
	 */
	public AnnotationDatabase(String dbpath) {
		dbName = dbpath;
	}

	/**
	 * Returns the disease ontology term associated with the page title. If there
	 * is no associated term, returns null. 
	 * @param pageTitle title of page on Gene Wiki+
	 * @return DO term associated with page title, or null if no term associated.
	 * @throws SQLException if an error occurs reading or connecting to the database.
	 */
	public String getAssociatedDisease(String pageTitle) throws SQLException {
		// Connect to the database
		Connection 			c = this.connect();
		// Retrieve the DO title associated with the title
		PreparedStatement 	ps = c.prepareStatement("select distinct(do_title) from title_do where title = ?");

		ps.setString(1, pageTitle);
//		ps.addBatch();
		c.setAutoCommit(false);
		ResultSet rs = ps.executeQuery();
		// This will be null if nothing was found
		String result = null;
		while (rs.next()) {
			result = rs.getString("do_title");
		}
		// Close resources
		rs.close();
		ps.close();
		c.close();

		return result;
	}
	
	public Set<String> getDiseaseAssociatedWithGene(String geneId, String pageTitle) throws SQLException {
		// Connect to the database
		Connection 			c 	= this.connect();
		
		// Alter our query depending on if we were supplied a gene id or page title
		PreparedStatement	ps 	= null;
		if (geneId != null) {
			ps = c.prepareStatement("select target_preferred_term from cannos where gene_id=?");
			ps.setString(1, geneId);
		} else if (pageTitle != null) {
			ps = c.prepareStatement("select target_preferred_term from cannos where title like ?");
			ps.setString(1, pageTitle);
		} else {
			throw new IllegalArgumentException("Either gene id or page title must not be null.");
		}
		ResultSet rs = ps.executeQuery();

		// Iterate over the results and add each result to the list
		Set<String> results = new HashSet<String>();
		while (rs.next()) {
			results.add(rs.getString("target_preferred_term"));
		}
		// close resources
		rs.close();
		ps.close();
		c.close();
		
		return results;
	}
	
	public Set<String> getDiseaseAssociatedWithSNP(String snpAcc) throws SQLException {
		// Connect to the database
		Connection 			c 	= this.connect();
		// Create and execute query
		PreparedStatement 	ps 	= c.prepareStatement("select target_preferred_term from snp_cannos where snp_id=?");
		ps.setString(1, snpAcc);
		ResultSet rs = ps.executeQuery();
		
		// Iterate over results and add each to the list
		Set<String> results = new HashSet<String>();
		while (rs.next()) {
			results.add(rs.getString("target_preferred_term"));
		}
		
		// close resources
		rs.close();
		ps.close();
		c.close();
		
		return results;
	}
	
	public Set<String> getLinkedDiseaseTerms(String geneOrSnp) throws SQLException {
		// Detect option
		Boolean gene = null; 
		if (geneOrSnp == "gene") {
			gene = true;
		} else if (geneOrSnp == "snp") {
			gene = false;
		} else {
			throw new IllegalArgumentException("Illegal option: must be 'gene' or 'snp'.");
		}
		// Connect to the database
		Connection 		c 		= this.connect();
		// Create and execute query, choosing table based on option specified
		String 			query 	= "select distinct(target_preferred_term) from "+((gene) ? "cannos" : "snp_cannos");
		Statement		s		= c.createStatement();
		ResultSet 		rs		= s.executeQuery(query);
		
		// Iteratively parse results
		Set<String> 	results	= new HashSet<String>();
		while (rs.next()) {
			results.add(rs.getString("target_preferred_term"));
		}
		rs.close();
		s.close();
		c.close();
		
		return results;
	}
	
	public Set<String> getAllLinkedDiseaseTerms() throws SQLException {
		// Convert all results to sets so we can perform a union on them
		Set<String> genDiseases	= getLinkedDiseaseTerms("gene");
		Set<String> snpDiseases	= getLinkedDiseaseTerms("snp");
		Set<String> union		= Sets.union(genDiseases, snpDiseases);

		return union;
	}
	
	/**
	 * Returns a connection to the database associated with this AnnotationDatabase object.
	 * @return a Connection object representing a direct connection with the database.
	 * @throws SQLException if connection could not be made to the specified database.
	 * @throws RuntimeException if SQLite drivers are not installed for JDBC.
	 */
	public Connection connect() throws SQLException {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("SQlite drivers not installed, aborting connection.");
		}
		return DriverManager.getConnection("jdbc:sqlite:"+dbName);
	}
	
}

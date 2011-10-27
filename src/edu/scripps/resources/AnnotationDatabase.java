package edu.scripps.resources;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
		PreparedStatement 	ps = c.prepareStatement("select distinct(do_title) from title_do where title like ?;");
		ps.setString(1, pageTitle);
		ps.addBatch();
		ResultSet rs = ps.executeQuery();
		// This will be null if nothing was found
		String result = rs.getString("do_title");
		// Close resources
		rs.close();
		ps.close();
		c.close();

		return result;
	}
	
	public List<String> getDiseaseAssociatedWithGene(String geneId, String pageTitle) throws SQLException {
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
		ps.addBatch();
		ResultSet rs = ps.executeQuery();

		// Iterate over the results and add each result to the list
		List<String> results = new ArrayList<String>();
		while (rs.next()) {
			results.add(rs.getString("target_preferred_term"));
		}
		// close resources
		rs.close();
		ps.close();
		c.close();
		
		return results;
	}
	
	public List<String> getDiseaseAssociatedWithSNP(String snpAcc) throws SQLException {
		// Connect to the database
		Connection 			c 	= this.connect();
		// Create and execute query
		PreparedStatement 	ps 	= c.prepareStatement("select target_preferred_term from snp_cannos where snp_id=?");
		ps.setString(1, snpAcc);
		ResultSet rs = ps.executeQuery();
		
		// Iterate over results and add each to the list
		List<String> results = new ArrayList<String>();
		while (rs.next()) {
			results.add(rs.getString("target_preferred_term"));
		}
		
		// close resources
		rs.close();
		ps.close();
		c.close();
		
		return results;
	}
	
	public List<String> getAllLinkedDiseaseTerms() {
		throw new RuntimeException("Not yet implemented.");
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
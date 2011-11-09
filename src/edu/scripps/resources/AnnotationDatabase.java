package edu.scripps.resources;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

public class AnnotationDatabase {
	
	final String dbName;
	
	/**
	 * Creates a new AnnotationDatabase utility that connects to 
	 * the specified database.
	 * @param dbpath the path to the database, including the database name.
	 * @throws IllegalArgumentException if dbpath does not point to a valid file
	 */
	public AnnotationDatabase(String dbpath) {
		if (new File(dbpath).exists()) {
			dbName = dbpath;
		} else {
			throw new IllegalArgumentException("Specified annotation database file not found.");
		}
	}

	/**
	 * Returns the disease ontology term associated with the page title. If there
	 * is no associated term, returns null. 
	 * @param pageTitle title of page on Gene Wiki+
	 * @return DO term associated with page title, or null if no term associated.
	 * @throws SQLException if an error occurs reading or connecting to the database.
	 */
	public String getAssociatedDisease(String pageTitle) throws SQLException {
		Connection 			c 	= this.connect();
		PreparedStatement 	ps 	= c.prepareStatement("select distinct(do_title) from title_do where title = ?");
		ps.setString(1, pageTitle);
		c.setAutoCommit(false);
		ResultSet 			rs 	= ps.executeQuery();
		// Retrieve the disease ontology term associated with the title. The method iterates
		// even though there is only one expected result due to idiosyncracies in JDBC's ResultSet
		// behavior (the ResultSet remains closed unless iterated upon).
		String result = null;
		while (rs.next()) {
			result = rs.getString("do_title");
		}

		ps.close();
		c.close();

		return result;
	}
	
	public Set<String> getDiseasePages() throws SQLException {
		Connection 	c 	= this.connect();
		Statement 	s 	= c.createStatement();
		ResultSet 	rs 	= s.executeQuery("select distinct(title) from title_do");
		
		Set<String> results = new HashSet<String>();
		while (rs.next()) {
			results.add(rs.getString("title"));
		}
		
		s.close();
		c.close();
		
		return results;
		
	}
	
	/**
	 * Returns the set of diseases associated with a given gene, which can be specified by either the 
	 * Entrez gene id or the page title (the other can be left as null). If both are given, the Entrez gene id is used.
	 * If neither are specified (both null), the method throws an IllegalArgumentException. 
	 * @param geneId the entrez gene id, or null if using the page title
	 * @param pageTitle the title of the article on Gene Wiki+. Ignored if gene id is not null.
	 * @param filterRedundant if true, return only the most specific disease terms associated with the gene (i.e. omits 'diabetes mellitus'
	 * in favor of 'diabetes mellitus type 1')
	 * @return set of diseases associated with gene
	 * @throws SQLException if an error occurs reading or connecting to the database.
	 * @throws IllegalArgumentException if both geneId and pageTitle are null
	 */
	public Map<String,String> getDiseasesAssociatedWithGene(String geneId, String pageTitle, boolean filterRedundant) throws SQLException {
		Connection 			c 	= this.connect();		
		// Alter our query depending on if we were supplied a gene id or page title
		PreparedStatement	ps 	= null;
		if (geneId != null) {
			// If "filterRedundant" is true, we limit to only the most specific terms
			ps = c.prepareStatement("select target_acc,target_preferred_term from cannos where gene_id=?" + 
					((filterRedundant) ? "and is_most_specific=1" : ""));
			ps.setString(1, geneId);
		} else if (pageTitle != null) {
			ps = c.prepareStatement("select target_acc,target_preferred_term from cannos where title like ?" +
					((filterRedundant) ? "and is_most_specific=1" : ""));
			ps.setString(1, pageTitle);
		} else {
			throw new IllegalArgumentException("Either gene id or page title must not be null.");
		}
		ResultSet rs = ps.executeQuery();


		Map<String, String> results = new HashMap<String, String>();
		while (rs.next()) {
			results.put(rs.getString("target_acc").replace(':', '_'), rs.getString("target_preferred_term"));
		}

		ps.close();
		c.close();
		
		return results;
	}
	
	/**
	 * Returns the set of diseases associated with a given single nucleotide polymorphism (SNP). 
	 * @param snpAcc the SNP accession number (of the form Rs1234)
	 * @param filterRedundant limit the returned diseases to only the most specific (i.e. lowest possible on
	 * category tree)
	 * @return the set of diseases
	 * @throws SQLException if an error occurs reading or connecting to the database.
	 */
	public Map<String,String> getDiseaseAssociatedWithSNP(String snpAcc, boolean filterRedundant) throws SQLException {

		Connection 			c 	= this.connect();
		PreparedStatement 	ps 	= c.prepareStatement("select target_acc, target_preferred_term from snp_cannos where snp_id=?" +
				((filterRedundant) ? "and is_most_specific=1" : ""));
		ps.setString(1, snpAcc);
		ResultSet rs = ps.executeQuery();
		
		Map<String,String> results = new HashMap<String,String>();
		while (rs.next()) {
			results.put(rs.getString("target_acc").replace(':', '_'), rs.getString("target_preferred_term"));
		}
		
		ps.close();
		c.close();
		
		return results;
	}
	
	/**
	 * Returns the set of linked disease terms for either all genes or all SNPs. The passed string must be
	 * either 'gene', in which case the set of all diseases linked to all genes in Gene Wiki+ is returned,
	 * or 'snp', in which case the set of all diseases linked to all SNPs in Gene Wiki+ is returned. 
	 * If the string is neither of these, an IllegalArgumentException is thrown.
	 * @param geneOrSnp must be either 'gene' or 'snp'; determines which set of linked diseases is returned
	 * @return the set of linked diseases
	 * @throws SQLException if an error occurs reading or connecting to the database
	 * @throws IllegalArgumentException if invalid string passed
	 */
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

		Connection 		c 		= this.connect();
		String 			query 	= "select distinct(target_preferred_term) from "+((gene) ? "cannos" : "snp_cannos");
		Statement		s		= c.createStatement();
		ResultSet 		rs		= s.executeQuery(query);
		
		Set<String> 	results	= new HashSet<String>();
		while (rs.next()) {
			results.add(rs.getString("target_preferred_term"));
		}

		s.close();
		c.close();
		
		return results;
	}
	
	/**
	 * Returns the set of linked disease terms for both genes and SNPs. This method obtains both respective sets
	 * and returns the union of the two sets.
	 * @return the union of the sets of linked diseases for genes and SNPs
	 * @throws SQLException if an error occurs reading or connecting to the database
	 */
	public Set<String> getAllLinkedDiseaseTerms() throws SQLException {
		// Convert all results to sets so we can perform a union on them
		Set<String> genDiseases	= getLinkedDiseaseTerms("gene");
		Set<String> snpDiseases	= getLinkedDiseaseTerms("snp");
		Set<String> union		= Sets.union(genDiseases, snpDiseases);

		return union;
	}
	
	/**
	 * Returns the corresponding page title for a human gene symbol. If the article does not exist,
	 * returns <tt>null</tt>.
	 * @param gene_sym HUGO gene symbol
	 * @return corresponding page title, if exists; if it does not exist, value is null
	 * @throws SQLException if an error occurs reading or connecting to the database
	 */
	public String getPageTitle(String gene_sym) throws SQLException {
		Connection 			c	= this.connect();
		PreparedStatement 	ps 	= c.prepareStatement("select gene_wiki_title from gene_id_map where gene_symbol=?");
		ps.setString(1, gene_sym);
		ResultSet 			rs 	= ps.executeQuery();
		

		String title = null;
		// Though we're only expecting one result, we iterate through it because this JDBC implementation
		// seems to require it...?
		while (rs.next()) {
			title = rs.getString("gene_wiki_title");
		}
		
		ps.close();
		c.close();
		
		return title;
		
	}
	
	public Set<String> getAllSymbols() throws SQLException {
		Connection 	c 	= this.connect();
		Set<String> sym	= new HashSet<String>(); 
		Statement 	s	= c.createStatement();
		ResultSet	rs	= s.executeQuery("select gene_symbol from gene_id_map");
		while (rs.next()) {
			sym.add(rs.getString("gene_symbol"));
		}
		return sym;
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

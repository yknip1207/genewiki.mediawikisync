package edu.scripps.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static java.lang.String.format;

import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import org.genewiki.api.Wiki;
import org.genewiki.api.Wiki.User;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <p>SyncService manages the persistence and scheduling of the Wikipedia => GeneWiki+ sync.
 * It does this using a ScheduledExecutor that launches a Sync object
 * at a rate described by the specified period. 
 * The main() method will prompt for passwords if none are specified on the command line.
 * <p>A properties file with the usernames for wikipedia and genewiki+,
 * as well as the current location of genewiki+, is required. The location of this
 * file can be specified on the command line. If it is not, the program looks in the
 * local directory for a "gwsync.conf" file; if this doesn't exist, the program prompts
 * for the user to enter the path to the file.
 * <p> The program will attempt to run continuously, restarting the Sync thread if it fails.
 * However, to prevent a non-recoverable situation triggering rapid restart-and-failures, it
 * will exit after 7 such rapid failures, sending an alert email to recipients specified in 
 * the configuration file.
 * @author eclarke
 *
 */
public class SyncScheduler {
	
	private String src_password;
	private final Wiki source;
	private String tgt_password;
	private final Wiki target;
	private final Properties properties;
	
	private static boolean debug = false;	// this allows stack traces to print to stdout
	
//	private final String alertSubject = "GeneWiki Sync Service has fallen and can't get up.";

	/**
	 * Entry point for running the program standalone from a command-line.
	 * @param args
	 * @throws IOException 
	 * @throws FailedLoginException 
	 */
	public static void main(String[] args) throws IOException, FailedLoginException {
		OptionParser parser = new OptionParser();
		OptionSpec<String> srcPassOption = parser.accepts("s", "source MediaWiki password")
				.withRequiredArg().ofType(String.class).describedAs("password");
		OptionSpec<String> tgtPassOption = parser.accepts("t", "target MediaWiki password")
				.withRequiredArg().ofType(String.class).describedAs("password");
		OptionSpec<File> configs = parser.accepts("c", "configuration file")
				.withRequiredArg().ofType(File.class).describedAs("config file");
		parser.accepts("d", "display stack traces");
		parser.accepts("h", "display help (this message)");
		
		OptionSet options = null;
		try {
			options = parser.parse(args);
		} catch (OptionException e) {
			System.err.println("Invalid options or missing option arguments.");
			parser.printHelpOn(System.err);
			System.exit(1);
		}
		
		// Display help
		if (options.has("h")) {
			System.err.println("Can be run with or without the following options (omitting options will prompt for missing info):");
			parser.printHelpOn(System.err);
			System.exit(0);
		}
			
		
		String srcPassword, tgtPassword;
		File configFile;
		
		// Parse Wikipedia password
		if (options.has(srcPassOption)) {
			srcPassword = options.valueOf(srcPassOption);
		} else {
			srcPassword = readln("Enter source MediaWiki password:");
			if (srcPassword.equals("")) {
				System.err.println("Password cannot be blank.");
				System.exit(1);
			}
				
		}
		
		// Parse GeneWiki+ password
		if (options.has(tgtPassOption)) {
			tgtPassword = options.valueOf(tgtPassOption);
		} else {
			tgtPassword = readln("Enter target MediaWiki password:");
			if (tgtPassword.equals("")) {
				System.err.println("Password cannot be blank.");
				System.exit(1);
			}
		}
		
		// Parse configs file
		if (options.has(configs)) {
			configFile = options.valueOf(configs);
		} else {
			if (!(configFile = new File("SyncService.conf")).exists()) {
				configFile = new File(readln("Enter config file location (absolute path):"));
				
			}
		}
		
		// debug mode
		if (options.has("d")) {
			debug = true;
		}

		SyncScheduler sync = new SyncScheduler(srcPassword, tgtPassword, configFile.getCanonicalPath());
		sync.start();
	}
	
	/**
	 * Creates a new SyncService with the specified passwords and configuration options.
	 * @param sourcePassword password for account with desired watchlist on source MediaWiki
	 * @param targetPassword password for account on target MediaWiki
	 * @param configLocation location of gwsync.conf
	 * @throws FileNotFoundException if configuration location is invalid
	 * @throws IOException if configuration cannot be read
	 * @throws FailedLoginException if provided credentials are invalid
	 */
	public SyncScheduler(String sourcePassword, String targetPassword, String configLocation) 
			throws FileNotFoundException, IOException, FailedLoginException {
		this.src_password  = sourcePassword;
		this.tgt_password = targetPassword;
		this.properties = new Properties();
		properties.load(new FileReader(configLocation));
		
		this.source = new Wiki();
		source.setMaxLag(0);
		source.login(
				checkNotNull(properties.getProperty("source.username")), 
				src_password.toCharArray());
		
		this.target = new Wiki(
				checkNotNull(properties.getProperty("target.location")), "");
		target.setMaxLag(0);
		target.setThrottle(0);
		target.setUsingCompressedRequests(false);
		target.login(
				checkNotNull(properties.getProperty("target.username")), 
				tgt_password.toCharArray());
	}
	
	/**
	 * Creates a new SyncService with the specified Wiki objects and configuration file
	 * location.
	 * @param wp Wikipedia Wiki
	 * @param gwp GeneWiki+ Wiki
	 * @param configLocation path to the configs file
	 * @throws FileNotFoundException if the configuration location is invalid
	 * @throws IOException if the configuration cannot be read
	 */
	public SyncScheduler(Wiki wp, Wiki gwp, String configLocation) throws FileNotFoundException, IOException {
		this.source = wp;
		this.target = gwp;
		this.properties = new Properties();
		properties.load(new FileReader(configLocation));
	}
	
	/**
	 * Starts the synchronization with a predefined period (in the config file). If none is defined,
	 * a default period of 5 minutes is used. 
	 * <p> This method attempts to be fail- and fault-safe by monitoring the results
	 * of each Sync thread. If the thread exited with an exception, it will be restarted,
	 * unless it has failed almost immediately after restart (a "quickfail"), multiple times. 
	 * In that case, an alert email will be sent to the recipient specified in the config file, and the
	 * method will return and must be manually restarted. Restarts are throttled so as to not
	 * overwhelm external resources by an incrementing counter (++5s for each quickfail).
	 * This throttle is reset the first time a thread does not quickfail.
	 */
	public void start() {
		Integer period = Integer.parseInt(properties.getProperty("sync.period", "5"));
		boolean rewrite = Boolean.parseBoolean(properties.getProperty("rewrite.article.content", "false"));

		// FIXME replace DefaultSync with your custom Sync implementation!
		Sync sync = new GeneWikiSync(source, target, period, rewrite);

		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
		ScheduledFuture<?> future = executor.scheduleAtFixedRate(sync, 0, period, TimeUnit.MINUTES);
		long launchTime = Calendar.getInstance().getTimeInMillis();
		int quickfail = 0; 
		log(String.format("Starting repeating sync with a period of %d minutes...", period));
		while (true) {
			if (future.isDone()) {
				try {
					// If the process exited in any non-normal way, it'll throw the corresponding exception
					future.get();
				
				} catch (ExecutionException e) {
					
					log(format("Exception encountered: %s", e.getCause().getMessage()));
					if (debug) 
						e.printStackTrace();
					
					long failTime = Calendar.getInstance().getTimeInMillis();
					
					/* If the failure time is within 1.1s of the launchtime, it is likely that something is wrong
					   in the outside environment (network error, etc). We put a throttle on this fail-fast
					   behavior to prevent it from going crazy and overwhelming the JVM
					 */ 
					if (failTime - launchTime < 1100) {
						quickfail++;
						
						// Failing multiple times in this manner is a good indicator that we should just abort
						if (quickfail > 7) {
							log("Failed immediately 7 consecutive times. Bailing out...");
							if (properties.getProperty("send.alert.email") != null);
								sendAlertEmail();
							return;
							
						} else {
							// throttle
							log(format("Failed immediately after launch. Sleeping for %d seconds...", 5*quickfail));
							pause(5000*quickfail);
						}
					} else {
						// if we've recovered, reset the quickfail counter
						quickfail = 0;	
					}
					
					// Relaunches the sync task using the same executor and assigns it to the same future object
					future = executor.scheduleWithFixedDelay(sync, 0, period, TimeUnit.MINUTES);
					// Notes the launch time for comparison against a possible failure time
					launchTime = Calendar.getInstance().getTimeInMillis();
					
				} catch (InterruptedException e) {
					if (debug)
						e.printStackTrace();
					return;
				}
			} else {
				
				// The while loop doesn't need to poll with too high of a frequency
				pause(500);
			}
		}
	}
	
	
	
	/**
	 * Uses MediaWiki's ability to email users to send an alert notifying a user on the source
	 * mediawiki. The details of the message are specified in the configuration file for the SyncService.
	 */
	private void sendAlertEmail() {
		
		String subject 	= properties.getProperty("alert.email.subject");
		String body		= properties.getProperty("alert.email.body");
		User recipient 	= source.new User(properties.getProperty("alert.email.target"));
		
		try {
			source.emailUser(recipient, body, subject, true);
		} catch (IOException e) {
			if (debug)
				e.printStackTrace();
			log(e.getLocalizedMessage());
		} catch (LoginException e) {
			if (debug)
				e.printStackTrace();
			log(e.getLocalizedMessage());
		}
	}
	
	private static String readln(String prompt) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		System.out.print(prompt+" ");
		
		try {
			return br.readLine();
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}

	// TODO Make this a real logger.
	private static void log(String message) {
		System.out.println(message);
	}
	
	private static void pause(long duration) {
		try { Thread.sleep(duration); }
		catch (InterruptedException e) {}
	}
	
}

/**
 * 
 */
package de.bvs.dev;

/**
 * @author michel
 *
 */

/*  $Id: HBCIBatch.java 133 2009-04-23 07:50:52Z kleiner $

 This file is part of hbci4java
 Copyright (C) 2001-2008  Stefan Palme

 hbci4java is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 hbci4java is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;

/**
 * Tool zum Ausführen von HBCI-Jobs, die in einer Batch-Datei definiert werden
 * können.
 * 
 * <pre>
 *  args[0] - configfile für HBCIUtils.init() (Property-File mit Kernel-Parametern
 *            [siehe API-Doc zu org.kapott.hbci.manager.HBCIUtils])
 *    zusätzliche parameter:
 *      client.passport.default=
 *      default.hbciversion=
 * 
 *  args[1] - Dateiname der Antwortdatei für Callbacks
 *    country=
 *    blz=
 *    host=
 *    port=
 *    filter=
 *    userid=
 *    customerid=
 *    sizentry=
 *    passphrase=
 *    softpin=
 *    pin=
 *    tans=
 * 
 *  args[2] - Dateiname der Batch-Datei (jobnamen und parameter siehe
 *            API-Doc zu Paket org.kapott.hbci.GV)
 *    # kommentar
 * 
 *    hljobname:jobid:(props|toString)[:customerid]
 *    hljobparam=paramvalue
 *    hljobparam=&lt;filename
 *    ...
 *    
 *    _lljobname:jobid[:customerid]
 *    _lljobparam=paramvalue
 *    _lljobparam=&lt;filename
 *    ...
 * 
 *    --[:customerid]
 * 
 *  args[3] - Dateiname der Ausgabedatei (mehr dazu siehe unten)
 *    jobid:XXXX
 *    job status:
 *    YYYYYYYYYYY
 *    ZZZZZZZZZZZ
 *    ...
 *    job result:
 *    resultparam=value
 *    resultparam=value
 *  
 *    ...
 *  [args[4]] - Dateiname der Log-Datei
 * </pre>
 * 
 * <p>
 * Alle Jobs, bei deren Ausführung ein Fehler auftritt, werden nicht in die
 * "normale" Ausgabedatei aufgenommen. Statt dessen wird eine zweite Aus-
 * gabedatei erzeugt, die den gleichen Namen wie die "normale" Ausgabedatei plus
 * ein Suffix ".err" hat. In dieser Fehlerdatei wird für jeden fehler- haften
 * Job folgende Struktur geschrieben (String in "<>" wird durch die jeweiligen
 * werte ersetzt):
 * </p>
 * 
 * <pre>
 *    jobid:JOBID 
 *    global status:
 *    allg. fehlermeldung zur hbci-nachricht, in der der job ausgeführt werden sollte
 *    job status:
 *    fehlermeldung zu dem nachrichten-segment, in welchem der job definiert war
 *  
 *    ...
 * </pre>
 * <p>
 * das ist zwar nicht besonders schön, reicht aber vielleicht erst mal (?)
 * Alternativ dazu könnte ich anbieten, dass eine vollständige Fehlernachricht
 * über den *kompletten* Batch-Vorgang in eine Fehlerdatei geschrieben wird,
 * sobald *irgendein* Job nicht sauber ausgeführt wurde (das hätte den Vorteil,
 * dass auch Fehler, die nicht direkt mit einem bestimmten Job in Verbindung
 * stehen [z.B. Fehler bei der Dialog-Initialisierung] ordentlich geloggt
 * werden).
 * </p>
 */
public class HBCIBatch {
	// speziell callback-klasse, um die ausgaben zu reduzieren und um die
	// nutzer-interaktion zu unterbinden, indem alle abgefragten daten auto-
	// tisch übergeben werden (aus args[1])
	private static class MyCallback extends HBCICallbackConsole {
		private Properties answers; // alle spezifizierten antwortdaten

		public MyCallback()  {
			// einlesen der answers-datei
			answers = new Properties();
			
			
			answers.setProperty("secmech", "920");
			answers.setProperty("passphrase", "secret");
			answers.setProperty("pin", "xxxxxx");
			answers.setProperty("country", "DE");
			answers.setProperty("blz", "xxxxxxxxx");
			answers.setProperty("host", "banking.s-fints-pt-th.de/PinTanServlet");
			answers.setProperty("port", "443");
			answers.setProperty("filter", "Base64");
			answers.setProperty("userid", "222222222");
			answers.setProperty("customerid", "222222222");
	
			
		}

		// modifizierte callback-methode, die daten-anfragen "automatisch"
		// beantwortet
		public synchronized void callback(HBCIPassport passport, int reason,
				String msg, int datatype, StringBuffer retData) {
			switch (reason) {
			case NEED_CHIPCARD:
				System.out.println(HBCIUtilsInternal
						.getLocMsg("CALLB_NEED_CHIPCARD"));
				break;
			case NEED_HARDPIN:
				System.out.println(HBCIUtilsInternal
						.getLocMsg("CALLB_NEED_HARDPIN"));
				break;
			case NEED_SOFTPIN:
				retData.replace(0, retData.length(),
						answers.getProperty("softpin"));
				break;

			case NEED_PASSPHRASE_LOAD:
			case NEED_PASSPHRASE_SAVE:
				retData.replace(0, retData.length(),
						answers.getProperty("passphrase"));
				break;

			case NEED_PT_SECMECH:
				retData.replace(0, retData.length(),
						answers.getProperty("secmech"));
				break;

			case NEED_PT_PIN:
				retData.replace(0, retData.length(), answers.getProperty("pin"));
				break;
			case NEED_PT_TAN:
				retData.replace(0, retData.length(), answers.getProperty("tan"));
				break;

			case NEED_COUNTRY:
				retData.replace(0, retData.length(),
						answers.getProperty("country"));
				break;
			case NEED_BLZ:
				retData.replace(0, retData.length(), answers.getProperty("blz"));
				break;
			case NEED_HOST:
				retData.replace(0, retData.length(),
						answers.getProperty("host"));
				break;
			case NEED_PORT:
				retData.replace(0, retData.length(),
						answers.getProperty("port"));
				break;
			case NEED_FILTER:
				retData.replace(0, retData.length(),
						answers.getProperty("filter"));
				break;
			case NEED_USERID:
				retData.replace(0, retData.length(),
						answers.getProperty("userid"));
				break;
			case NEED_CUSTOMERID:
				retData.replace(0, retData.length(),
						answers.getProperty("customerid"));
				break;

			case NEED_SIZENTRY_SELECT:
				retData.replace(0, retData.length(),
						answers.getProperty("sizentry"));
				break;

			case NEED_NEW_INST_KEYS_ACK:
				retData.replace(0, retData.length(), "");
				break;
			case HAVE_NEW_MY_KEYS:
				System.out.println("please restart batch process");
				break;

			case HAVE_INST_MSG:
				HBCIUtils.log(msg, HBCIUtils.LOG_INFO);
				break;

			case NEED_CONNECTION:
			case CLOSE_CONNECTION:
				break;
			}
		}

		// ausgabe der status-meldungen komplett unterbinden
		public synchronized void status(HBCIPassport passport, int statusTag,
				Object[] objs) {
		}
	}

	public static void main(String[] args) throws Exception {
		// initialisieren von hbci4java
		Properties props = new Properties();

		// alt - args[0] deaktiviert
		// InputStream istream=new FileInputStream(args[0]);
		// props.load(istream);
		// istream.close();

		// new - ersetzt file input
		props.setProperty("log.loglevel.default", "2");
		props.setProperty("client.passport.default", "PinTan");
		props.setProperty("client.passport.PinTan.filename", "pintan_hbci4java");
		props.setProperty("client.passport.PinTan.checkcert", "1");
		props.setProperty("client.passport.PinTan.init", "1");
		props.setProperty("client.passport.hbciversion.default", "plus");

		HBCIUtils.init(props, new MyCallback());

		//

		// erzeugen des passport-objektes
		HBCIPassport passport = AbstractHBCIPassport.getInstance();

		try {
			// initialisieren des hbci-handlers für das passport
			String version = passport.getHBCIVersion();
			HBCIHandler handler = new HBCIHandler(
					version.length() != 0 ? version
							: HBCIUtils.getParam("default.hbciversion"),
					passport);

			// new job

			HBCIJob job = null; // job-objekt
			String jobid = "jobsaldo1"; // job-bezeichner
			String customerId = null; // customer-id für job
			Hashtable<String, Object> jobs = new Hashtable<String, Object>(); // liste
																				// aller
																				// jobs
			String jobname = "SaldoReq";
			String resultMode = "props";

			job = handler.newJob(jobname);

			// job in menge der jobs speichern
			jobs.put(jobid, job);
			// ... und ausgabemodus für diesen job merken
			jobs.put(jobid + "_resultMode", resultMode);

			// parameternamen und -wert holen
			String paramName = "my.number";
			String paramValue = "19064829";
			// parameter für aktuellen job setzen
			job.setParam(paramName, paramValue);

			// parameternamen und -wert holen
			paramName = "my.blz";
			paramValue = "83053030";
			// parameter für aktuellen job setzen
			job.setParam(paramName, paramValue);

			// aktuellen job zur job-queue hinzufügen
			job.addToQueue(customerId);

			// alle batch-jobs ausführen
			handler.execute();

			// alle bekannten job-bezeichner (IDs) durchlaufen
			for (Enumeration jobIds = jobs.keys(); jobIds.hasMoreElements();) {
				jobid = (String) jobIds.nextElement();
				if (jobid.endsWith("_resultMode")) {
					continue;
				}
				// den dazugehörigen job holen
				job = (HBCIJob) jobs.get(jobid);

				if (job.getJobResult().isOK()) {
					// wenn der job erfolgreich gelaufen ist

					// ausgabe von jobid
					System.out.println("jobid:" + jobid);
					// ausgabe der hbci-status-meldungen zu diesem job
					System.out.println("job status:");
					System.out.println(job.getJobResult().getJobStatus());

					// ausgabe der job-ergebnisse
					System.out.println("job result:");

					// ausgabemodus="props": alle ergebnisdaten
					// als lowlevel-properties ausgeben

					Properties result = job.getJobResult().getResultData();
					if (result != null) {
						// array mit result-properties holen und
						// sortieren
						String[] keys = (String[]) new ArrayList(
								result.keySet()).toArray(new String[0]);
						Arrays.sort(keys);

						// ausgabe aller result-properties
						for (int i = 0; i < keys.length; i++) {
							String name = keys[i];
							String value = result.getProperty(name);
							System.out.println(name + "=" + value);
						}
					}

					// leerzeile einfügen
					System.out.println();
				} else {
					// wenn ein job fehler erzeugt hatte, die fehlermeldungen
					// an die err-datei anhängen

					System.err.println("jobid:" + jobid);
					System.err.println("global status:");
					System.err.println(job.getJobResult().getGlobStatus()
							.getErrorString());
					System.err.println("job status:");
					System.err.println(job.getJobResult().getJobStatus()
							.getErrorString());
					System.err.println();
				}

				handler.close();
				passport = null;

			}
		} finally {

			if (passport != null) {
				passport.close();
			}
		}
	}
}
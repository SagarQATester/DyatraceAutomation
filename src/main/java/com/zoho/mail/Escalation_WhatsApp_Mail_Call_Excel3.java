package com.zoho.mail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.testng.annotations.Test;

import com.readexcel.ExcelReader;
import com.readexcel.ExcelReader3;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;

public class Escalation_WhatsApp_Mail_Call_Excel3{

	   private static final String API_URL = "https://graph.facebook.com/v20.0/440426155815858/messages";
	    private static final String ACCESS_TOKEN = "EAAFsdRTS8kgBO63ZA5RUA6WWBYaI1CCuDzG3ZBCYaIaqM8ppD1zrolvHgAXQjvqYuttZCJfjgxhsLBpbEzO6pNlQ447xcsM3CPyyMgWbv8A0ZCENzgaxynZBFZCjZAamgW2C11BjtoiDl9qd1BQjnEgDwSp3px1G7iMJ6BQke5USMlL2E0rh0ntSLCFUmexxwTvxAZDZD";
	    private static String EMAIL_ID;
	    private static String EMAIL_PASSWORD;	   
	    // Twilio Account SID and Auth Token
	    public static final String ACCOUNT_SID = "AC1e48448a3fe44505b3ee62d66f2c08b7";
	    public static final String AUTH_TOKEN = "4670aa353cbf593054c8f5e5a9700b1b";
	    public static final String TWILIO_PHONE_NUMBER = "+13852009761";

	    private static final String PROBLEM_STATE_FILE = "ProblemFile\\problem_state.txt";

	    private static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
	    private static final Map<String, String[]> escalationContacts = new HashMap<>();
	    private static final Map<String, String[]> escalationMails = new HashMap<>();
	    private static final Map<String, Long> problemDetectionTimes = new HashMap<>();
	    private static final Map<String, String[]> escalationContacts_Call = new HashMap<>();
	    private static final ConcurrentHashMap<String, Boolean> l2MessageSentMap = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Boolean> l1MessageSentMap = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Set<String>> notifiedTeamsMap_WA = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Set<String>> notifiedTeamsMap_Mail = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Set<String>> notifiedTeamsMap_Call = new ConcurrentHashMap<>();

	    private static final ConcurrentHashMap<String, String> problemSeverityMap = new ConcurrentHashMap<>();

	    static {
	        ExcelReader3.loadDataFromExcel(); // Load the data from the Excel file
	        EMAIL_ID = ExcelReader3.emailId;
	        EMAIL_PASSWORD = ExcelReader3.emailPassword;
	        
	        // Initialize Twilio with Account SID and Auth Token
	        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
	        
	        escalationTimeFrames.put("Critical_L1", ExcelReader3.escalationTimeFrames.get("Critical_L1_TEAM"));
	        escalationTimeFrames.put("Critical_L2", ExcelReader3.escalationTimeFrames.get("Critical_L2_TEAM"));
	        escalationTimeFrames.put("NonCritical_L1", ExcelReader3.escalationTimeFrames.get("Non_Critical_L1_TEAM"));
	        escalationTimeFrames.put("NonCritical_L2", ExcelReader3.escalationTimeFrames.get("Non_Critical_L2_TEAM"));

	        escalationContacts.put("Critical_L1", ExcelReader3.escalationContacts.get("Critical_L1_TEAM"));
	        escalationContacts.put("Critical_L2", ExcelReader3.escalationContacts.get("Critical_L2_TEAM"));
	        escalationContacts.put("NonCritical_L1", ExcelReader3.escalationContacts.get("Non_Critical_L1_TEAM"));
	        escalationContacts.put("NonCritical_L2", ExcelReader3.escalationContacts.get("Non_Critical_L2_TEAM"));
	        
	        
	        escalationMails.put("Critical_L1", ExcelReader3.escalationMails.get("Critical_L1_TEAM"));
	        escalationMails.put("Critical_L2", ExcelReader3.escalationMails.get("Critical_L2_TEAM"));
	        escalationMails.put("NonCritical_L1", ExcelReader3.escalationMails.get("Non_Critical_L1_TEAM"));
	        escalationMails.put("NonCritical_L2", ExcelReader3.escalationMails.get("Non_Critical_L2_TEAM"));
	   
	        escalationContacts_Call.put("Critical_L1", ExcelReader3.escalationContacts_Call.get("Critical_L1_TEAM"));
	        escalationContacts_Call.put("Critical_L2", ExcelReader3.escalationContacts_Call.get("Critical_L2_TEAM"));
	        escalationContacts_Call.put("NonCritical_L1", ExcelReader3.escalationContacts_Call.get("Non_Critical_L1_TEAM"));
	        escalationContacts_Call.put("NonCritical_L2", ExcelReader3.escalationContacts_Call.get("Non_Critical_L2_TEAM"));
	    }
	 
	    @Test
    public void run() {
    	
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", "imap.zoho.in");
        properties.put("mail.imaps.port", "993");
        properties.put("mail.imaps.ssl.enable", "true");

        try {
            Session session = Session.getDefaultInstance(properties, null);
            Store store = session.getStore();
            store.connect(EMAIL_ID, EMAIL_PASSWORD);

            while (true) {
                checkForNewEmails(store);
                checkForOpenProblems();
                Thread.sleep(5000); // Check every 5 seconds
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkForNewEmails(Store store) {
        try {
            Folder inbox = store.getFolder("INBOX");
            Folder notificationFolder = inbox.getFolder("notification");
            notificationFolder.open(Folder.READ_WRITE);

            // Search for unread emails
            Message[] messages = notificationFolder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            for (Message message : messages) {
                // Check if the email is from "no-reply@dynatrace.com"
                Address[] fromAddresses = message.getFrom();
                if (fromAddresses != null && fromAddresses.length > 0 &&
                        fromAddresses[0].toString().contains("no-reply@dynatrace.com")) {

    
                    String subject = message.getSubject();
                    String body = getTextFromMessage(message);
    

                    String problemState = extractPattern(subject, "Problem State\\s*:\\s*(\\w+)");
                    String problemID = extractPattern(subject, "Problem ID\\s*:\\s*(P-\\d+)");
                    String problemSeverity = extractPattern(subject, "Problem Severity\\s*:\\s*(\\w+)");

                    System.out.println("Extracted Severity for Problem ID " + problemID + ": " + problemSeverity);

                    String problemDetectedAt = extractPattern(body, "(Problem detected at:\\s*\\d{2}:\\d{2} \\(UTC\\) \\d{2}\\.\\d{2}\\.\\d{4}(?: - \\d{2}:\\d{2} \\(UTC\\) \\d{2}\\.\\d{2}\\.\\d{4})?)");                    String impactedEntities = extractPattern(subject, "Impacted Entities\\s*:\\s*(.+)");
                    String environment = extractPattern(body, "environment\\s*(\\w+)");
                    String host = extractPattern(body, "Host\\s*(.+)");
                    String rootCause = extractPattern(body, "Root cause\\s*(.+)");
                    String problemLink = extractPattern(body, "(https?://\\S+)");

                    String formattedMessage = "*Problem State:* " + problemState + "\n" +
                            "*Problem ID:* " + problemID + "\n" +
                            "*Problem Severity:* " + problemSeverity + "\n" +
                            "*Problem detected at:* " + problemDetectedAt + "\n" + 
                            "*Impacted Entities:* " + impactedEntities + "\n" +
                            "*Environment:* " + environment + "\n" +
                            "*Host:* " + host + "\n" +
                            "*Root cause:* " + rootCause + "\n" +
                            "*Problem Link:* " + problemLink  ; 
                          
                                              
                  

                    Thread.sleep(1000);
                    if ("OPEN".equalsIgnoreCase(problemState)) {
                    	
                    	saveMessageToFile(message, problemID);
                        String severity = getSeverityFromProblemMessage(problemSeverity);
                        saveProblemState(problemID, problemState, severity, formattedMessage);
                        problemSeverityMap.put(problemID, severity);
                     

                        System.out.println("Stored Severity and Problem State for Problem ID " + problemID + ": " + "Problem State :"+problemState+" ProblemSeverity :" +severity);

                        SerializableMail mail=getMessageFromFile(problemID); 
                        startEscalationTimer(problemID, formattedMessage, severity,mail);

                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        System.out.println("Processing Resolved State for Problem ID " + problemID);
                        saveMessageToFile(message, problemID);
                        SerializableMail mail=getMessageFromFile(problemID); 
                        removeProblemState(problemID,formattedMessage, mail);
                    }

                    // Mark the email as read
                    message.setFlag(Flags.Flag.SEEN, true);
                }
            }

            notificationFolder.close(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkForOpenProblems() {
        try {
            File file = new File(PROBLEM_STATE_FILE);
            if (!file.exists()) {
                return;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                if (parts.length == 3) {
                    String problemID = parts[0].trim();
                    String problemState = parts[1].trim();
                    String severity = parts[2].trim();

                    if (!problemID.isEmpty() && "open".equalsIgnoreCase(problemState)) {
                        // Skip escalation if the problem was resolved before escalation started
                        long detectionTime = problemDetectionTimes.getOrDefault(problemID, 0L);
                        long currentTime = System.currentTimeMillis();
                        long l1Delay = escalationTimeFrames.getOrDefault(severity + "_L1", 0) * 60 * 1000L;
                        long l2Delay = escalationTimeFrames.getOrDefault(severity + "_L2", 0) * 60 * 1000L;

                        if ((currentTime - detectionTime) < Math.min(l1Delay, l2Delay)) {
                            // Problem resolved within the escalation time frame; skip escalation
                            continue;
                        }

                        String problemMessage = getProblemMessage(problemID);
                        SerializableMail mail = getMessageFromFile(problemID);

                        startEscalationTimer(problemID, problemMessage, severity, mail);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

 // Helper method to check if the problem is still open
    private static boolean isProblemStillOpen(String problemID) {
        File file = new File(PROBLEM_STATE_FILE);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                if (parts.length == 3 && parts[0].trim().equals(problemID)) {
                    String problemState = parts[1].trim();
                    return "OPEN".equalsIgnoreCase(problemState);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    private static void saveProblemState(String problemID, String problemState, String severity, String formattedMessage) {
        try {
            Set<String> problemIDs = new HashSet<>();
            File file = new File(PROBLEM_STATE_FILE);
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    problemIDs.add(line.trim());
                }
                reader.close();
            }

            System.out.println("Extracted Problem State for Problem ID " + problemID + ": " + problemState);

            if (!problemIDs.contains(problemID + " " + problemState + " " + severity)) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(PROBLEM_STATE_FILE, true));
                writer.write(problemID + " " + problemState + " " + severity);
                writer.newLine();
                writer.close();
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter("ProblemFile\\"+problemID + ".txt"));
            writer.write(formattedMessage);
            writer.close();

            System.out.println("Saved problem state for " + problemID + " with state: " + problemState + " and severity: " + severity);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void saveMessageToFile(Message message, String problemId) {
        try {
            String subject = message.getSubject();
            String content = extractMessageBody(message);
            
            SerializableMail serializableMail = new SerializableMail(subject, content);
            
            String fileName = "ProblemFile/" + problemId + "_Mail.ser";
            try (FileOutputStream fos = new FileOutputStream(fileName);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(serializableMail);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static SerializableMail getMessageFromFile(String problemId) {
        SerializableMail mail = null;
        try {
            String fileName = "ProblemFile/" + problemId + "_Mail.ser";
            try (FileInputStream fis = new FileInputStream(fileName);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                mail = (SerializableMail) ois.readObject();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mail;
    }

    private static void removeProblemState(String problemID, String resolvedformattedMessage, SerializableMail mail) throws MessagingException, IOException
    {
        try {
            File file = new File(PROBLEM_STATE_FILE);
            if (!file.exists()) {
                return;
            }

            File tempFile = new File(  PROBLEM_STATE_FILE+"_temp");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(problemID)) {
                    writer.write(line);
                    writer.newLine();
                } else {
                    System.out.println("Removed problem state entry: " + line);
                }
            }

            writer.close();
            reader.close();

            // Replace the original file with the updated one
            if (!file.delete() || !tempFile.renameTo(file)) {
                throw new IOException("Failed to replace the original problem state file");
            }

            // Delete the specific problem file
            File problemFile = new File("ProblemFile\\"+problemID + ".txt");
            if (problemFile.exists()) {
                if (!problemFile.delete()) {
                    throw new IOException("Failed to delete problem file for " + problemID);
                }
            }

            // Send problem resolution notifications
            notifyTeamsOfResolution(problemID, resolvedformattedMessage,mail);
            deleteSavedMessageFile(problemID);
  

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static boolean deleteSavedMessageFile(String problemId) {
        String fileName = "ProblemFile/" + problemId + "_Mail.ser";
        File file = new File(fileName);
        if (file.exists()) {
            return file.delete();  // Returns true if the file is successfully deleted
        } else {
            System.out.println("File not found: " + fileName);
            return false;
        }
        
    }

    private static void startEscalationTimer(String problemID, String formattedMessage, String severity, SerializableMail mail) {
        if (!problemDetectionTimes.containsKey(problemID)) {
            problemDetectionTimes.put(problemID, System.currentTimeMillis());
        }

        long detectionTime = problemDetectionTimes.get(problemID);
      

        notifiedTeamsMap_WA.putIfAbsent(problemID, new HashSet<>());
        notifiedTeamsMap_Mail.putIfAbsent(problemID, new HashSet<>());
        notifiedTeamsMap_Call.putIfAbsent(problemID, new HashSet<>());
        l1MessageSentMap.putIfAbsent(problemID, false);
        l2MessageSentMap.putIfAbsent(problemID, false);

        // Check L1 escalation
        String l1Key = severity + "_L1";
            
        int l1TimeFrame = escalationTimeFrames.getOrDefault(l1Key, 0);
        long l1Delay = l1TimeFrame * 60 * 1000L;

        Timer l1Timer = new Timer();
        l1Timer.schedule(new TimerTask() {
            @Override
            public void run() {
            	 if (!l1MessageSentMap.get(problemID) && isProblemStillOpen(problemID)) {
                	  long currentTime = System.currentTimeMillis();
                	  
                    if (currentTime - detectionTime >= l1Delay) {
                    	
                        l1MessageSentMap.put(problemID, true);
                        String[] teams = escalationContacts.get(l1Key);
                        String[] team_mail = escalationMails.get(l1Key);
                        String[] team_Call = escalationContacts_Call.get(l1Key);
                        String issueReportTime = "Issue Reported To L1 Team At: " + getCurrentDateTime();

                        if (teams != null) {
                            sendMessageToTeams(teams, formattedMessage + "\n" + issueReportTime, problemID);
                        } 
                         if  (team_Call != null) {
                        
                            callToTeams(team_Call, formattedMessage, problemID);
                        } 
                        
                         if (team_mail != null) {
                            try {
								sendMailToTeams(team_mail, mail, problemID);
							} catch (MessagingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
                        }                    } 
                    else {
                        System.out.println("Waiting for L1 Escalation Time OR Problem ID " + problemID + " resolved before L1 escalation time");
                    }
                }
            }
        }, l1Delay);

        // Check L2 escalation
        String l2Key = severity + "_L2";     
        int l2TimeFrame = escalationTimeFrames.getOrDefault(l2Key, 0);
        long l2Delay = l2TimeFrame * 60 * 1000L;

        Timer l2Timer = new Timer();
        l2Timer.schedule(new TimerTask() {
            @Override
            public void run() {
            	 if (!l2MessageSentMap.get(problemID) && isProblemStillOpen(problemID)) {
                	  long currentTime = System.currentTimeMillis();
                    if (currentTime - (detectionTime+l1Delay) >= l2Delay) {
                    	
                        l2MessageSentMap.put(problemID, true);
                        String[] teams = escalationContacts.get(l2Key);
                        String[] team_mail = escalationMails.get(l2Key);
                        String[] team_Call = escalationContacts_Call.get(l2Key);

                        String issueReportTime = "Issue Reported To L2 Team At: " + getCurrentDateTime();

                        if (teams != null) {
                            sendMessageToTeams(teams, formattedMessage + "\n" + issueReportTime, problemID);
                        } 
                         if (team_Call != null) {
                            callToTeams(team_Call, formattedMessage, problemID);
                        }
                        
                         if (team_mail != null) {
                            try {
								sendMailToTeams(team_mail, mail, problemID);
							} catch (MessagingException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
                        }
                        else {
                            System.err.println("No L2 teams found for severity: " + severity);
                        }
                    } else {
                        System.out.println("Waiting for L2 Escalation Time OR Problem ID " + problemID + " resolved before L2 escalation time.");
                    }
                }
            }
        }, l2Delay);
    }

    private static void notifyTeamsOfResolution(String problemID, String resolvedformattedMessage,SerializableMail mail) throws MessagingException, IOException {
        Set<String> notified_WA_Teams = notifiedTeamsMap_WA.get(problemID);
        Set<String> notified_Mail_Teams = notifiedTeamsMap_Mail.get(problemID); 
        Set<String> notified_Call_Teams = notifiedTeamsMap_Call.get(problemID); 
     
        if (notified_WA_Teams != null) {
            String resolutionMessage = "The issue has been RESOLVED."+ "\n" ;
           
            for (String team : notified_WA_Teams) {
            	System.out.println("notify Teams Of Resolution By Whats App Message:"+team);
                sendMessage(team, resolutionMessage+ resolvedformattedMessage);
            }
        }
        if (notified_Mail_Teams != null) {
        	
            for (String team : notified_Mail_Teams) {
            	System.out.println("notify Teams Of Resolution By Mail:"+team);
              sendEmail(team, mail, problemID);
            }
        }
        
    /*   
        if (notified_Call_Teams != null) {
            String resolutionMessage = "The issue has been RESOLVED."+ "\n" ;
            for (String team : notified_Call_Teams) {
            	System.out.println("notify Teams Of Resolution By call:"+team);
                makeVoiceCall(team, resolutionMessage+ resolvedformattedMessage);;
            }              
        }
        */
    }

    private static String getProblemMessage(String problemID) {
        StringBuilder problemMessage = new StringBuilder();

        try {
            File file = new File("ProblemFile\\"+problemID + ".txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    problemMessage.append(line).append("\n");
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return problemMessage.toString();
    }

    private static String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            String result = "";
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            int count = mimeMultipart.getCount();
            for (int i = 0; i < count; i++) {
                BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    result = result + bodyPart.getContent();
                } else if (bodyPart.isMimeType("text/html")) {
                    String html = (String) bodyPart.getContent();
                    result = result + Jsoup.parse(html).text();
                }
            }
            return result;
        }
        return "";
    }



    private static String extractPattern(String text, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private static String getSeverityFromProblemMessage(String problemSev) {
        System.out.println("Debug: Analyzing problem severity...");

        if (problemSev == null || problemSev.trim().isEmpty()) {
            System.out.println("Warning: Problem severity is null or empty. Defaulting to 'Non-Critical'.");
            return "NonCritical";
        }
        // Check if the severity matches 'CUSTOM_ALERT' or 'AVAILABILITY'
        if (problemSev.equalsIgnoreCase("CUSTOM_ALERT") || problemSev.equalsIgnoreCase("AVAILABILITY")) {
            System.out.println("Severity recognized as 'Critical'.");
            return "Critical";
        }
        // Check if the severity matches 'PERFORMANCE'
        else if (problemSev.equalsIgnoreCase("PERFORMANCE")) {
            System.out.println("Severity recognized as 'Non-Critical'.");
            return "NonCritical";
        } else {
            System.out.println("Warning: Severity not recognized. Defaulting to 'Non-Critical'.");
            return "NonCritical";
        }
    }


    private static void sendMessageToTeams(String[] teams, String message, String problemID) {
        if (teams != null) {
            for (String team : teams) {
                sendMessage(team, message);
                System.out.println("Whats App Message Sent to "+team);
                notifiedTeamsMap_WA.get(problemID).add(team);
            }
        } else {
            System.err.println("Cannot send message. Team list is null.");
        }
    }
    public static void callToTeams(String[] teams, String message, String problemID) {
        if (teams != null) {
            for (String team : teams) {
                makeVoiceCall(team, message);
                System.out.println("Make a voice call to: "+team);
                notifiedTeamsMap_Call.get(problemID).add(team);
            }
        } else {
            System.err.println("Cannot Call to team. Team list is null.");
        }
    }
	private static void sendMailToTeams(String[] teams, SerializableMail mail, String problemID) throws MessagingException, IOException {
        if (teams != null) {
            for (String team : teams) {
            	if(team.equalsIgnoreCase("NA"))
            	{
            		System.out.println("Email ID not added in the Escalation Matrics ");
            	}
            	else {
            	System.out.println("Team details:"+team);
            	sendEmail(team, mail,problemID);
            	  System.out.println("Mail sent to: "+team);
                notifiedTeamsMap_Mail.get(problemID).add(team);
            	}
            }
        } else {
            System.err.println("Cannot send message. Team list is null.");
        }
    }
    private static void sendEmail(String team, SerializableMail mail , String problemID) throws MessagingException, IOException {
       
        String messageSubject = mail.getSubject();
        String messageBody = mail.getContent();
        
    	Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.zoho.in");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_ID, EMAIL_PASSWORD);
            }
        });
        
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_ID));

            message.addRecipient(Message.RecipientType.TO, new InternetAddress(team));
        
            message.setSubject(messageSubject);
          
           message.setContent(messageBody, "text/html; charset=utf-8");
            

            Transport.send(message);
            System.out.println("Email sent successfully.");

        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Extract only the email body content
    private static String extractMessageBody(Message message) throws MessagingException, IOException {
        String content = "";
        if (message.isMimeType("text/plain")) {
            content = (String) message.getContent();
            System.out.println("isMimeTypt: text/plain");
        } else if (message.isMimeType("text/html")) {
            content = (String) message.getContent();
            System.out.println("isMimeTypt: text/html");
        } else if (message.isMimeType("multipart/*")) {
            content = getTextFromMimeMultipart((MimeMultipart) message.getContent());
            System.out.println("isMimeTypt: multipart");
        }
        return content;
    }

    private static String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/html")) {
                result.append(bodyPart.getContent().toString());
          System.out.println("isMimeType:text/ plain");
            } 
   
        }
        return result.toString();
    }
    
    private static void sendMessage(String recipient, String message) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(API_URL);

            JSONObject json = new JSONObject();
            json.put("messaging_product", "whatsapp");
            json.put("recipient_type", "individual");
            json.put("to", recipient);
            json.put("type", "text");
            json.put("text", new JSONObject().put("body", message));

            StringEntity entity = new StringEntity(json.toString());
            post.setEntity(entity);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);

            CloseableHttpResponse response = client.execute(post);
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("Sent message to " + recipient + ". Response: " + responseBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void makeVoiceCall(String to, String formattedMessage) {
        try {
            // Construct the message for the voice call
            String message = "This is an automated call initiated to address an issue with the application " +
                    formattedMessage;

            // URL encode the message
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());

            // TwiML URL (Twilio Markup Language)
            String twimlUrl = "http://twimlets.com/message?Message%5B0%5D=" + encodedMessage;

            // Create the call
            Call call = Call.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(TWILIO_PHONE_NUMBER),
                    URI.create(twimlUrl))
                .create();

            System.out.println("Calling: " + to);
            System.out.println("Call SID: " + call.getSid());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static String getCurrentDateTime() {
        // Define the date and time format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();
        
        // Format and return as a string
        return now.format(formatter);
    }
}

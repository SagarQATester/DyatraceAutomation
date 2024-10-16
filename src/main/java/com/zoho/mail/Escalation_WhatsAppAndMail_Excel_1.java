package com.zoho.mail;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.internet.*;
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

public class Escalation_WhatsAppAndMail_Excel_1{

	   private static final String API_URL = "https://graph.facebook.com/v20.0/440426155815858/messages";
	    private static final String ACCESS_TOKEN = "EAAFsdRTS8kgBO63ZA5RUA6WWBYaI1CCuDzG3ZBCYaIaqM8ppD1zrolvHgAXQjvqYuttZCJfjgxhsLBpbEzO6pNlQ447xcsM3CPyyMgWbv8A0ZCENzgaxynZBFZCjZAamgW2C11BjtoiDl9qd1BQjnEgDwSp3px1G7iMJ6BQke5USMlL2E0rh0ntSLCFUmexxwTvxAZDZD";
	    private static String EMAIL_ID;
	    private static String EMAIL_PASSWORD;
	    
	    private static final String PROBLEM_STATE_FILE = "problem_state.txt";

	    private static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
	    private static final Map<String, String[]> escalationContacts = new HashMap<>();
	    private static final Map<String, String[]> escalationMails = new HashMap<>();
	    private static final Map<String, Long> problemDetectionTimes = new HashMap<>();
	    private static final ConcurrentHashMap<String, Boolean> l2MessageSentMap = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Boolean> l1MessageSentMap = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Set<String>> notifiedTeamsMap_WA = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, Set<String>> notifiedTeamsMap_Mail = new ConcurrentHashMap<>();
	    private static final ConcurrentHashMap<String, String> problemSeverityMap = new ConcurrentHashMap<>();

		
	    static {
	        ExcelReader.loadDataFromExcel(); // Load the data from the Excel file
	        EMAIL_ID = ExcelReader.emailId;
	        EMAIL_PASSWORD = ExcelReader.emailPassword;
	
	        escalationTimeFrames.put("Critical_L1", ExcelReader.escalationTimeFrames.get("Critical_L1_TEAM"));
	        escalationTimeFrames.put("Critical_L2", ExcelReader.escalationTimeFrames.get("Critical_L2_TEAM"));
	        escalationTimeFrames.put("NonCritical_L1", ExcelReader.escalationTimeFrames.get("Non_Critical_L1_TEAM"));
	        escalationTimeFrames.put("NonCritical_L2", ExcelReader.escalationTimeFrames.get("Non_Critical_L2_TEAM"));

	        escalationContacts.put("Critical_L1", ExcelReader.escalationContacts.get("Critical_L1_TEAM"));
	        escalationContacts.put("Critical_L2", ExcelReader.escalationContacts.get("Critical_L2_TEAM"));
	        escalationContacts.put("NonCritical_L1", ExcelReader.escalationContacts.get("Non_Critical_L1_TEAM"));
	        escalationContacts.put("NonCritical_L2", ExcelReader.escalationContacts.get("Non_Critical_L2_TEAM"));
	        
	        escalationMails.put("Critical_L1", ExcelReader.escalationMails.get("Critical_L1_TEAM"));
	        escalationMails.put("Critical_L2", ExcelReader.escalationMails.get("Critical_L2_TEAM"));
	        escalationMails.put("NonCritical_L1", ExcelReader.escalationMails.get("Non_Critical_L1_TEAM"));
	        escalationMails.put("NonCritical_L2", ExcelReader.escalationMails.get("Non_Critical_L2_TEAM"));
	    }
	 
	    @Test
    public void run() {
     //	ExcelReader.loadDataFromExcel();
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

                    String impactedEntities = extractPattern(subject, "Impacted Entities\\s*:\\s*(.+)");
                    String environment = extractPattern(body, "environment\\s*(\\w+)");
                    String host = extractPattern(body, "Host\\s*(.+)");
                    String rootCause = extractPattern(body, "Root cause\\s*(.+)");
                    String problemLink = extractPattern(body, "(https?://\\S+)");

                    String formattedMessage = "*Problem State:* " + problemState + "\n" +
                            "*Problem ID:* " + problemID + "\n" +
                            "*Problem Severity:* " + problemSeverity + "\n" +
                            "*Impacted Entities:* " + impactedEntities + "\n" +
                            "*Environment:* " + environment + "\n" +
                            "*Host:* " + host + "\n" +
                            "*Root cause:* " + rootCause + "\n" +
                            "*Problem Link:* " + problemLink;

                    Thread.sleep(1000);
                    if ("OPEN".equalsIgnoreCase(problemState)) {
                    	
                        String severity = getSeverityFromProblemMessage(problemSeverity);
                        saveProblemState(problemID, problemState, severity, formattedMessage);
                        problemSeverityMap.put(problemID, severity);

                        System.out.println("Stored Severity and Problem State for Problem ID " + problemID + ": " + "Problem State :"+problemState+" ProblemSeverity :" +severity);

                        startEscalationTimer(problemID, formattedMessage, severity);

                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        System.out.println("Processing Resolved State for Problem ID " + problemID);
                        removeProblemState(problemID,formattedMessage);
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

                    if (!problemID.isEmpty()) {
                        // Skip escalation if the problem was resolved before escalation started
                        if (l1MessageSentMap.containsKey(problemID) || l2MessageSentMap.containsKey(problemID)) {
                            long detectionTime = problemDetectionTimes.getOrDefault(problemID, 0L);
                            long currentTime = System.currentTimeMillis();
                            long l1Delay = escalationTimeFrames.getOrDefault(severity + "_L1", 0) * 60 * 1000L;
                            long l2Delay = escalationTimeFrames.getOrDefault(severity + "_L2", 0) * 60 * 1000L;

                            if ((currentTime - detectionTime) < Math.min(l1Delay, l2Delay)) {
                                // Problem resolved within the escalation time frame; skip escalation
                                continue;
                            }
                        }

                        String problemMessage = getProblemMessage(problemID);

                        startEscalationTimer(problemID, problemMessage, severity);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

            BufferedWriter writer = new BufferedWriter(new FileWriter(problemID + ".txt"));
            writer.write(formattedMessage);
            writer.close();

            System.out.println("Saved problem state for " + problemID + " with state: " + problemState + " and severity: " + severity);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void removeProblemState(String problemID, String resolvedformattedMessage) {
        try {
            File file = new File(PROBLEM_STATE_FILE);
            if (!file.exists()) {
                return;
            }

            File tempFile = new File("temp_" + PROBLEM_STATE_FILE);
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
            File problemFile = new File(problemID + ".txt");
            if (problemFile.exists()) {
                if (!problemFile.delete()) {
                    throw new IOException("Failed to delete problem file for " + problemID);
                }
            }

            // Send problem resolution notifications
            notifyTeamsOfResolution(problemID, resolvedformattedMessage);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startEscalationTimer(String problemID, String formattedMessage, String severity) {
        if (!problemDetectionTimes.containsKey(problemID)) {
            problemDetectionTimes.put(problemID, System.currentTimeMillis());
        }

        long detectionTime = problemDetectionTimes.get(problemID);
        long currentTime = System.currentTimeMillis();

        notifiedTeamsMap_WA.putIfAbsent(problemID, new HashSet<>());
        notifiedTeamsMap_Mail.putIfAbsent(problemID, new HashSet<>());
        l1MessageSentMap.putIfAbsent(problemID, false);
        l2MessageSentMap.putIfAbsent(problemID, false);

        // Check L1 escalation
        String l1Key = severity + "_L1";
        String l1Key_Mail = severity + "_L1";
        
        int l1TimeFrame = escalationTimeFrames.getOrDefault(l1Key, 0);
        long l1Delay = l1TimeFrame * 60 * 1000L;

        Timer l1Timer = new Timer();
        l1Timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!l1MessageSentMap.get(problemID)) {
                    if (currentTime - detectionTime >= l1Delay) {
                        l1MessageSentMap.put(problemID, true);
                        String[] teams = escalationContacts.get(l1Key);
                        String[] team_mail = escalationMails.get(l1Key_Mail);
                        if (teams != null) {
                            sendMessageToTeams(teams, formattedMessage, problemID);
                            System.out.println("Whats App message sent to L1 Team: "+teams.toString()+ ",Severity:"+severity+ " ,Problem ID: "+problemID);
                        }
                        if (team_mail != null) {
                        	sendMailToTeams(team_mail, formattedMessage, problemID);
                            System.out.println("Mail sent to L1 Team: "+teams.toString()+ ",Severity:"+severity+ " ,Problem ID: "+problemID);

                        }
                        else {
                            System.err.println("No L1 teams found for severity: " + severity);
                        }
                    } else {
                        System.out.println("Waiting for L1 Escalation Time OR Problem ID " + problemID + " resolved before L1 escalation time");
                    }
                }
            }
        }, l1Delay);

        // Check L2 escalation
        String l2Key = severity + "_L2";
        String l2Key_Mail = severity + "_L2";
        int l2TimeFrame = escalationTimeFrames.getOrDefault(l2Key, 0);
        long l2Delay = l2TimeFrame * 60 * 1000L;

        Timer l2Timer = new Timer();
        l2Timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!l2MessageSentMap.get(problemID)) {
                    if (currentTime - detectionTime >= l2Delay) {
                        l2MessageSentMap.put(problemID, true);
                        String[] teams = escalationContacts.get(l2Key);
                        String[] team_mail = escalationMails.get(l2Key_Mail);
                        if (teams != null) {
                            sendMessageToTeams(teams, formattedMessage, problemID);
                            System.out.println("Whats App message sent to L2 Team: "+teams.toString()+ ",Severity:"+severity+ " ,Problem ID: "+problemID);
                        }
                        if (team_mail != null) {
                        	sendMailToTeams(team_mail, formattedMessage, problemID);
                            System.out.println("Mail sent to L2 Team: "+teams.toString()+ ",Severity:"+severity+ " ,Problem ID: "+problemID);

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

    private static void notifyTeamsOfResolution(String problemID, String resolvedformattedMessage) {
        Set<String> notified_WA_Teams = notifiedTeamsMap_WA.get(problemID);
        Set<String> notified_Mail_Teams = notifiedTeamsMap_Mail.get(problemID);        
        if (notified_WA_Teams != null) {
            String resolutionMessage = "The issue has been RESOLVED."+ "\n" ;
            for (String team : notified_WA_Teams) {
            	System.out.println("notifyTeamsOfResolution:"+team);
                sendMessage(team, resolutionMessage+ resolvedformattedMessage);
            }
        }
        if (notified_Mail_Teams != null) {
            String resolutionMessage = "The issue has been RESOLVED."+ "\n" ;
            for (String team : notified_Mail_Teams) {
            	System.out.println("notifyTeamsOfResolution:"+team);
                sendEmail(team, resolutionMessage+ resolvedformattedMessage, problemID);
            }
        }
    }

    private static String getProblemMessage(String problemID) {
        StringBuilder problemMessage = new StringBuilder();

        try {
            File file = new File(problemID + ".txt");
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
                notifiedTeamsMap_WA.get(problemID).add(team);
            }
        } else {
            System.err.println("Cannot send message. Team list is null.");
        }
    }
    
	private static void sendMailToTeams(String[] teams, String formattedMessage, String problemID) {
        if (teams != null) {
            for (String team : teams) {
            	System.out.println("Team details:"+team);
            	sendEmail(team, formattedMessage,problemID);
                notifiedTeamsMap_Mail.get(problemID).add(team);
            }
        } else {
            System.err.println("Cannot send message. Team list is null.");
        }
    }
    private static void sendEmail(String team, String formattedMessage, String problemID) {
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
        

            message.setSubject("Forwarded: " +  problemID);
            message.setText(formattedMessage);

            Transport.send(message);
            System.out.println("Email sent successfully.");

        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}

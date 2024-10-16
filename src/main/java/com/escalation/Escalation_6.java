package com.escalation;

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

public class Escalation_6 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD"; // Replace with your actual token securely
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089"; // Replace with your actual password securely
    private static final String[] EMAIL_RECIPIENTS = {"sagar.k@believe-it.in"};
    private static final String[] RECIPIENTS = {"+919011734501", "+917058071404"};
    private static final String PROBLEM_STATE_FILE = "problem_state.txt";
    
    // Additional recipient numbers for different severity levels
    private static final String[] CRITICAL_L1_TEAM = {"+919011734501"};
    private static final String[] NON_CRITICAL_L1_TEAM = {"+917058071404"};

    // Data structures to hold the escalation matrix details
    private static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
    private static final Map<String, String[]> escalationContacts = new HashMap<>();
    private static final Map<String, Long> problemDetectionTimes = new HashMap<>();
    

 // Define the map to track L2 message sent status
    private static final ConcurrentHashMap<String, Boolean> l2MessageSentMap = new ConcurrentHashMap<>();
 // Define the map to track L1 message sent status
    private static final ConcurrentHashMap<String, Boolean> l1MessageSentMap = new ConcurrentHashMap<>();

    static {	
        // Populating the escalation time frames and contacts
        escalationTimeFrames.put("Critical_L1", 0); // 0 minutes for L1
        escalationTimeFrames.put("Critical_L2", 2); // 2 minutes for L2
        escalationTimeFrames.put("NonCritical_L1", 1); // 3 minutes for L1
        escalationTimeFrames.put("NonCritical_L2", 3); // 4 minutes for L2

        escalationContacts.put("Critical_L1", CRITICAL_L1_TEAM);
        escalationContacts.put("Critical_L2", new String[]{"+917058071404"}); // L2 team
        escalationContacts.put("NonCritical_L1", NON_CRITICAL_L1_TEAM);
        escalationContacts.put("NonCritical_L2", new String[]{"+917058071404"}); // L2 team
    }

    public static void main(String[] args) {
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

                    // Extract information from subject
                    String problemState = extractPattern(subject, "Problem State\\s*:\\s*(\\w+)");
                    String problemID = extractPattern(subject, "Problem ID\\s*:\\s*(P-\\d+)");
                    String problemSeverity = extractPattern(subject, "Problem Severity\\s*:\\s*(\\w+)");
                    String impactedEntities = extractPattern(subject, "Impacted Entities\\s*:\\s*(.+)");

                    // Extract information from body
                    String environment = extractPattern(body, "Environment\\s*:\\s*(\\w+)");
                    String host = extractPattern(body, "Host\\s*:\\s*(.+)");
                    String rootCause = extractPattern(body, "Root cause\\s*:\\s*(.+)");
                    String problemLink = extractPattern(body, "(https?://\\S+)");

                    // Format the message
                    String formattedMessage = "*Problem State:* " + problemState + "\n" +
                            "*Problem ID:* " + problemID + "\n" +
                            "*Problem Severity:* " + problemSeverity + "\n" +
                            "*Impacted Entities:* " + impactedEntities + "\n" +
                            "*Environment:* " + environment + "\n" +
                            "*Host:* " + host + "\n" +
                            "*Root cause:* " + rootCause + "\n" +
                            "*Problem Link:* " + problemLink;

                    if ("OPEN".equalsIgnoreCase(problemState)) {
                        // Save problem state and details
                        saveProblemState(problemID, formattedMessage);
                        saveProblemState_Time(problemID, formattedMessage);  // Save the detection time

                        System.out.println("Problem Severity:"+problemSeverity);
                        // Determine if severity is critical or non-critical
                        String severity = getSeverityFromProblemMessage(problemSeverity);
                        System.out.println("Problem Severity:"+problemSeverity);
                        // Start escalation timer
                        startEscalationTimer(problemID, formattedMessage, severity);

                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        // Prepare and send resolved message
                        String resolvedMessage = prepareResolvedMessage(problemID);
                        if (resolvedMessage != null) {
                            sendMessageToAll(resolvedMessage, RECIPIENTS);
                            // Remove problem state and delete problemId.txt
                            removeProblemState(problemID);
                        } else {
                            System.out.println("Resolved Problem ID " + problemID + " not found.");
                        }
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
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    String problemID = parts[0].trim();
                    String problemState = parts[1].trim();
                    if (!problemID.isEmpty()) {
                        String problemMessage = getProblemMessage(problemID);
                        String severity = getSeverityFromProblemMessage(problemMessage); // Extract severity

                        // Start escalation timer for this problem
                        startEscalationTimer(problemID, problemMessage, severity);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveProblemState(String problemID, String formattedMessage) {
        try {
            // Check if problemID is already saved
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

            //String problemState = extractPattern(formattedMessage, "Problem State\\s*:\\s*(\\w+)");
            String problemState = extractPattern(formattedMessage, "Problem State\\s*:\\s*(\\w+)");
            if (!problemIDs.contains(problemID + " " + problemState)) {
                // Append problemID and state to PROBLEM_STATE_FILE
                BufferedWriter writer = new BufferedWriter(new FileWriter(PROBLEM_STATE_FILE, true));
                writer.write(problemID + " " + problemState);    
                writer.newLine();
                writer.close();
            }

            // Create problemID.txt with formattedMessage
            BufferedWriter writer = new BufferedWriter(new FileWriter(problemID + ".txt"));
            writer.write(formattedMessage);
            writer.close();

            System.out.println("Saved problem state for " + problemID+"Problem State:"+problemState);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void removeProblemState(String problemID) {
        try {
            File inputFile = new File(PROBLEM_STATE_FILE);
            File tempFile = new File("temp_" + PROBLEM_STATE_FILE);

            if (!inputFile.exists()) {
                System.out.println("PROBLEM_STATE_FILE does not exist.");
                return;
            }

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                String trimmedLine = currentLine.trim();
                if (!trimmedLine.startsWith(problemID + " ")) {
                    writer.write(currentLine);
                    writer.newLine();
                }
            }
            writer.close();
            reader.close();

            if (!inputFile.delete()) {
                System.out.println("Could not delete PROBLEM_STATE_FILE");
            } else if (!tempFile.renameTo(inputFile)) {
                System.out.println("Could not rename temp file to PROBLEM_STATE_FILE");
            }

            // Delete the corresponding problemID.txt file
            File problemFile = new File(problemID + ".txt");
            if (problemFile.exists() && !problemFile.delete()) {
                System.out.println("Could not delete " + problemID + ".txt");
            }

            // Send the resolved message
            String resolvedMessage = prepareResolvedMessage(problemID);
            if (resolvedMessage != null) {
                sendMessageToAll(resolvedMessage, RECIPIENTS);
                System.out.println("Resolved message sent for Problem ID " + problemID + " at " + new Date());
            } else {
                System.out.println("Resolved Problem ID " + problemID + " not found.");
            }

            // Clear L1 and L2 message sent status for the problem ID
            l1MessageSentMap.remove(problemID);
            l2MessageSentMap.remove(problemID);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startEscalationTimer(String problemID, String formattedMessage, String severity) {
        new Thread(() -> {
            try {
                String l1Key = severity + "_L1";
                String l2Key = severity + "_L2";

                // Get L1 and L2 escalation time frames and contacts
                Integer l1TimeFrame = escalationTimeFrames.get(l1Key);
                Integer l2TimeFrame = escalationTimeFrames.get(l2Key);

                String[] l1Contacts = escalationContacts.get(l1Key);
                String[] l2Contacts = escalationContacts.get(l2Key);

                if (l1TimeFrame != null && l2TimeFrame != null && l1Contacts != null && l2Contacts != null) {
                    // Wait for L1 escalation time frame
                    Thread.sleep(l1TimeFrame * 60 * 1000);

                    // Check if the problem is resolved before escalating to L1
                    if (isProblemResolved(problemID)) {
                        System.out.println("Problem " + problemID + " is resolved before L1 escalation.");
                        return;  // Stop escalation
                    }

                    // Check if L1 message has already been sent
                    if (l1MessageSentMap.putIfAbsent(problemID, true) == null) {
                        sendMessageToAll(formattedMessage, l1Contacts);
                        System.out.println("L1 escalation triggered for Problem ID " + problemID + " at " + new Date());
                    } else {
                        System.out.println("L1 message for Problem ID " + problemID + " has already been sent.");
                    }

                    // Wait for L2 escalation time frame
                    Thread.sleep((l2TimeFrame - l1TimeFrame) * 60 * 1000);

                    // Check if the problem is resolved before escalating to L2
                    if (isProblemResolved(problemID)) {
                        System.out.println("Problem " + problemID + " is resolved before L2 escalation.");
                        return;  // Stop escalation
                    }

                    // Check if L2 message has already been sent
                    if (l2MessageSentMap.putIfAbsent(problemID, true) == null) {
                        sendMessageToAll(formattedMessage, l2Contacts);
                        System.out.println("L2 escalation triggered for Problem ID " + problemID + " at " + new Date());
                    } else {
                        System.out.println("L2 message for Problem ID " + problemID + " has already been sent.");
                    }

                } else {
                    System.out.println("Escalation time frame or contacts not found for severity: " + severity);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static boolean isProblemResolved(String problemID) {
        File file = new File(problemID + ".txt");
        if (!file.exists()) {
            return true;  // Assume resolved if the problem file does not exist
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Problem State: RESOLVED")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String prepareResolvedMessage(String problemID) {
        try {
            File file = new File(problemID + ".txt");
            if (!file.exists()) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder resolvedMessage = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                resolvedMessage.append(line).append("\n");
            }
            reader.close();

            // Append "Problem Resolved" at the beginning
            resolvedMessage.insert(0, "*Problem Resolved*\n");

            return resolvedMessage.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String extractPattern(String input, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        return matcher.find() ? matcher.group(1) : "N/A";
    }

    private static String getTextFromMessage(Message message) throws MessagingException, IOException {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            StringBuilder result = new StringBuilder();
            Multipart multipart = (Multipart) message.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                result.append(bodyPart.getContent().toString());
            }
            return result.toString();
        }
        return "";
    }

    private static String getSeverityFromProblemMessage(String problemSev) {
        System.out.println("Debug: Analyzing problem severity...");

        if (problemSev == null || problemSev.trim().isEmpty()) {
            System.out.println("Warning: Problem severity is null or empty. Defaulting to 'Non-Critical'.");
            return "NonCritical";
        }
        // Check if the severity matches 'PERFORMANCE' or 'AVAILABILITY'
        if (problemSev.equalsIgnoreCase("PERFORMANCE") ||problemSev.equalsIgnoreCase("AVAILABILITY") ) {
            System.out.println("Severity recognized as 'Critical'.");
            return "Critical";
        }
        // Check if the severity matches 'CUSTOM_ALERT'
        else if (problemSev.equalsIgnoreCase("CUSTOM_ALERT")) {
            System.out.println("Severity recognized as 'Non-Critical'.");
            return "NonCritical";
        } else {
            System.out.println("Warning: Severity not recognized. Defaulting to 'Non-Critical'.");
            return "NonCritical";
        }
    }

    private static String getProblemMessage(String problemID) {
        try {
            File file = new File(problemID + ".txt");
            if (!file.exists()) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder problemMessage = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                problemMessage.append(line).append("\n");
            }
            reader.close();

            return problemMessage.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveProblemState_Time(String problemID, String formattedMessage) {
        problemDetectionTimes.put(problemID, System.currentTimeMillis());
    }

    private static void sendMessageToAll(String message, String[] recipients) {
        for (String recipient : recipients) {
            sendMessage(message, recipient);
        }
    }

    private static void sendMessage(String message, String recipient) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            json.put("messaging_product", "whatsapp");

            JSONObject recipientJson = new JSONObject();
            recipientJson.put("type", "individual");
            recipientJson.put("recipient_type", "individual");
            recipientJson.put("to", recipient);

            json.put("to", recipient);
            json.put("type", "text");
            JSONObject textJson = new JSONObject();
            textJson.put("body", message);
            json.put("text", textJson);

            StringEntity entity = new StringEntity(json.toString());
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response: " + responseBody);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

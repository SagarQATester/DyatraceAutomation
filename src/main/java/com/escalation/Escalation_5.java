package com.escalation;


import java.io.*;
import java.util.*;
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

public class Escalation_5 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD"; // Replace with your actual token securely
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089"; // Replace with your actual password securely
    private static final String[] EMAIL_RECIPIENTS = {"sagar.k@believe-it.in"};
    private static final String[] RECIPIENTS = {"+919011734501", "+917058071404"};
    private static final String PROBLEM_STATE_FILE = "problem_state.txt";
    
    // Additional recipient numbers for different severity levels
    private static final String[] CRITICAL_L1_TEAM = {"+9011734501"};
    private static final String[] NON_CRITICAL_L1_TEAM = {"+7058071404"};

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
                Thread.sleep(60000); // Check every 10 seconds
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

                        // Determine if severity is critical or non-critical
                        String severity = "N/A";
                        if ("PERFORMANCE".equalsIgnoreCase(problemSeverity) || "AVAILABILITY".equalsIgnoreCase(problemSeverity)) {
                            severity = "Critical";
                        } else if ("CUSTOM_ALERT".equalsIgnoreCase(problemSeverity)) {
                            severity = "Non-Critical";
                        }

                        // Send the message based on severity
                        if ("Critical".equalsIgnoreCase(severity)) {
                            sendMessageToAll(formattedMessage, CRITICAL_L1_TEAM);
                            System.out.println("Message send to CRITICAL_L1_TEAM ");
                        } else if ("Non-Critical".equalsIgnoreCase(severity)) {
                            sendMessageToAll(formattedMessage, NON_CRITICAL_L1_TEAM);
                            System.out.println("Message send to NON_CRITICAL_L1_TEAM ");
                        }
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

                    // Optionally, send email notifications
                    // sendEmail(EMAIL_RECIPIENTS, message);

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
            String problemID;
            while ((problemID = reader.readLine()) != null) {
                problemID = problemID.trim();
                if (!problemID.isEmpty()) {
                    String problemMessage = getProblemMessage(problemID);
                    if (!"N/A".equals(problemMessage)) {
                        sendMessageToAll(problemMessage, RECIPIENTS);
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

            if (!problemIDs.contains(problemID)) {
                // Append problemID to PROBLEM_STATE_FILE
                BufferedWriter writer = new BufferedWriter(new FileWriter(PROBLEM_STATE_FILE, true));
                writer.write(problemID);
                writer.newLine();
                writer.close();
            }

            // Create problemID.txt with formattedMessage
            BufferedWriter writer = new BufferedWriter(new FileWriter(problemID + ".txt"));
            writer.write(formattedMessage);
            writer.close();

            System.out.println("Saved problem state for " + problemID);

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

            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(problemID)) {
                    found = true;
                    continue; // Skip writing this line
                }
                writer.write(line);
                writer.newLine();
            }
            reader.close();
            writer.close();

            if (!inputFile.delete()) {
                System.out.println("Could not delete the original file.");
                return;
            }

            if (!tempFile.renameTo(inputFile)) {
                System.out.println("Could not rename the temporary file.");
            } else {
                System.out.println("Removed problem state for " + problemID);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getProblemMessage(String problemID) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(problemID + ".txt"));
            StringBuilder messageBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                messageBuilder.append(line).append("\n");
            }
            reader.close();
            return messageBuilder.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
            return "N/A";
        }
    }

    private static String extractPattern(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "N/A";
    }

    private static String getTextFromMessage(Message message) throws IOException, MessagingException {
        StringBuilder sb = new StringBuilder();
        if (message.isMimeType("text/plain")) {
            sb.append(message.getContent().toString());
        } else if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    sb.append(bodyPart.getContent().toString());
                    break; // Assuming there is only one text part
                }
            }
        }
        return sb.toString();
    }

    private static void sendMessageToAll(String message, String[] recipients) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            for (String recipient : recipients) {
                HttpPost httpPost = new HttpPost(API_URL);
                httpPost.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);
                httpPost.setHeader("Content-Type", "application/json");

                JSONObject json = new JSONObject();
                json.put("messaging_product", "whatsapp");
                json.put("to", recipient);
                json.put("type", "text");

                JSONObject text = new JSONObject();
                text.put("body", message);
                json.put("text", text);

                StringEntity entity = new StringEntity(json.toString());
                httpPost.setEntity(entity);

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    System.out.println("Response: " + responseBody);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendEmail(String[] recipients, Message originalMessage) throws IOException {
        // Send email to the specified recipients
        try {
            Session session = Session.getInstance(System.getProperties(), null);
            MimeMessage newMessage = new MimeMessage(session);
            newMessage.setFrom(new InternetAddress(EMAIL_ID));
            for (String recipient : recipients) {
                newMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }
            newMessage.setSubject(originalMessage.getSubject());
            newMessage.setContent(getTextFromMessage(originalMessage), "text/html");
            Transport.send(newMessage);
            System.out.println("Email sent to " + Arrays.toString(recipients));
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private static String prepareResolvedMessage(String problemID) {
        File file = new File(problemID + ".txt");
        if (file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder messageBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    messageBuilder.append(line).append("\n");
                }
                reader.close();
                return messageBuilder.toString().trim();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}


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

public class Escalation_4 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/440426155815858/messages";
    private static final String ACCESS_TOKEN = "EAAFsdRTS8kgBO63ZA5RUA6WWBYaI1CCuDzG3ZBCYaIaqM8ppD1zrolvHgAXQjvqYuttZCJfjgxhsLBpbEzO6pNlQ447xcsM3CPyyMgWbv8A0ZCENzgaxynZBFZCjZAamgW2C11BjtoiDl9qd1BQjnEgDwSp3px1G7iMJ6BQke5USMlL2E0rh0ntSLCFUmexxwTvxAZDZD";
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089"; // Replace with your actual password securely
    private static final String[] EMAIL_RECIPIENTS = {"sagar.k@believe-it.in"};
    private static final String[] RECIPIENTS  = {"+919011734501", "+917058071404"};
    private static final String PROBLEM_STATE_FILE = "problem_state.txt";

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
                Thread.sleep(10000); // Check every 1 minute
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
                        // Send the formatted message to all WA members
                        sendMessageToAll(formattedMessage);
                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        // Prepare and send resolved message
                        String resolvedMessage = prepareResolvedMessage(problemID);
                        if (resolvedMessage != null) {
                            sendMessageToAll(formattedMessage);//resolvedMessage
                            // Remove problem state and delete problemId.txt
                            removeProblemState(problemID);
                        } else {
                            System.out.println("Resolved Problem ID " + problemID + " not found.");
                        }
                    }

                    // Optionally, send email notifications
                     sendEmail(EMAIL_RECIPIENTS, message);

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
                        sendMessageToAll(problemMessage);
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
                System.err.println("Could not delete original PROBLEM_STATE_FILE.");
                return;
            }
            if (!tempFile.renameTo(inputFile)) {
                System.err.println("Could not rename temp_PROBLEM_STATE_FILE.");
            }

            // Delete problemID.txt
            File problemFile = new File(problemID + ".txt");
            if (problemFile.exists()) {
                if (problemFile.delete()) {
                    System.out.println("Deleted " + problemID + ".txt");
                } else {
                    System.err.println("Could not delete " + problemID + ".txt");
                }
            }

            if (found) {
                System.out.println("Removed problem state for " + problemID);
            } else {
                System.out.println("Problem ID " + problemID + " not found in PROBLEM_STATE_FILE.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String extractPattern(String text, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "N/A";
    }

    private static String getTextFromMessage(Message message) throws Exception {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    private static String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent());
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result.append(Jsoup.parse(html).text());
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }
        return result.toString();
    }

    private static void sendMessageToAll(String message) {
        for (String recipient : RECIPIENTS) {
            sendMessage(recipient, message);
        }
    }

    private static void sendMessage(String to, String message) {
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);
            post.setHeader("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            json.put("messaging_product", "whatsapp");
            json.put("to", to);
            json.put("text", new JSONObject().put("body", message));

            StringEntity entity = new StringEntity(json.toString());
            post.setEntity(entity);

            CloseableHttpResponse response = client.execute(post);
            String responseString = EntityUtils.toString(response.getEntity());
            System.out.println("Response for " + to + ": " + responseString);

            response.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendEmail(String[] toAddresses, Message originalMessage) {
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

            for (String toAddress : toAddresses) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
            }

            message.setSubject("Forwarded: " + originalMessage.getSubject());
            message.setText(getTextFromMessage(originalMessage));

            Transport.send(message);
            System.out.println("Email sent successfully.");

        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getProblemMessage(String problemID) {
        try {
            File file = new File(problemID + ".txt");
            if (!file.exists()) {
                return "N/A";
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder message = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                message.append(line).append("\n");
            }
            reader.close();
            return message.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "N/A";
        }
    }

    private static String prepareResolvedMessage(String problemID) {
        String problemDetails = getProblemMessage(problemID);
        if ("N/A".equals(problemDetails)) {
            return null;
        }
        String resolvedMessage = "*Problem Resolved:* \n" + problemDetails;
        return resolvedMessage;
    }
}

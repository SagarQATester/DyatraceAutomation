package com.zoho.mail;

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

public class Escalation_WhatsAppAndMail_2 {

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
                Thread.sleep(5000); // Check every 10 seconds
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
                    String bodyHtml = getHtmlFromMessage(message);

                    // Extract information from subject
                    String problemState = extractPattern(subject, "Problem State\\s*:\\s*(\\w+)");
                    String problemID = extractPattern(subject, "Problem ID\\s*:\\s*(P-\\d+)");
                    String problemSeverity = extractPattern(subject, "Problem Severity\\s*:\\s*(\\w+)");
                    String impactedEntities = extractPattern(subject, "Impacted Entities\\s*:\\s*(.+)");

                    // Save the full HTML email content if the problem is in the open state
                    if ("OPEN".equalsIgnoreCase(problemState)) {
                        saveProblemStateHtml(problemID, bodyHtml);
                        sendMessageToAll("*Problem ID:* " + problemID + "\n" + "Check email for details.");
                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        String resolvedMessage = prepareResolvedMessage(problemID);
                        if (resolvedMessage != null) {
                            sendMessageToAll(resolvedMessage);
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

    private static String getHtmlFromMessage(Message message) throws Exception {
        String result = "";
        if (message.isMimeType("text/html")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getHtmlFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    private static String getHtmlFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/html")) {
                result.append(bodyPart.getContent());
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getHtmlFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }
        return result.toString();
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

    private static void saveProblemStateHtml(String problemID, String htmlContent) {
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

            // Save the HTML email content to problemID.html
            BufferedWriter writer = new BufferedWriter(new FileWriter(problemID + ".html"));
            writer.write(htmlContent);
            writer.close();

            System.out.println("Saved problem state for " + problemID + " in HTML format.");

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

            // Delete problemID.html
            File problemFile = new File(problemID + ".html");
            if (problemFile.exists()) {
                if (problemFile.delete()) {
                    System.out.println("Deleted " + problemID + ".html");
                } else {
                    System.err.println("Could not delete " + problemID + ".html");
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

    private static String prepareResolvedMessage(String problemID) {
        // Customize the resolved message
        return "The problem with ID " + problemID + " has been resolved.";
    }

    private static String getProblemMessage(String problemID) {
        File file = new File(problemID + ".html");
        if (!file.exists()) {
            return "N/A";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("*Problem ID:* ").append(problemID).append("\n");
        sb.append("Details are available in the email content.");

        return sb.toString();
    }

    private static void sendMessageToAll(String message) {
        for (String recipient : RECIPIENTS) {
            sendWhatsAppMessage(recipient, message);
        }
    }

    private static void sendWhatsAppMessage(String recipient, String message) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject body = new JSONObject();
            JSONObject messagingProduct = new JSONObject();
            messagingProduct.put("messaging_product", "whatsapp");
            messagingProduct.put("to", recipient);
            messagingProduct.put("text", new JSONObject().put("body", message));

            body.put("recipient_type", "individual");
            body.put("to", recipient);
            body.put("type", "text");
            body.put("text", new JSONObject().put("body", message));

            httpPost.setEntity(new StringEntity(body.toString()));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response from WhatsApp API: " + responseBody);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendEmail(String[] recipients, Message message) {
        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", "smtp.zoho.in");
            properties.put("mail.smtp.port", "587");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(EMAIL_ID, EMAIL_PASSWORD);
                }
            });

            MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(EMAIL_ID));

            for (String recipient : recipients) {
                mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }

            mimeMessage.setSubject(message.getSubject());
            mimeMessage.setContent(message.getContent(), message.getContentType());

            Transport.send(mimeMessage);
            System.out.println("Email sent successfully to " + Arrays.toString(recipients));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

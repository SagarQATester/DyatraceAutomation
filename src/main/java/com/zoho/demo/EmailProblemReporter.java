package com.zoho.demo;


import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import java.awt.Robot;
import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EmailProblemReporter {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD";
    private static final String REPORTED_IDS_FILE = "reported_ids.txt"; // File to store reported IDs
    private static final String EMAIL = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String PASSWORD = "Itb@M7089";
    private static final String HOST = "mail.zoho.in";
    private static final String FOLDER = "INBOX";

    public static void main(String[] args) {


        Set<String> reportedIds = loadReportedIds();

        // Start email scanning
        while (true) { // Continuous monitoring loop
            try {
                // Connect to the email account
                Properties properties = new Properties();
                properties.put("mail.store.protocol", "imaps");
                Session session = Session.getDefaultInstance(properties, null);
                Store store = session.getStore("imaps");
                store.connect(HOST, EMAIL, PASSWORD);

                // Access the inbox folder
                Folder folder = store.getFolder(FOLDER);
                folder.open(Folder.READ_WRITE);

                // Search for unread emails
                Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

                for (Message message : messages) {
                    String subject = message.getSubject();
                    String content = getTextFromMessage(message);

                    // Assume the problem ID is in the subject or content
                    String problemId = extractProblemId(subject, content);

                    if (problemId != null && !reportedIds.contains(problemId)) { // Check if ID has been reported
                        JSONObject details = new JSONObject();
                        details.put("Subject", subject);
                        details.put("Content", content);

                        String messageText = formatMessage("Problem Details", details);
                        String[] recipients = {"+919011734501", "+917058071404"};
                        for (String recipient : recipients) {
                            sendMessage(recipient, messageText);
                        }

                        reportedIds.add(problemId); // Add to reported IDs
                        saveReportedId(problemId); // Save to file
                    }
                    
                    // Mark email as read
                    message.setFlag(Flags.Flag.SEEN, true);
                }

                folder.close(false);
                store.close();
                
                // Sleep for 5 seconds before the next email scan
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String formatMessage(String heading, JSONObject details) {
        StringBuilder message = new StringBuilder();
        message.append(heading).append("\n\n");
        for (String key : details.keySet()) {
            message.append(key).append(": ").append(details.getString(key)).append("\n");
        }
        return message.toString();
    }

    public static void sendMessage(String to, String message) {
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);
            post.setHeader("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            json.put("messaging_product", "whatsapp");
            json.put("to", to);
            json.put("type", "text");

            JSONObject messageContent = new JSONObject();
            messageContent.put("body", message);

            json.put("text", messageContent);

            StringEntity entity = new StringEntity(json.toString());
            post.setEntity(entity);

            CloseableHttpResponse response = client.execute(post);

            // Get the current timestamp
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedNow = now.format(formatter);

            // Print the message, recipient, and time to the console
            System.out.println("Time: " + formattedNow);
            System.out.println("Sending to: " + to);
            System.out.println("Message: " + message);
            System.out.println("Response Status: " + response.getStatusLine());
            System.out.println("Response Body: " + EntityUtils.toString(response.getEntity()));

            response.close();
        } catch (Exception e) {
            System.err.println("Error sending message to " + to + ": " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                System.err.println("Error closing HTTP client: " + e.getMessage());
            }
        }
    }

    private static Set<String> loadReportedIds() {
        Set<String> reportedIds = new HashSet<>();
        File file = new File(REPORTED_IDS_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    reportedIds.add(line.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return reportedIds;
    }

    private static void saveReportedId(String problemId) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(REPORTED_IDS_FILE, true))) {
            writer.write(problemId);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                result.append(org.jsoup.Jsoup.parse(html).text());
            }
        }
        return result.toString();
    }

    private static String extractProblemId(String subject, String content) {
        // Implement your logic to extract the problem ID from the subject or content
        // For example, you might look for a specific pattern in the subject or content
        return null; // Placeholder, replace with your extraction logic
    }
}


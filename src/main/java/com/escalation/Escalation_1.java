package com.escalation;

import java.io.IOException;
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

public class Escalation_1 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD";
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089";
    private static final String[] RECIPIENTS = {"+919011734501", "+917058071404"};
    
    // Thresholds in minutes
    private static final int CRITICAL_THRESHOLD_MINUTES = 1;
    private static final int NON_CRITICAL_THRESHOLD_1_MINUTES = 2;
    private static final int NON_CRITICAL_THRESHOLD_2_MINUTES = 3;

    // Store Problem IDs with their state and timestamps
    private static final Map<String, Long> problemStates = new HashMap<>();

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
                Thread.sleep(1000); // Check every second
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
                Address[] fromAddresses = message.getFrom();
                if (fromAddresses != null && fromAddresses.length > 0 && fromAddresses[0].toString().contains("no-reply@dynatrace.com")) {

                    String subject = message.getSubject();
                    String body = getTextFromMessage(message);

                    String problemState = extractPattern(subject, "Problem State\\s*:\\s*(\\w+)");
                    String problemID = extractPattern(subject, "Problem ID\\s*:\\s*(P-\\d+)");
                    String problemSeverity = extractPattern(subject, "Problem Severity\\s*:\\s*(\\w+)");
                    String impactedEntities = extractPattern(subject, "Impacted Entities\\s*:\\s*(.+)");
                    String environment = extractPattern(body, "environment\\s*(\\w+)");
                    String host = extractPattern(body, "Host\\s*(.+)");
                    String rootCause = extractPattern(body, "Root cause\\s*(.+)");
                    String problemLink = extractPattern(body, "(https?://\\S+)");

                    // Store problem state and timestamp
                    long currentTime = System.currentTimeMillis();
                    if (problemState.equals("OPEN")) {
                        problemStates.put(problemID, currentTime);
                    }

                    // Determine severity
                    String severity = problemSeverity;
                    boolean isCritical = severity.equals("PERFORMANCE") || severity.equals("AVAILABILITY");
                    
                    // Handle reporting based on severity and time
                    handleReporting(problemID, problemState, severity, currentTime);

                    // Mark the email as read
                    message.setFlag(Flags.Flag.SEEN, true);
                }
            }

            notificationFolder.close(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleReporting(String problemID, String problemState, String severity, long currentTime) {
        long problemTime = problemStates.getOrDefault(problemID, currentTime);
        long elapsedTime = (currentTime - problemTime) / 60000; // Convert milliseconds to minutes

        if (problemState.equals("OPEN")) {
            if (isCritical(severity)) {
                if (elapsedTime <= CRITICAL_THRESHOLD_MINUTES) {
                    reportToL1();
                    System.out.println("Critical Issue Reported Immidiately TO L1");
                } else {
                    reportToL2();
                    System.out.println("Critical Issue Reported To L2");

                }
            } else {
                if (elapsedTime > NON_CRITICAL_THRESHOLD_1_MINUTES && elapsedTime <= NON_CRITICAL_THRESHOLD_2_MINUTES) {
                    reportToL1();
                    System.out.println("Non Critical Issue Reported  TO L1");

                } else if (elapsedTime > NON_CRITICAL_THRESHOLD_2_MINUTES) {
                    reportToL2();
                    System.out.println("Non Critical Issue Reported  TO L2");

                }
            }
        }
    }

    private static boolean isCritical(String severity) {
        return severity.equals("PERFORMANCE") || severity.equals("AVAILABILITY");
    }

    private static void reportToL1() {
        String message = "Immediate report to L1";
        for (String recipient : RECIPIENTS) {
            sendMessage(recipient, message);
        }
    }

    private static void reportToL2() {
        String message = "Report to L2";
        for (String recipient : RECIPIENTS) {
            sendMessage(recipient, message);
        }
    }

    private static String extractPattern(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
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

    private static void sendMessage(String to, String message) {
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
            System.out.println("Response Status: " + response.getStatusLine());
            System.out.println("Response Body: " + EntityUtils.toString(response.getEntity()));

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
}

package com.escalation;
import java.io.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class Escalation_2 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD";
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089";
    private static final String[] EMAIL_RECIPIENTS = {""};
    private static final String[] RECIPIENTS = {"+919011734501", "+917058071404"};

    private static final String CRITICAL_L1_TEAM = "L1: 9619841083, dhaval.yadav.gw@hitachi-systems.com";
    private static final String NON_CRITICAL_L1_TEAM = "L1: 9619841083, dhaval.yadav.gw@hitachi-systems.com";

    private static final Set<String> processedProblemIDs = new HashSet<>();

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

                    if (!processedProblemIDs.contains(problemID)) {
                        String formattedMessage = "*Problem State:* " + problemState + "\n" +
                                "*Problem ID:* " + problemID + "\n" +
                                "*Problem Severity:* " + problemSeverity + "\n" +
                                "*Impacted Entities:* " + impactedEntities + "\n" +
                                "*Environment:* " + environment + "\n" +
                                "*Host:* " + host + "\n" +
                                "*Root cause:* " + rootCause + "\n" +
                                "*Problem Link:* " + problemLink;

                        // Store Problem ID and State
                        processedProblemIDs.add(problemID);

                        // Handle problem based on severity
                        handleProblem(problemID, problemState, problemSeverity);

                        // Send WhatsApp message
                        for (String recipient : RECIPIENTS) {
                            sendMessage(recipient, formattedMessage);
                        }

                        // Send email with original content
                        sendEmail(EMAIL_RECIPIENTS, message);

                        // Mark the email as read
                        message.setFlag(Flags.Flag.SEEN, true);
                    }
                }
            }

            notificationFolder.close(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleProblem(String problemID, String problemState, String problemSeverity) {
        // Determine severity classification
        boolean isCritical = problemSeverity.equals("PERFORMANCE") || problemSeverity.equals("AVAILABILITY");
        boolean isNonCritical = problemSeverity.equals("CUSTOM_ALERT");

        if (problemState.equals("OPEN")) {
            if (isCritical) {
                // Report critical problem
                reportToTeam(CRITICAL_L1_TEAM);
            } else if (isNonCritical) {
                // Report non-critical problem
                reportToTeam(NON_CRITICAL_L1_TEAM);
            }
        }
    }

    private static void reportToTeam(String teamInfo) {
        // Logic to notify the team
        System.out.println("Reporting to team: " + teamInfo);
        // Add your notification logic here (e.g., send an email or message)
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

    private static void sendEmail(String[] recipients, Message originalMessage) {
        // Logic to send an email with the original content
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.zoho.in");
            props.put("mail.smtp.port", "465");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(EMAIL_ID, EMAIL_PASSWORD);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_ID));
            for (String recipient : recipients) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }
            message.setSubject("Forwarded Dynatrace Alert");
            message.setContent(getTextFromMessage(originalMessage), "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("Email sent successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

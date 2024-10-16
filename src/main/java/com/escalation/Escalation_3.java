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

public class Escalation_3 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD";
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089";
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
                Thread.sleep(1000); // Check every 1 minute
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

                    String formattedMessage = "*Problem State:* " + problemState + "\n" +
                            "*Problem ID:* " + problemID + "\n" +
                            "*Problem Severity:* " + problemSeverity + "\n" +
                            "*Impacted Entities:* " + impactedEntities + "\n" +
                            "*Environment:* " + environment + "\n" +
                            "*Host:* " + host + "\n" +
                            "*Root cause:* " + rootCause + "\n" +
                            "*Problem Link:* " + problemLink;

                    if ("OPEN".equalsIgnoreCase(problemState)) {
                        saveProblemState(problemID, problemState);
                        for (String recipient : RECIPIENTS) {
                            sendMessage(recipient, formattedMessage);
                        }
                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        removeProblemState(problemID);
                    }

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
            BufferedReader reader = new BufferedReader(new FileReader(PROBLEM_STATE_FILE));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String problemID = parts[0].trim();
                    String problemState = parts[1].trim();

                    if ("OPEN".equalsIgnoreCase(problemState)) {
                        // Send the message again if the problem is still open
                        String message = getProblemMessage(problemID);
                        for (String recipient : RECIPIENTS) {
                            sendMessage(recipient, message);
                        }
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveProblemState(String problemID, String problemState) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROBLEM_STATE_FILE, true))) {
            writer.write(problemID + ":" + problemState);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void removeProblemState(String problemID) {
        try {
            File inputFile = new File(PROBLEM_STATE_FILE);
            File tempFile = new File("temp_" + PROBLEM_STATE_FILE);

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(problemID + ":")) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            reader.close();
            writer.close();

            inputFile.delete();
            tempFile.renameTo(inputFile);
        } catch (IOException e) {
            e.printStackTrace();
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
            json.put("text", new JSONObject().put("body", message));

            StringEntity entity = new StringEntity(json.toString());
            post.setEntity(entity);

            CloseableHttpResponse response = client.execute(post);
            System.out.println(EntityUtils.toString(response.getEntity()));
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
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.zoho.in");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_ID, EMAIL_PASSWORD);
            }
        });

        try {
            MimeMessage newMessage = new MimeMessage(session);
            newMessage.setFrom(new InternetAddress(EMAIL_ID));
            for (String recipient : recipients) {
                newMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            }
            newMessage.setSubject("Forwarded: " + originalMessage.getSubject());
            newMessage.setContent(originalMessage.getContent(), originalMessage.getContentType());

            Transport.send(newMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static String getProblemMessage(String problemID) {
        // Retrieve the message for a given Problem ID
        // For demonstration purposes, we'll return a static message
        // Replace this with your actual implementation
        return "*Problem ID:* " + problemID + "\n*Status:* OPEN";
    }
}


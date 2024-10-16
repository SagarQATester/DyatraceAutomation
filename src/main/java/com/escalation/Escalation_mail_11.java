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
import org.jsoup.Jsoup;

public class Escalation_mail_11 {

    private static final String API_URL = "https://graph.facebook.com/v20.0/440426155815858/messages";
    private static final String ACCESS_TOKEN = "EAAFsdRTS8kgBO63ZA5RUA6WWBYaI1CCuDzG3ZBCYaIaqM8ppD1zrolvHgAXQjvqYuttZCJfjgxhsLBpbEzO6pNlQ447xcsM3CPyyMgWbv8A0ZCENzgaxynZBFZCjZAamgW2C11BjtoiDl9qd1BQjnEgDwSp3px1G7iMJ6BQke5USMlL2E0rh0ntSLCFUmexxwTvxAZDZD";
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089"; // Replace with your actual password securely
    private static final String PROBLEM_STATE_FILE = "probleState_Mail.txt";
    
    private static final String[] CRITICAL_L1_TEAM = {"+919011734501"};
    private static final String[] CRITICAL_L2_TEAM = {"+917058071404"};
    private static final String[] NON_CRITICAL_L1_TEAM = {"+917058071404"};
    private static final String[] NON_CRITICAL_L2_TEAM = {"+919011734501"};
    private static final String[] CRITICAL_L1_EMAILS = {"sagar.k@believe-it.in"};
    private static final String[] CRITICAL_L2_EMAILS = {"criticall2@believe-it.in"};
    private static final String[] NON_CRITICAL_L1_EMAILS = {"sagar.k@believe-it.in"};
    private static final String[] NON_CRITICAL_L2_EMAILS = {"noncriticall2@believe-it.in"};

    private static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
    private static final Map<String, String[]> escalationContacts = new HashMap<>();
    private static final Map<String, Long> problemDetectionTimes = new HashMap<>();
    private static final ConcurrentHashMap<String, Boolean> l2MessageSentMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> l1MessageSentMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<String>> notifiedTeamsMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> problemSeverityMap = new ConcurrentHashMap<>();

    static {
        escalationTimeFrames.put("Critical_L1", 0); // 0 minutes for L1
        escalationTimeFrames.put("Critical_L2", 1); // 1 minutes for L2
        escalationTimeFrames.put("NonCritical_L1", 0); // 0 minutes for L1
        escalationTimeFrames.put("NonCritical_L2", 1); // 1 minutes for L2

        escalationContacts.put("Critical_L1", CRITICAL_L1_TEAM);
        escalationContacts.put("Critical_L2", CRITICAL_L2_TEAM);
        escalationContacts.put("NonCritical_L1", NON_CRITICAL_L1_TEAM);
        escalationContacts.put("NonCritical_L2", NON_CRITICAL_L2_TEAM);

        // Adding email contacts
        escalationContacts.put("Critical_L1_Email", CRITICAL_L1_EMAILS);
        escalationContacts.put("Critical_L2_Email", CRITICAL_L2_EMAILS);
        escalationContacts.put("NonCritical_L1_Email", NON_CRITICAL_L1_EMAILS);
        escalationContacts.put("NonCritical_L2_Email", NON_CRITICAL_L2_EMAILS);
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

                        // Save email content to problemId_Mail.txt
                        saveEmailContent(problemID, subject, body);

                        // Save problem state to probleState_Mail.txt
                        saveProblemState(problemID, problemState, severity, formattedMessage);

                        problemSeverityMap.put(problemID, severity);

                        System.out.println("Stored Severity and Problem State for Problem ID " + problemID + ": " + "Problem State :" + problemState + " ProblemSeverity :" + severity);

                        startEscalationTimer(problemID, formattedMessage, severity);

                    } else if ("RESOLVED".equalsIgnoreCase(problemState)) {
                        System.out.println("Processing Resolved State for Problem ID " + problemID);
                        removeProblemState(problemID, formattedMessage);
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
                                // Problem resolved within the escalation period
                                continue;
                            }
                        }

                        if ("OPEN".equalsIgnoreCase(problemState)) {
                            // Proceed with escalation logic for email
                            sendEscalationEmails(problemID, severity);
                            sendWhatsMessage(problemID, severity);
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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



    private static void startEscalationTimer(String problemID, String formattedMessage, String severity) {
        long detectionTime = System.currentTimeMillis();
        problemDetectionTimes.put(problemID, detectionTime);
        l1MessageSentMap.put(problemID, false);
        l2MessageSentMap.put(problemID, false);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendEscalationEmails(problemID, severity);
                sendWhatsMessage(problemID, severity);
            }
        }, escalationTimeFrames.getOrDefault(severity + "_L1", 0) * 60 * 1000L);
    }

    private static void sendEscalationEmails(String problemID, String severity) {
        try {
            // Load previously sent email addresses
            String[] l1Emails = escalationContacts.getOrDefault(severity + "_L1_Email", new String[]{});
            String[] l2Emails = escalationContacts.getOrDefault(severity + "_L2_Email", new String[]{});
            boolean l1Sent = l1MessageSentMap.getOrDefault(problemID, false);
            boolean l2Sent = l2MessageSentMap.getOrDefault(problemID, false);

            if (!l1Sent) {
                sendEmails(problemID, l1Emails);
                l1MessageSentMap.put(problemID, true);
            }
            if (!l2Sent) {
                sendEmails(problemID, l2Emails);
                l2MessageSentMap.put(problemID, true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendEmails(String problemID, String[] emails) throws MessagingException {
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

        for (String email : emails) {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_ID));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Problem ID: " + problemID + " Escalation Notification");
            message.setText("This is an escalation notification for Problem ID: " + problemID);
            Transport.send(message);
        }
    }

    private static void sendWhatsMessage(String problemID, String severity) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String[] l1Numbers = escalationContacts.getOrDefault(severity + "_L1", new String[]{});
            String[] l2Numbers = escalationContacts.getOrDefault(severity + "_L2", new String[]{});

            sendMessagesToNumbers(client, l1Numbers, problemID, severity);
            sendMessagesToNumbers(client, l2Numbers, problemID, severity);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendMessagesToNumbers(CloseableHttpClient client, String[] numbers, String problemID, String severity) throws IOException {
        for (String number : numbers) {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Authorization", "Bearer " + ACCESS_TOKEN);
            post.setHeader("Content-Type", "application/json");

            String jsonPayload = String.format(
                    "{\"messaging_product\": \"whatsapp\", \"to\": \"%s\", \"type\": \"text\", \"text\": {\"body\": \"Problem ID: %s, Severity: %s\"}}",
                    number, problemID, severity);

            post.setEntity(new StringEntity(jsonPayload));
            try (CloseableHttpResponse response = client.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("WhatsApp Message Response: " + responseBody);
            }
        }
    }

    private static void saveEmailContent(String problemID, String subject, String body) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(problemID + "_Mail.txt"))) {
            writer.write("Subject: " + subject + "\n");
            writer.write("Body: " + body);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveProblemState(String problemID, String problemState, String severity, String formattedMessage) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROBLEM_STATE_FILE, true))) {
            writer.write(problemID + " " + problemState + " " + severity + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void removeProblemState(String problemID, String formattedMessage) {
        File file = new File(PROBLEM_STATE_FILE);
        if (!file.exists()) {
            return;
        }

        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith(problemID)) {
                        lines.add(line);
                    }
                }
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String line : lines) {
                    writer.write(line + "\n");
                }
            }

            // Remove related files
            File mailFile = new File(problemID + "_Mail.txt");
            if (mailFile.exists()) {
                mailFile.delete();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String extractPattern(String input, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(input);
        return m.find() ? m.group(1) : "";
    }

    private static String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("text/html")) {
            String html = (String) message.getContent();
            return Jsoup.parse(html).text();
        }
        return "";
    }
}

package com.hitachi.problemdetector;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

public class DynatraceMonitor {

    private static final String API_URL = "https://plg32786.live.dynatrace.com/api/v2/problems";
    private static final String API_TOKEN = "dt0c01.2AW3N6IEQ7ST2MB4QBFOZKF6.NL3K7UGJ3XS7CLWYWWULAX2SAN5GCOFKNXTMNNAIJXWNMBVQ25PSLIN7QOGLLFI5";
    private static final String WHATSAPP_API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String WHATSAPP_ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD";
    private static final String REPORTED_IDS_FILE = "reported_ids.txt"; // File to store reported IDs
    private static final String[] RECIPIENTS = {"+919011734501", "+917058071404"};

    public static void main(String[] args) {
        Set<String> reportedIds = loadReportedIds();

        while (true) { // Continuous monitoring loop
            try {
                JSONArray problems = fetchProblems();
                for (int i = 0; i < problems.length(); i++) {
                    JSONObject problem = problems.getJSONObject(i);
                    String problemId = problem.getString("problemId");

                    if (!reportedIds.contains(problemId)) { // Check if ID has been reported
                        String message = formatMessage(problem);
                        for (String recipient : RECIPIENTS) {
                            sendMessage(recipient, message);
                        }

                        reportedIds.add(problemId); // Add to reported IDs
                        saveReportedId(problemId); // Save to file
                       
                    }
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("I am executed");
            // Sleep for a while before fetching data again
            try {
                Thread.sleep(5000); // 10 sec delay between each API call
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static JSONArray fetchProblems() throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet request = new HttpGet(API_URL);
        request.setHeader("Authorization", "Api-Token " + API_TOKEN);
        request.setHeader("accept", "application/json; charset=utf-8");

        CloseableHttpResponse response = client.execute(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        JSONObject jsonResponse = new JSONObject(responseBody);
        JSONArray problems = jsonResponse.getJSONArray("problems");

        response.close();
        client.close();

        return problems;
    }

    private static String formatMessage(JSONObject problem) {
        StringBuilder message = new StringBuilder();
        message.append("Problem ID: ").append(problem.getString("displayId")).append("\n");
        message.append("Title: ").append(problem.getString("title")).append("\n");
        message.append("Impact Level: ").append(problem.getString("impactLevel")).append("\n");
        message.append("Severity Level: ").append(problem.getString("severityLevel")).append("\n");
        message.append("Status: ").append(problem.getString("status")).append("\n");
        message.append("Start Time: ").append(problem.getLong("startTime")).append("\n");

        return message.toString();
    }

    public static void sendMessage(String to, String message) {
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            HttpPost post = new HttpPost(WHATSAPP_API_URL);
            post.setHeader("Authorization", "Bearer " + WHATSAPP_ACCESS_TOKEN);
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
}


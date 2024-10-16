package com.hitachi.problemdetector;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

import java.awt.Robot;
import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ProblemReporter {

    private static final String API_URL = "https://graph.facebook.com/v20.0/326080960599185/messages";
    private static final String ACCESS_TOKEN = "EAAQqqd6t5AMBO3oktCNs60JFdhiVbtvyZBZAjDNjEC8fufT3WF5XlzZAcFRoPkxx5OH4FtFklrwdmqzeFLjGuqceZBnUQL3oeTUtLgnTfnA5rpne6ZCqWr8afYZCdScZAdMF1bLKZCn1MTZAMr3R7u6x6lBevoyMZBylpodIBj9hsGM490hsMdlYK4JekPqO1L8uvrWwZDZD";
    private static final String REPORTED_IDS_FILE = "reported_ids.txt"; // File to store reported IDs

    public static void main(String[] args) {
        // Start the sleep prevention in a separate thread
        new Thread(() -> {
            try {
                Robot robot = new Robot();
                while (true) {
                    robot.mouseMove(0, 0);
                    robot.mouseMove(1, 1);
                    Thread.sleep(40000);  // Sleep for 40 seconds
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    //	WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver();
        try {
            driver.get("https://sso.dynatrace.com/");
            driver.manage().window().maximize();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            driver.findElement(By.name("email")).sendKeys("sagar.k@believe-it.in");
            driver.findElement(By.cssSelector("[type='submit']")).click();
            driver.findElement(By.cssSelector("[type='password']")).sendKeys("Bit@1234567891");
            driver.findElement(By.cssSelector(".fwSaCT")).click();
            Thread.sleep(5000);

            driver.switchTo().newWindow(WindowType.TAB);
            Set<String> windowHandles = driver.getWindowHandles();
            Iterator<String> iterator = windowHandles.iterator();
            String originalWindow = iterator.next();
            String newWindow = iterator.next();
            driver.switchTo().window(newWindow);

            Set<String> reportedIds = loadReportedIds();

            while (true) { // Continuous monitoring loop
                try {
                    driver.get("https://wkf10640.apps.dynatrace.com/ui");
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
                    wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//span[contains(text(),'Problems')]"))).click();
                    wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("app-iframe")));
                    
                    WebElement table = driver.findElement(By.className("strato-table-virtualization-container"));
                    List<WebElement> headers = table.findElements(By.cssSelector("div[role='columnheader']"));
                    int statusIndex = -1;
                    int idIndex = -1;

                    for (int i = 0; i < headers.size(); i++) {
                        String headerText = headers.get(i).getText().trim();
                        if (headerText.equals("Status")) {
                            statusIndex = i;
                        }
                        if (headerText.equals("ID")) {
                            idIndex = i;
                        }
                    }

                    List<WebElement> rows = driver.findElements(By.cssSelector("[class='strato-loading-wrapper'] [role='row']"));
                    for (WebElement row : rows) {
                        List<WebElement> cells = row.findElements(By.cssSelector("div[role='cell']"));
                        if (cells.size() > statusIndex && "Active".equals(cells.get(statusIndex).getText())) {
                            String problemId = cells.get(idIndex).getText().trim();
                            if (!reportedIds.contains(problemId)) { // Check if ID has been reported
                                JSONObject details = new JSONObject();
                                for (int i = 0; i < headers.size(); i++) {
                                    if (!headers.get(i).getText().trim().isEmpty()) {
                                        details.put(headers.get(i).getText().trim(), 
                                        		cells.get(i).getText().trim());
                                    }
                                }

                                String message = formatMessage("Problem Details", details);
                                String[] recipients = {"+919011734501", "+917058071404"};
                                for (String recipient : recipients) {
                                    sendMessage(recipient, message);
                                }

                                reportedIds.add(problemId); // Add to reported IDs
                                saveReportedId(problemId); // Save to file
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error while processing the page: " + e.getMessage());
                }

                // Sleep for 5 seconds before refreshing the page
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit(); // Ensure the WebDriver quits even if an error occurs
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
}

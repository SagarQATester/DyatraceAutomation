package com.zoho.mail;

import java.io.IOException;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;

public class MailToWhatsApp {

	   private static final String API_URL = "https://graph.facebook.com/v20.0/440426155815858/messages";
	    private static final String ACCESS_TOKEN = "EAAFsdRTS8kgBO63ZA5RUA6WWBYaI1CCuDzG3ZBCYaIaqM8ppD1zrolvHgAXQjvqYuttZCJfjgxhsLBpbEzO6pNlQ447xcsM3CPyyMgWbv8A0ZCENzgaxynZBFZCjZAamgW2C11BjtoiDl9qd1BQjnEgDwSp3px1G7iMJ6BQke5USMlL2E0rh0ntSLCFUmexxwTvxAZDZD";
    private static final String EMAIL_ID = "incidenttest.believeit-ext@hitachi-systems.co.in";
    private static final String EMAIL_PASSWORD = "Itb@M7089";

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
                Thread.sleep(1000); // Check every 5 seconds
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
                String subject = message.getSubject();
                String body = getTextFromMessage(message);

                String formattedMessage = "Subject: " + subject + "\n\nBody:\n" + body;
                String[] recipients = {"+919011734501", "+917058071404"};

                for (String recipient : recipients) {
                    sendMessage(recipient, formattedMessage);
                }

                // Mark the email as read
                message.setFlag(Flags.Flag.SEEN, true);
            }

            notificationFolder.close(false);

        } catch (Exception e) {
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

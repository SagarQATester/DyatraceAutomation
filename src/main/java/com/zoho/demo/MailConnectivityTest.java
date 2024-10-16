package com.zoho.demo;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.Store;

public class MailConnectivityTest {
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", "imap.zoho.in");
        properties.put("mail.imaps.port", "993");
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.ssl.trust", "imap.zoho.in"); // Trusting the host explicitly

        try {
            Session session = Session.getDefaultInstance(properties, null);
            Store store = session.getStore();
            store.connect("incidenttest.believeit-ext@hitachi-systems.co.in", "Itb@M7089");
            System.out.println("Connected successfully!");
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

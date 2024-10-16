package com.voice.message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {
    public static String getCurrentDateTime() {
        // Define the date and time format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();
        
        // Format and return as a string
        return now.format(formatter);
    }

    public static void main(String[] args) {
        // Example usage
        System.out.println("Current Date and Time: " + getCurrentDateTime());
    }
}


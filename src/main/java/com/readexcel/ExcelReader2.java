package com.readexcel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ExcelReader2 {

    private static final String ESCALATION_MATRIX_SHEET = "Escalation Matrix";
    private static final String LOGIN_CREDENTIALS_SHEET = "Login Credentials";

    private static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
    private static final Map<String, String[]> escalationContacts = new HashMap<>();
    private static final Map<String, String[]> escalationMails = new HashMap<>();
    public static final Map<String, String[]> escalationContacts_Call = new HashMap<>();
    private static String emailId;
    private static String emailPassword;

    public static void main(String[] args) {
        loadDataFromExcel("F:\\SeleniumProject\\DyatraceAutomation\\TestData\\EscalationMatrix.xlsx");
        printData();
    }

    private static void loadDataFromExcel(String filePath) {
    	   try (FileInputStream file = new FileInputStream(filePath);
    	             Workbook workbook = new XSSFWorkbook(file)) {

    	            // Load login credentials
    	            Sheet loginSheet = workbook.getSheet(LOGIN_CREDENTIALS_SHEET);
    	            if (loginSheet != null) {
    	                Row loginRow = loginSheet.getRow(1); // Assuming the data is in the second row (0-based index)
    	                if (loginRow != null) {
    	                    emailId = getCellAsString(loginRow.getCell(0));
    	                    emailPassword = getCellAsString(loginRow.getCell(1));
    	                }
    	            }

    	            // Load escalation matrix
    	            Sheet escalationSheet = workbook.getSheet(ESCALATION_MATRIX_SHEET);
    	            if (escalationSheet != null) {
    	                for (Row row : escalationSheet) {
    	                    if (row.getRowNum() == 0) continue; // Skip header row

    	                    Integer escalationTimeFrame = null;
    	                    String teamType = null;
    	                    String teamContact = "";
    	                    String emailAddress = "";
    	                    String teamContact_Call = "";

    	                    Cell cell = row.getCell(0);
    	                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
    	                        escalationTimeFrame = (int) cell.getNumericCellValue();
    	                    }

    	           

    	                    teamType=getCellAsString(row.getCell(1));
    	                    teamContact = getCellAsString(row.getCell(2));
    	                    emailAddress = getCellAsString(row.getCell(3));
    	                    teamContact_Call = getCellAsString(row.getCell(4));

    	                    if (teamType != null && escalationTimeFrame != null) {
    	                        // Update escalation time frames
    	                        escalationTimeFrames.put(teamType, escalationTimeFrame);

    	                        // Update escalation contacts
    	                        escalationContacts.computeIfAbsent(teamType, k -> new String[0]);
    	                        escalationContacts.put(teamType, appendToArray(escalationContacts.get(teamType), teamContact));

    	                        // Update escalation emails
    	                        escalationMails.computeIfAbsent(teamType, k -> new String[0]);
    	                        escalationMails.put(teamType, appendToArray(escalationMails.get(teamType), emailAddress));
    	                    
    	                        // Update escalation Contact for call
    	                        escalationContacts_Call.computeIfAbsent(teamType, k -> new String[0]);
    	                        escalationContacts_Call.put(teamType, appendToArray(escalationContacts_Call.get(teamType), teamContact_Call));
    	                    
    	                    }
    	                }
    	            }

    	        } catch (IOException e) {
    	            e.printStackTrace();
    	        }
    	    }

    private static String getCellAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return "";
        }
    }

    private static String[] appendToArray(String[] array, String value) {
        String[] newArray = new String[array.length + 1];
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[array.length] = value;
        return newArray;
    }

    private static void printData() {
        System.out.println("Email ID: " + emailId);
        System.out.println("Email Password: " + emailPassword);

        System.out.println("\nEscalation Time Frames:");
        for (Map.Entry<String, Integer> entry : escalationTimeFrames.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\nEscalation Contacts:");
        for (Map.Entry<String, String[]> entry : escalationContacts.entrySet()) {
            System.out.println(entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }

        System.out.println("\nEscalation Emails:");
        for (Map.Entry<String, String[]> entry : escalationMails.entrySet()) {
            System.out.println(entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }
        
        System.out.println("\nEscalation Contact Number for Calling:");
        for (Map.Entry<String, String[]> entry : escalationContacts_Call.entrySet()) {
            System.out.println(entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }
    }
}

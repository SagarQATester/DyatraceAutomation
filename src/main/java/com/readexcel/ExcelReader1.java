package com.readexcel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ExcelReader1 {

    private static final String ESCALATION_MATRIX_SHEET = "Escalation Matrix";
    private static final String LOGIN_CREDENTIALS_SHEET = "Login Credentials";

    private static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
    private static final Map<String, String[]> escalationContacts = new HashMap<>();
    private static final Map<String, String[]> escalationMails = new HashMap<>();
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
            Row loginRow = loginSheet.getRow(1); // Assuming the data is in the second row (0-based index)
            emailId = loginRow.getCell(0).getStringCellValue();
            emailPassword = loginRow.getCell(1).getStringCellValue();

            // Load escalation matrix
            Sheet escalationSheet = workbook.getSheet(ESCALATION_MATRIX_SHEET);
            for (Row row : escalationSheet) {
                if (row.getRowNum() == 0) continue; // Skip header row

                int escalationTimeFrame = (int) row.getCell(0).getNumericCellValue();
                String teamType = row.getCell(1).getStringCellValue();
                String teamContact = getCellAsString(row.getCell(2));
                String emailAddress = row.getCell(3).getStringCellValue();

                // Update escalation time frames
                escalationTimeFrames.put(teamType, escalationTimeFrame);

                // Update escalation contacts
                escalationContacts.computeIfAbsent(teamType, k -> new String[0]);
                escalationContacts.put(teamType, appendToArray(escalationContacts.get(teamType), teamContact));

                // Update escalation emails
                escalationMails.computeIfAbsent(teamType, k -> new String[0]);
                escalationMails.put(teamType, appendToArray(escalationMails.get(teamType), emailAddress));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static String getCellAsString(Cell cell) {
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue()); // Convert numeric to long and then to String
        } else {
            // Handle other cell types if needed
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
    }
}


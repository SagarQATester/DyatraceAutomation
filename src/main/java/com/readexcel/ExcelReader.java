package com.readexcel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ExcelReader {

    private static final String ESCALATION_MATRIX_SHEET = "Escalation Matrix";
    private static final String LOGIN_CREDENTIALS_SHEET = "Login Credentials";

    public static final Map<String, Integer> escalationTimeFrames = new HashMap<>();
    public static final Map<String, String[]> escalationContacts = new HashMap<>();
    public static final Map<String, String[]> escalationMails = new HashMap<>();
    public static String emailId;
    public static String emailPassword;


    public static void loadDataFromExcel() {
    	String filePath=System.getProperty("user.dir")+"\\TestData\\EscalationMatrix.xlsx";
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

                    Cell cell = row.getCell(0);
                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        escalationTimeFrame = (int) cell.getNumericCellValue();
                    }

                    cell = row.getCell(1);
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        teamType = cell.getStringCellValue();
                    }

                    teamContact = getCellAsString(row.getCell(2));
                    emailAddress = getCellAsString(row.getCell(3));

                    if (teamType != null && escalationTimeFrame != null) {
                        // Update escalation time frames
                        escalationTimeFrames.put(teamType, escalationTimeFrame);

                        // Update escalation contacts
                        escalationContacts.computeIfAbsent(teamType, k -> new String[0]);
                        escalationContacts.put(teamType, appendToArray(escalationContacts.get(teamType), teamContact));

                        // Update escalation emails
                        escalationMails.computeIfAbsent(teamType, k -> new String[0]);
                        escalationMails.put(teamType, appendToArray(escalationMails.get(teamType), emailAddress));
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


  
 
}

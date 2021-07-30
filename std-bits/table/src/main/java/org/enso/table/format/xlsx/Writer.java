package org.enso.table.format.xlsx;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.enso.table.data.table.Table;
import org.enso.table.format.util.FileSplitter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.BiConsumer;

/** Writer for XLSX files. */
public class Writer {
  /** Specifies write behavior for files that already exist. */
  public enum WriteMode {
    /** Append new contents to the existing sheet. */
    APPEND,
    /** Remove old contents and replace with new. */
    OVERWRITE_SHEET,
    /** Create a new sheet, avoiding a name clash. */
    CREATE_SHEET
  }

  /**
   * Write a table to XLSX.
   *
   * @param table the table
   * @param path the path to the xlsx file
   * @param sheetName the name of the sheet
   * @param writeMode specification of this function's behavior when the specified sheet already
   *     exists
   * @param writeHeader whether the first row should contain column names
   * @param maxRecords the max number of records that can be written to a single file
   * @param writeCell a helper for writing arbitrary objects into XLSX cells.
   * @throws IOException when any of the files cannot be read.
   * @throws InvalidFormatException when the specified file exists, but is not an XLSX file.
   */
  public static void writeXlsx(
      Table table,
      String path,
      String sheetName,
      WriteMode writeMode,
      boolean writeHeader,
      Integer maxRecords,
      BiConsumer<Object, Cell> writeCell)
      throws IOException, InvalidFormatException {
    if (maxRecords == null || maxRecords >= table.rowCount()) {
      var file = new File(path);
      writeXlsx(table, file, sheetName, writeMode, writeHeader, 0, table.rowCount(), writeCell);
    } else {
      var splitter = new FileSplitter(table.rowCount(), maxRecords, new File(path));
      for (int i = 0; i < splitter.getNumberOfFiles(); i++) {
        writeXlsx(
            table,
            splitter.getFile(i),
            sheetName,
            writeMode,
            writeHeader,
            i * maxRecords,
            maxRecords,
            writeCell);
      }
    }
  }

  private static Workbook workbookForFile(File file) throws IOException, InvalidFormatException {
    if (file.exists()) {
      try (var stream = new FileInputStream(file)) {
        return new XSSFWorkbook(stream);
      }
    } else {
      return new XSSFWorkbook();
    }
  }

  private static void writeXlsx(
      Table table,
      File file,
      String sheetName,
      WriteMode writeMode,
      boolean writeHeader,
      int startRecord,
      int numRecords,
      BiConsumer<Object, Cell> writeCell)
      throws IOException, InvalidFormatException {
    try (var workbook = workbookForFile(file)) {
      writeWorkbook(
          table, workbook, sheetName, writeMode, writeHeader, startRecord, numRecords, writeCell);
      try (var outputStream = new FileOutputStream(file)) {
        workbook.write(outputStream);
      }
    }
  }

  private static void writeWorkbook(
      Table table,
      Workbook workbook,
      String sheetName,
      WriteMode writeMode,
      boolean writeHeader,
      int startRecord,
      int numRecords,
      BiConsumer<Object, Cell> writeCell) {
    var sheet = workbook.getSheet(sheetName);
    if (sheet == null) {
      var newSheet = workbook.createSheet(sheetName);
      writeSheet(table, newSheet, writeHeader, startRecord, numRecords, 0, 0, writeCell);
      return;
    }
    switch (writeMode) {
      case APPEND:
        writeSheet(
            table,
            sheet,
            writeHeader,
            startRecord,
            numRecords,
            sheet.getLastRowNum() + 1,
            0,
            writeCell);
        workbook.setForceFormulaRecalculation(true);
        return;
      case OVERWRITE_SHEET:
        int row;
        while ((row = sheet.getLastRowNum()) != -1) {
          sheet.removeRow(sheet.getRow(row));
        }
        writeSheet(table, sheet, writeHeader, startRecord, numRecords, 0, 0, writeCell);
        workbook.setForceFormulaRecalculation(true);
        return;
      case CREATE_SHEET:
        int currentSheet = 1;
        var newSheetName = "";
        do {
          newSheetName = sheetName + " " + currentSheet;
          sheet = workbook.getSheet(newSheetName);
          currentSheet++;
        } while (sheet != null);
        sheet = workbook.createSheet(newSheetName);
        writeSheet(table, sheet, writeHeader, startRecord, numRecords, 0, 0, writeCell);
        workbook.setForceFormulaRecalculation(true);
        return;
    }
  }

  private static void writeSheet(
      Table table,
      Sheet sheet,
      boolean writeHeader,
      int startRecord,
      int numRecords,
      int startRow,
      int startCol,
      BiConsumer<Object, Cell> writeCell) {
    var columns = Arrays.asList(table.getColumns());
    var index = table.getIndex().toColumn();
    if (index != null) {
      columns.add(0, index);
    }
    if (writeHeader) {
      var row = sheet.createRow(startRow);
      startRow++;
      for (int j = 0; j < columns.size(); j++) {
        var cell = row.createCell(startCol + j, CellType.STRING);
        cell.setCellValue(columns.get(j).getName());
      }
    }
    var rowLimit = Math.min(numRecords, table.rowCount() - startRecord);
    for (int i = 0; i < rowLimit; i++) {
      var row = sheet.createRow(startRow + i);
      for (int j = 0; j < columns.size(); j++) {
        var cell = row.createCell(startCol + j);
        var storage = columns.get(j).getStorage();
        if (storage.isNa(startRecord + i)) {
          cell.setBlank();
        } else {
          storage.writeSpreadsheetCell(startRecord + i, cell, writeCell);
        }
      }
    }
  }
}

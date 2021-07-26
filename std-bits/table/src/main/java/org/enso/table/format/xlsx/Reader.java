package org.enso.table.format.xlsx;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.enso.table.data.column.builder.object.Builder;
import org.enso.table.data.column.builder.object.InferredBuilder;
import org.enso.table.data.table.Column;
import org.enso.table.data.table.Table;
import org.enso.table.util.NameDeduplicator;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/** A table reader for MS Excel files. */
public class Reader {
  /**
   * Reads the specified XLSX file into a table.
   *
   * @param inputStream an {@link InputStream} allowing to read the XLSX file contents.
   * @param sheetIdx specifies which sheet should be read. If the value is a {@link Long}, it is
   *     used as a 0-based index of the sheet. If it is a {@link String}, it is used as a sheet
   *     name. Otherwise, the active sheet is read.
   * @param hasHeaders specifies whether the first non-empty row of the sheet should be used for
   *     column names.
   * @param unnamedColumnPrefix specifies the prefix to use for missing columns.
   * @param mkDate a function converting java-based dates into a format understandable by the
   *     caller.
   * @return a {@link Table} containing the specified data.
   * @throws IOException when the input stream cannot be read.
   */
  public static Table read_xlsx(
      InputStream inputStream,
      Object sheetIdx,
      boolean hasHeaders,
      String unnamedColumnPrefix,
      Function<LocalDate, Value> mkDate)
      throws IOException {
    return read_table(
        new XSSFWorkbook(inputStream), sheetIdx, hasHeaders, unnamedColumnPrefix, mkDate);
  }

  /**
   * Reads the specified XLS file into a table.
   *
   * @param is an {@link InputStream} allowing to read the XLS file contents.
   * @param sheetIdx specifies which sheet should be read. If the value is a {@link Long}, it is
   *     used as a 0-based index of the sheet. If it is a {@link String}, it is used as a sheet
   *     name. Otherwise, the active sheet is read.
   * @param hasHeaders specifies whether the first non-empty row of the sheet should be used for
   *     column names.
   * @param unnamedColumnPrefix specifies the prefix to use for missing columns.
   * @param mkDate a function converting java-based dates into a format understandable by the
   *     caller.
   * @return a {@link Table} containing the specified data.
   * @throws IOException when the input stream cannot be read.
   */
  public static Table read_xls(
      InputStream is,
      Object sheetIdx,
      boolean hasHeaders,
      String unnamedColumnPrefix,
      Function<LocalDate, Value> mkDate)
      throws IOException {
    return read_table(new HSSFWorkbook(is), sheetIdx, hasHeaders, unnamedColumnPrefix, mkDate);
  }

  private static Table read_table(
      Workbook workbook,
      Object sheetIdx,
      boolean hasHeaders,
      String unnamedColumnPrefix,
      Function<LocalDate, Value> mkDate)
      throws IOException {
    Sheet sheet = null;
    if (sheetIdx instanceof Long) {
      sheet = workbook.getSheetAt(((Long) sheetIdx).intValue());
    } else if (sheetIdx instanceof String) {
      sheet = workbook.getSheet((String) sheetIdx);
    }
    if (sheet == null) {
      sheet = workbook.getSheetAt(workbook.getActiveSheetIndex());
    }
    int minRow = sheet.getFirstRowNum();
    int maxRow = sheet.getLastRowNum();
    if (minRow == -1) {
      return new Table(new Column[0]);
    }
    int minCol = Integer.MAX_VALUE;
    int maxCol = -1;
    for (int i = minRow; i <= maxRow; i++) {
      var row = sheet.getRow(i);
      if (row == null) continue;
      var firstCell = row.getFirstCellNum();
      var lastCell = row.getLastCellNum();
      if (firstCell != -1 && lastCell != -1) {
        minCol = Math.min(minCol, firstCell);
        maxCol = Math.max(maxCol, lastCell);
      }
    }
    if (minCol >= maxCol) {
      return new Table(new Column[0]);
    }

    List<String> colNames = new ArrayList<>(maxCol - minCol);
    if (hasHeaders) {
      var headerRow = sheet.getRow(minRow);
      minRow++;
      for (int i = minCol; i < maxCol; i++) {
        var cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null || cell.getCellType() != CellType.STRING) {
          colNames.add(unnamedColumnPrefix + (i - minCol));
        } else {
          colNames.add(cell.getStringCellValue());
        }
      }
    } else {
      for (int i = minCol; i < maxCol; i++) {
        colNames.add(unnamedColumnPrefix + (i - minCol));
      }
    }
    var deduplicatedColumnNames = NameDeduplicator.deduplicate(colNames);

    List<Builder> builders = new ArrayList<>();
    for (int i = minCol; i < maxCol; i++) {
      builders.add(new InferredBuilder(maxRow - minRow + 1));
    }

    for (int i = minRow; i <= maxRow; i++) {
      var row = sheet.getRow(i);
      for (int j = minCol; j < maxCol; j++) {
        if (row == null) {
          builders.get(j - minCol).append(null);
          continue;
        }
        var cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
          builders.get(j - minCol).append(null);
        } else {
          builders.get(j - minCol).append(getCellValue(cell, mkDate));
        }
      }
    }
    Column[] columns =
        IntStream.range(0, maxCol - minCol)
            .mapToObj(idx -> new Column(deduplicatedColumnNames.get(idx), builders.get(idx).seal()))
            .toArray(Column[]::new);
    return new Table(columns);
  }

  private static Object getCellValue(Cell cell, Function<LocalDate, Value> mkDate) {
    return getCellValue(cell, cell.getCellType(), mkDate);
  }

  private static Object getCellValue(
      Cell cell, CellType cellType, Function<LocalDate, Value> mkDate) {
    switch (cellType) {
      case FORMULA:
        return getCellValue(cell, cell.getCachedFormulaResultType(), mkDate);
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return mkDate.apply(cell.getLocalDateTimeCellValue().toLocalDate());
        } else {
          return cell.getNumericCellValue();
        }
      case STRING:
        return cell.getStringCellValue();
      case ERROR:
        return null;
      case BOOLEAN:
        return cell.getBooleanCellValue();
      case BLANK:
        return null;
      case _NONE:
        return null;
    }
    return null;
  }
}

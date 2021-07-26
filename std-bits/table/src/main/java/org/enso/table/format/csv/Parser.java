package org.enso.table.format.csv;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.enso.table.data.column.storage.Storage;
import org.enso.table.data.column.builder.string.StorageBuilder;
import org.enso.table.data.column.builder.string.PrimInferredStorageBuilder;
import org.enso.table.data.index.DefaultIndex;
import org.enso.table.data.table.Column;
import org.enso.table.data.table.Table;
import org.enso.table.util.NameDeduplicator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** A CSV parser. */
public class Parser {
  private final boolean hasHeader;
  private final String unnamedColumnPrefix;

  private Parser(boolean hasHeader, String unnamedColumnPrefix) {
    this.hasHeader = hasHeader;
    this.unnamedColumnPrefix = unnamedColumnPrefix;
  }

  /**
   * Creates a new parser with given parameters.
   *
   * @param hasHeader whether or not the first line of the file should be used as a header line
   * @param unnamedColumnPrefix the string to prepend to column index for columns with unknown name.
   * @return a CSV parser
   */
  public static Parser create(boolean hasHeader, String unnamedColumnPrefix) {
    return new Parser(hasHeader, unnamedColumnPrefix);
  }

  /**
   * Parses the given input stream into a Table.
   *
   * @param inputStream the input stream to parse
   * @return a table corresponding to the contents of the stream
   */
  public Table parse(InputStream inputStream) {
    CsvParserSettings settings = new CsvParserSettings();
    settings.setHeaderExtractionEnabled(hasHeader);
    settings.detectFormatAutomatically();
    CsvParser parser = new CsvParser(settings);
    parser.beginParsing(inputStream);
    StorageBuilder[] builders = null;
    String[] header = parser.getContext().headers();
    // TODO[MK] Handle irregular table sizes
    if (header != null) {
      builders = initBuilders(header.length);
    }
    String[] row = parser.parseNext();
    if (row == null) {
      return new Table(new Column[0]);
    }
    if (builders == null) {
      builders = initBuilders(row.length);
    }
    for (int i = 0; i < builders.length; i++) {
      builders[i] = builders[i].parseAndAppend(handleNa(row[i]));
    }
    while ((row = parser.parseNext()) != null) {
      for (int i = 0; i < builders.length; i++) {
        builders[i] = builders[i].parseAndAppend(handleNa(row[i]));
      }
    }
    Column[] columns = new Column[builders.length];
    List<String> names = new ArrayList<>(builders.length);
    for (int i = 0; i < builders.length; i++) {
      names.add(header != null ? header[i] : unnamedColumnPrefix + i);
    }
    names = NameDeduplicator.deduplicate(names);
    for (int i = 0; i < builders.length; i++) {
      Storage col = builders[i].seal();
      columns[i] = new Column(names.get(i), new DefaultIndex(col.size()), col);
    }
    return new Table(columns);
  }

  private StorageBuilder[] initBuilders(int count) {
    StorageBuilder[] res = new StorageBuilder[count];
    for (int i = 0; i < count; i++) {
      res[i] = new PrimInferredStorageBuilder();
    }
    return res;
  }

  private String handleNa(String raw) {
    if (raw == null || raw.length() == 0) {
      return null;
    }
    return raw;
  }
}

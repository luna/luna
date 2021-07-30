package org.enso.table.format.util;

import java.io.File;

public class FileSplitter {
  private final int numberOfFiles;
  private final String basename;
  private final String extension;
  private final File dir;

  public FileSplitter(int numberOfRecords, int maxRecordsPerFile, File prototype) {
    numberOfFiles =
        numberOfRecords / maxRecordsPerFile + (numberOfRecords % maxRecordsPerFile == 0 ? 0 : 1);
    var originalName = prototype.getName();
    var extIndex = originalName.indexOf('.');
    dir = prototype.getParentFile();
    if (extIndex == -1) {
      basename = originalName;
      extension = "";
    } else {
      basename = originalName.substring(0, extIndex);
      extension = originalName.substring(extIndex);
    }
  }

  public int getNumberOfFiles() {
    return numberOfFiles;
  }

  public File getFile(int index) {
    return new File(dir, basename + "_" + (index + 1) + extension);
  }
}

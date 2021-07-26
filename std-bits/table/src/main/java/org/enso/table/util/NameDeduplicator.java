package org.enso.table.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NameDeduplicator {
  public static List<String> deduplicate(List<String> names) {
    return deduplicate(names, " ");
  }

  public static List<String> deduplicate(List<String> names, String separator) {
    Set<String> seenNames = new HashSet<>();
    return names.stream()
        .map(
            name -> {
              var result = name;
              var nextIdx = 1;
              while (seenNames.contains(result)) {
                result = name + separator + nextIdx;
                nextIdx++;
              }
              seenNames.add(result);
              return result;
            })
        .collect(Collectors.toList());
  }
}

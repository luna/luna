package org.enso.table.data.column.builder.object;

import org.enso.table.data.column.storage.Storage;

import java.math.BigDecimal;

/**
 * A builder performing type inference on the appended elements, choosing the best possible storage.
 */
public class InferredBuilder extends Builder {
  private TypedBuilder currentBuilder = null;
  private int currentSize = 0;
  private final int initialSize;

  /**
   * Creates a new instance of this builder, with the given known result size.
   *
   * @param initialSize the result size
   */
  public InferredBuilder(int initialSize) {
    this.initialSize = initialSize;
  }

  @Override
  public void appendNoGrow(Object o) {
    if (currentBuilder == null) {
      if (o == null) {
        currentSize++;
        return;
      } else {
        initBuilderFor(o);
      }
    }
    if (o == null) {
      currentBuilder.appendNoGrow(o);
    } else {
      switch (currentBuilder.getType()) {
        case Storage.Type.BOOL:
          if (o instanceof Boolean) {
            currentBuilder.appendNoGrow(o);
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.LONG:
          if (o instanceof Long) {
            currentBuilder.appendNoGrow(o);
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.DOUBLE:
          if (o instanceof Double || o instanceof BigDecimal) {
            currentBuilder.appendNoGrow(o);
          } else if (o instanceof Long) {
            currentBuilder.appendNoGrow(((Long) o).doubleValue());
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.STRING:
          if (o instanceof String) {
            currentBuilder.appendNoGrow(o);
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.OBJECT:
          currentBuilder.appendNoGrow(o);
          break;
      }
    }
    currentSize++;
  }

  @Override
  public void append(Object o) {
    if (currentBuilder == null) {
      if (o == null) {
        currentSize++;
        return;
      } else {
        initBuilderFor(o);
      }
    }
    if (o == null) {
      currentBuilder.append(o);
    } else {
      switch (currentBuilder.getType()) {
        case Storage.Type.BOOL:
          if (o instanceof Boolean) {
            currentBuilder.append(o);
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.LONG:
          if (o instanceof Long) {
            currentBuilder.append(o);
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.DOUBLE:
          if (o instanceof Double || o instanceof BigDecimal) {
            currentBuilder.append(o);
          } else if (o instanceof Long) {
            currentBuilder.append(((Long) o).doubleValue());
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.STRING:
          if (o instanceof String) {
            currentBuilder.append(o);
          } else {
            retypeAndAppend(o);
          }
          break;
        case Storage.Type.OBJECT:
          currentBuilder.append(o);
          break;
      }
    }
    currentSize++;
  }

  @Override
  public void appendNulls(int count) {
    if (currentBuilder != null) {
      currentBuilder.appendNulls(count);
    }
    currentSize += count;
  }

  private void initBuilderFor(Object o) {
    int initialCapacity = Math.max(initialSize, currentSize);
    if (o instanceof Boolean) {
      currentBuilder = new BoolBuilder();
    } else if (o instanceof Double || o instanceof BigDecimal) {
      currentBuilder = NumericBuilder.createDoubleBuilder(initialCapacity);
    } else if (o instanceof Long) {
      currentBuilder = NumericBuilder.createLongBuilder(initialCapacity);
    } else if (o instanceof String) {
      currentBuilder = new StringBuilder(initialCapacity);
    } else {
      currentBuilder = new ObjectBuilder(initialCapacity);
    }
    currentBuilder.appendNulls(currentSize);
  }

  private void retypeAndAppend(Object o) {
    if ((o instanceof Double || o instanceof BigDecimal)
        && currentBuilder.canRetypeTo(Storage.Type.DOUBLE)) {
      currentBuilder = currentBuilder.retypeTo(Storage.Type.DOUBLE);
    } else if (o instanceof String && currentBuilder.canRetypeTo(Storage.Type.STRING)) {
      currentBuilder = currentBuilder.retypeTo(Storage.Type.STRING);
    } else if (o instanceof Long && currentBuilder.canRetypeTo(Storage.Type.LONG)) {
      currentBuilder = currentBuilder.retypeTo(Storage.Type.LONG);
    } else if (o instanceof Boolean && currentBuilder.canRetypeTo(Storage.Type.BOOL)) {
      currentBuilder = currentBuilder.retypeTo(Storage.Type.BOOL);
    } else if (currentBuilder.canRetypeTo(Storage.Type.OBJECT)) {
      currentBuilder = currentBuilder.retypeTo(Storage.Type.OBJECT);
    } else {
      retypeToObject();
    }
    currentBuilder.append(o);
  }

  private void retypeToObject() {
    ObjectBuilder objectBuilder = new ObjectBuilder(initialSize);
    currentBuilder.writeTo(objectBuilder.getData());
    objectBuilder.setCurrentSize(currentBuilder.getCurrentSize());
    currentBuilder = objectBuilder;
  }

  @Override
  public int getCurrentSize() {
    return currentSize;
  }

  @Override
  public int getCurrentCapacity() {
    return 0;
  }

  @Override
  public Storage seal() {
    if (currentBuilder == null) {
      initBuilderFor(null);
    }
    return currentBuilder.seal();
  }
}

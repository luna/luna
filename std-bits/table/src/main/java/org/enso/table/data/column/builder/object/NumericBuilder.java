package org.enso.table.data.column.builder.object;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.BitSet;
import org.enso.table.data.column.storage.DoubleStorage;
import org.enso.table.data.column.storage.LongStorage;
import org.enso.table.data.column.storage.Storage;

/** A builder for numeric columns. */
public class NumericBuilder extends TypedBuilder {
  private final BitSet isMissing = new BitSet();
  private long[] data;
  private boolean isDouble;
  private int currentSize;

  private NumericBuilder(boolean isDouble, int size) {
    this.data = new long[size];
    this.isDouble = isDouble;
  }

  public static NumericBuilder createDoubleBuilder(int size) {
    return new NumericBuilder(true, size);
  }

  public static NumericBuilder createLongBuilder(int size) {
    return new NumericBuilder(false, size);
  }

  @Override
  public void writeTo(Object[] items) {
    for (int i = 0; i < currentSize; i++) {
      if (isMissing.get(i)) {
        items[i] = null;
      } else if (isDouble) {
        items[i] = Double.longBitsToDouble(data[i]);
      } else {
        items[i] = data[i];
      }
    }
  }

  @Override
  public boolean canRetypeTo(long type) {
    return !this.isDouble && type == Storage.Type.DOUBLE;
  }

  @Override
  public TypedBuilder retypeTo(long type) {
    if (!this.isDouble && type == Storage.Type.DOUBLE) {
      this.isDouble = true;
      for (int i = 0; i < currentSize; i++) {
        data[i] = Double.doubleToRawLongBits(data[i]);
      }
      return this;
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public int getType() {
    return isDouble ? Storage.Type.DOUBLE : Storage.Type.LONG;
  }

  @Override
  public void appendNoGrow(Object o) {
    if (o == null) {
      isMissing.set(currentSize++);
    } else if (isDouble && o instanceof Double) {
      data[currentSize++] = Double.doubleToRawLongBits((Double) o);
    } else if (!isDouble && o instanceof Long) {
      data[currentSize++] = (Long) o;
    } else if (isDouble && o instanceof BigDecimal) {
      data[currentSize++] = Double.doubleToRawLongBits(((BigDecimal) o).doubleValue());
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public void append(Object o) {
    if (currentSize + 1 > data.length) {
      grow();
    }
    appendNoGrow(o);
  }

  @Override
  public void appendNulls(int count) {
    isMissing.set(currentSize, currentSize + count);
    currentSize += count;
  }

  /**
   * Append a new item in raw form to this builder, assuming that it has enough allocated space.
   *
   * <p>This function should only be used when it is guaranteed that the builder has enough
   * capacity, for example if it was initialized with an initial capacity known up-front.
   *
   * @param rawData the raw encoding of the item, for long numbers just the number and for doubles,
   *     its long bytes
   */
  public void appendRawNoGrow(long rawData) {
    data[currentSize++] = rawData;
  }

  /**
   * Append a new integer to this builder.
   *
   * @param data the integer to append
   */
  public void appendLong(long data) {
    if (currentSize + 1 > this.data.length) {
      grow();
    }
    appendRawNoGrow(data);
  }

  /**
   * Append a new double to this builder.
   *
   * @param data the double to append
   */
  public void appendDouble(double data) {
    if (currentSize + 1 > this.data.length) {
      grow();
    }
    appendRawNoGrow(Double.doubleToRawLongBits(data));
  }

  @Override
  public int getCurrentSize() {
    return currentSize;
  }

  @Override
  public int getCurrentCapacity() {
    return data.length;
  }

  @Override
  public Storage seal() {
    if (isDouble) {
      return new DoubleStorage(data, currentSize, isMissing);
    } else {
      return new LongStorage(data, currentSize, isMissing);
    }
  }

  private void grow() {
    int desiredCapacity = 3;
    if (data.length > 1) {
      desiredCapacity = (data.length * 3 / 2);
    }
    this.data = Arrays.copyOf(data, desiredCapacity);
  }
}

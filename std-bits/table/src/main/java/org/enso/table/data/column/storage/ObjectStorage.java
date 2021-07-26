package org.enso.table.data.column.storage;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.function.Function;

import org.enso.table.data.column.operation.map.MapOpStorage;
import org.enso.table.data.column.operation.map.UnaryMapOperation;
import org.enso.table.data.index.Index;
import org.enso.table.data.mask.OrderMask;

/** A column storing arbitrary objects. */
public class ObjectStorage extends Storage {
  private final Object[] data;
  private final int size;
  protected static final MapOpStorage<ObjectStorage> ops = buildOps();

  /**
   * @param data the underlying data
   * @param size the number of items stored
   */
  public ObjectStorage(Object[] data, int size) {
    this.data = data;
    this.size = size;
  }

  /** @inheritDoc */
  @Override
  public int size() {
    return size;
  }

  /** @inheritDoc */
  @Override
  public int countMissing() {
    int count = 0;
    for (int i = 0; i < size; i++) {
      if (data[i] == null) {
        count += 1;
      }
    }
    return count;
  }

  /**
   * @param idx an index
   * @return the data item contained at the given index.
   */
  public Object getItem(long idx) {
    return data[(int) idx];
  }

  @Override
  public Object getItemBoxed(int idx) {
    return data[idx];
  }

  /** @inheritDoc */
  @Override
  public long getType() {
    return Type.OBJECT;
  }

  /** @inheritDoc */
  @Override
  public boolean isNa(long idx) {
    return data[(int) idx] == null;
  }

  @Override
  protected boolean isOpVectorized(String name) {
    return ops.isSupported(name);
  }

  @Override
  protected Storage runVectorizedMap(String name, Object argument) {
    return ops.runMap(name, this, argument);
  }

  @Override
  protected Storage runVectorizedZip(String name, Storage argument) {
    return ops.runZip(name, this, argument);
  }

  @Override
  public ObjectStorage mask(BitSet mask, int cardinality) {
    Object[] newData = new Object[cardinality];
    int resIx = 0;
    for (int i = 0; i < size; i++) {
      if (mask.get(i)) {
        newData[resIx++] = data[i];
      }
    }
    return new ObjectStorage(newData, cardinality);
  }

  @Override
  public ObjectStorage applyMask(OrderMask mask) {
    int[] positions = mask.getPositions();
    Object[] newData = new Object[positions.length];
    for (int i = 0; i < positions.length; i++) {
      if (positions[i] == Index.NOT_FOUND) {
        newData[i] = null;
      } else {
        newData[i] = data[positions[i]];
      }
    }
    return new ObjectStorage(newData, positions.length);
  }

  @Override
  public ObjectStorage countMask(int[] counts, int total) {
    Object[] newData = new Object[total];
    int pos = 0;
    for (int i = 0; i < counts.length; i++) {
      for (int j = 0; j < counts[i]; j++) {
        newData[pos++] = data[i];
      }
    }
    return new ObjectStorage(newData, total);
  }

  public Object[] getData() {
    return data;
  }

  @Override
  public Comparator<Object> getDefaultComparator() {
    return null;
  }

  private static MapOpStorage<ObjectStorage> buildOps() {
    MapOpStorage<ObjectStorage> ops = new MapOpStorage<>();
    ops.add(
        new UnaryMapOperation<>(Maps.IS_MISSING) {
          @Override
          protected Storage run(ObjectStorage storage) {
            BitSet r = new BitSet();
            for (int i = 0; i < storage.size; i++) {
              if (storage.data[i] == null) {
                r.set(i);
              }
            }
            return new BoolStorage(r, new BitSet(), storage.size, false);
          }
        });
    return ops;
  }

  @Override
  public ObjectStorage slice(int offset, int limit) {
    int newSize = Math.min(size - offset, limit);
    Object[] newData = new Object[newSize];
    System.arraycopy(data, offset, newData, 0, newSize);
    return new ObjectStorage(newData, newSize);
  }

  @Override
  protected String getPresentCsvString(int index, Function<Object, String> toCsvString) {
    return toCsvString.apply(getItem(index));
  }
}

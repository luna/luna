package org.enso.table.data.column.storage;

import java.util.BitSet;
import java.util.Comparator;
import java.util.function.Function;

import org.enso.table.data.column.builder.object.StringBuilder;
import org.enso.table.data.column.operation.map.MapOpStorage;
import org.enso.table.data.column.operation.map.MapOperation;
import org.enso.table.data.column.operation.map.text.StringBooleanOp;
import org.enso.table.data.mask.OrderMask;

/** A column storing strings. */
public class StringStorage extends ObjectStorage {

  private static final MapOpStorage<StringStorage> ops = buildOps();

  /**
   * @param data the underlying data
   * @param size the number of items stored
   */
  public StringStorage(Object[] data, int size) {
    super(data, size);
  }

  /**
   * @param idx an index
   * @return the data item contained at the given index.
   */
  public String getItem(long idx) {
    return (String) super.getItem(idx);
  }

  /** @inheritDoc */
  @Override
  public long getType() {
    return Type.STRING;
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
  public Storage fillMissing(Object arg) {
    if (arg instanceof String) {
      return fillMissingHelper(arg, new StringBuilder(size()));
    } else {
      return super.fillMissing(arg);
    }
  }

  @Override
  public StringStorage mask(BitSet mask, int cardinality) {
    ObjectStorage storage = super.mask(mask, cardinality);
    return new StringStorage(storage.getData(), cardinality);
  }

  @Override
  public StringStorage applyMask(OrderMask mask) {
    ObjectStorage storage = super.applyMask(mask);
    return new StringStorage(storage.getData(), (int) storage.size());
  }

  @Override
  public StringStorage countMask(int[] counts, int total) {
    ObjectStorage storage = super.countMask(counts, total);
    return new StringStorage(storage.getData(), total);
  }

  @Override
  public Comparator getDefaultComparator() {
    return Comparator.<String>naturalOrder();
  }

  private static MapOpStorage<StringStorage> buildOps() {
    MapOpStorage<StringStorage> t = ObjectStorage.ops.makeChild();
    t.add(
        new MapOperation<>(Maps.EQ) {
          @Override
          public Storage runMap(StringStorage storage, Object arg) {
            BitSet r = new BitSet();
            BitSet missing = new BitSet();
            for (int i = 0; i < storage.size(); i++) {
              if (storage.getItem(i) == null) {
                missing.set(i);
              } else if (storage.getItem(i).equals(arg)) {
                r.set(i);
              }
            }
            return new BoolStorage(r, missing, storage.size(), false);
          }

          @Override
          public Storage runZip(StringStorage storage, Storage arg) {
            BitSet r = new BitSet();
            BitSet missing = new BitSet();
            for (int i = 0; i < storage.size(); i++) {
              if (storage.getItem(i) == null || i >= arg.size() || arg.isNa(i)) {
                missing.set(i);
              } else if (storage.getItem(i).equals(arg.getItemBoxed(i))) {
                r.set(i);
              }
            }
            return new BoolStorage(r, missing, storage.size(), false);
          }
        });
    t.add(
        new StringBooleanOp(Maps.STARTS_WITH) {
          @Override
          protected boolean doString(String a, String b) {
            return a.startsWith(b);
          }
        });
    t.add(
        new StringBooleanOp(Maps.ENDS_WITH) {
          @Override
          protected boolean doString(String a, String b) {
            return a.endsWith(b);
          }
        });
    t.add(
        new StringBooleanOp(Maps.CONTAINS) {
          @Override
          protected boolean doString(String a, String b) {
            return a.contains(b);
          }
        });
    return t;
  }

  @Override
  public StringStorage slice(int offset, int limit) {
    ObjectStorage storage = super.slice(offset, limit);
    return new StringStorage(storage.getData(), storage.size());
  }

  @Override
  protected String getPresentCsvString(int index, Function<Object, String> toCsvString) {
    return getItem(index);
  }
}

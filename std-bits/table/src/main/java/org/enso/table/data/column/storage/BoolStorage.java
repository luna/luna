package org.enso.table.data.column.storage;

import java.util.BitSet;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.enso.table.data.column.operation.map.MapOpStorage;
import org.enso.table.data.column.operation.map.MapOperation;
import org.enso.table.data.column.operation.map.UnaryMapOperation;
import org.enso.table.data.index.Index;
import org.enso.table.data.mask.OrderMask;
import org.enso.table.error.UnexpectedColumnTypeException;
import org.enso.table.error.UnexpectedTypeException;

/** A boolean column storage. */
public class BoolStorage extends Storage {
  private static final MapOpStorage<BoolStorage> ops = buildOps();
  private final BitSet values;
  private final BitSet isMissing;
  private final int size;
  private final boolean negated;

  public BoolStorage(BitSet values, BitSet isMissing, int size, boolean negated) {
    this.values = values;
    this.isMissing = isMissing;
    this.size = size;
    this.negated = negated;
  }

  @Override
  public int size() {
    return size;
  }

  /** @inheritDoc */
  @Override
  public int countMissing() {
    return isMissing.cardinality();
  }

  @Override
  public long getType() {
    return Type.BOOL;
  }

  @Override
  public Object getItemBoxed(int idx) {
    return isMissing.get(idx) ? null : getItem(idx);
  }

  public boolean getItem(long idx) {
    return negated != values.get((int) idx);
  }

  @Override
  public boolean isNa(long idx) {
    return isMissing.get((int) idx);
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

  public BitSet getValues() {
    return values;
  }

  public BitSet getIsMissing() {
    return isMissing;
  }

  /**
   * Creates a new BoolStorage in which all missing values have been replaced by arg.
   *
   * <p>It works by setting the new isMissing to an empty bitset and changing the values bitset
   * accordingly. If `arg` is true, new values are `values || isMissing` and if `arg` is false, new
   * values are `values && (~isMissing)`.
   */
  private BoolStorage fillMissingBoolean(boolean arg) {
    final var newValues = (BitSet) values.clone();
    if (arg) {
      newValues.or(isMissing);
    } else {
      newValues.andNot(isMissing);
    }
    return new BoolStorage(newValues, new BitSet(), size, negated);
  }

  @Override
  public Storage fillMissing(Object arg) {
    if (arg instanceof Boolean) {
      return fillMissingBoolean((Boolean) arg);
    } else {
      return super.fillMissing(arg);
    }
  }

  @Override
  public Storage mask(BitSet mask, int cardinality) {
    BitSet newMissing = new BitSet();
    BitSet newValues = new BitSet();
    int resultIx = 0;
    for (int i = 0; i < size; i++) {
      if (mask.get(i)) {
        if (isMissing.get(i)) {
          newMissing.set(resultIx++);
        } else if (values.get(i)) {
          newValues.set(resultIx++);
        }
      }
    }
    return new BoolStorage(newValues, newMissing, cardinality, negated);
  }

  @Override
  public Storage applyMask(OrderMask mask) {
    int[] positions = mask.getPositions();
    BitSet newNa = new BitSet();
    BitSet newVals = new BitSet();
    for (int i = 0; i < positions.length; i++) {
      if (positions[i] == Index.NOT_FOUND || isMissing.get(positions[i])) {
        newNa.set(i);
      } else if (values.get(positions[i])) {
        newVals.set(i);
      }
    }
    return new BoolStorage(newVals, newNa, positions.length, negated);
  }

  @Override
  public Storage countMask(int[] counts, int total) {
    BitSet newNa = new BitSet();
    BitSet newVals = new BitSet();
    int pos = 0;
    for (int i = 0; i < counts.length; i++) {
      if (isMissing.get(i)) {
        newNa.set(pos, pos + counts[i]);
      } else if (values.get(i)) {
        newVals.set(pos, pos + counts[i]);
      }
      pos += counts[i];
    }
    return new BoolStorage(newVals, newNa, total, negated);
  }

  public boolean isNegated() {
    return negated;
  }

  private static MapOpStorage<BoolStorage> buildOps() {
    MapOpStorage<BoolStorage> ops = new MapOpStorage<>();
    ops.add(
            new UnaryMapOperation<>(Maps.NOT) {
              @Override
              protected Storage run(BoolStorage storage) {
                return new BoolStorage(
                    storage.values, storage.isMissing, storage.size, !storage.negated);
              }
            })
        .add(
            new MapOperation<>(Maps.EQ) {
              @Override
              public Storage runMap(BoolStorage storage, Object arg) {
                if (arg instanceof Boolean) {
                  if ((Boolean) arg) {
                    return storage;
                  } else {
                    return new BoolStorage(
                        storage.values, storage.isMissing, storage.size, !storage.negated);
                  }
                } else {
                  return new BoolStorage(new BitSet(), storage.isMissing, storage.size, false);
                }
              }

              @Override
              public Storage runZip(BoolStorage storage, Storage arg) {
                BitSet out = new BitSet();
                BitSet missing = new BitSet();
                for (int i = 0; i < storage.size; i++) {
                  if (!storage.isNa(i) && i < arg.size() && !arg.isNa(i)) {
                    if (((Boolean) storage.getItem(i)).equals(arg.getItemBoxed(i))) {
                      out.set(i);
                    }
                  } else {
                    missing.set(i);
                  }
                }
                return new BoolStorage(out, missing, storage.size, false);
              }
            })
        .add(
            new MapOperation<>(Maps.AND) {
              @Override
              public Storage runMap(BoolStorage storage, Object arg) {
                if (arg instanceof Boolean) {
                  boolean v = (Boolean) arg;
                  if (v) {
                    return storage;
                  } else {
                    return new BoolStorage(new BitSet(), storage.isMissing, storage.size, false);
                  }
                } else {
                  throw new UnexpectedTypeException("a Boolean");
                }
              }

              @Override
              public Storage runZip(BoolStorage storage, Storage arg) {
                if (arg instanceof BoolStorage) {
                  BoolStorage v = (BoolStorage) arg;
                  BitSet missing = v.isMissing.get(0, storage.size);
                  missing.or(storage.isMissing);
                  BitSet out = v.values.get(0, storage.size);
                  boolean negated;
                  if (storage.negated && v.negated) {
                    out.or(storage.values);
                    negated = true;
                  } else if (storage.negated) {
                    out.andNot(storage.values);
                    negated = false;
                  } else if (v.negated) {
                    out.flip(0, storage.size);
                    out.and(storage.values);
                    negated = false;
                  } else {
                    out.and(storage.values);
                    negated = false;
                  }
                  return new BoolStorage(out, missing, storage.size, negated);
                } else {
                  throw new UnexpectedColumnTypeException("Boolean");
                }
              }
            })
        .add(
            new MapOperation<>(Maps.OR) {
              @Override
              public Storage runMap(BoolStorage storage, Object arg) {
                if (arg instanceof Boolean) {
                  boolean v = (Boolean) arg;
                  if (v) {
                    return new BoolStorage(new BitSet(), storage.isMissing, storage.size, true);
                  } else {
                    return storage;
                  }
                } else {
                  throw new UnexpectedTypeException("a Boolean");
                }
              }

              @Override
              public Storage runZip(BoolStorage storage, Storage arg) {
                if (arg instanceof BoolStorage) {
                  BoolStorage v = (BoolStorage) arg;
                  BitSet missing = v.isMissing.get(0, storage.size);
                  missing.or(storage.isMissing);
                  BitSet out = v.values.get(0, storage.size);
                  boolean negated;
                  if (storage.negated && v.negated) {
                    out.and(storage.values);
                    negated = true;
                  } else if (storage.negated) {
                    out.flip(0, storage.size);
                    out.and(storage.values);
                    negated = true;
                  } else if (v.negated) {
                    out.flip(0, storage.size);
                    out.or(storage.values);
                    negated = false;
                  } else {
                    out.or(storage.values);
                    negated = false;
                  }
                  return new BoolStorage(out, missing, storage.size, negated);
                } else {
                  throw new UnexpectedColumnTypeException("Boolean");
                }
              }
            });
    return ops;
  }

  /** Creates a mask that selects elements corresponding to true entries in the passed storage. */
  public static BitSet toMask(BoolStorage storage) {
    BitSet mask = new BitSet();
    mask.or(storage.getValues());
    if (storage.isNegated()) {
      mask.flip(0, storage.size());
    }
    mask.andNot(storage.getIsMissing());
    return mask;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Comparator getDefaultComparator() {
    return Comparator.naturalOrder();
  }

  @Override
  public BoolStorage slice(int offset, int limit) {
    int newSize = Math.min(size - offset, limit);
    return new BoolStorage(
        values.get(offset, offset + limit),
        isMissing.get(offset, offset + limit),
        newSize,
        negated);
  }

  @Override
  public String getPresentCsvString(int index, Function<Object, String> toCsvString) {
    return getItem(index) ? "True" : "False";
  }

  @Override
  public void writeSpreadsheetCell(int index, Cell cell, BiConsumer<Object, Cell> writeCell) {
    cell.setCellValue(getItem(index));
  }
}

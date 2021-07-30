package org.enso.table.data.column.storage;

import java.util.BitSet;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.enso.table.data.column.builder.object.NumericBuilder;
import org.enso.table.data.column.operation.map.MapOpStorage;
import org.enso.table.data.column.operation.map.UnaryMapOperation;
import org.enso.table.data.column.operation.map.numeric.DoubleBooleanOp;
import org.enso.table.data.column.operation.map.numeric.DoubleNumericOp;
import org.enso.table.data.index.Index;
import org.enso.table.data.mask.OrderMask;

/** A column containing floating point numbers. */
public class DoubleStorage extends NumericStorage {
  private final long[] data;
  private final BitSet isMissing;
  private final int size;
  private static final MapOpStorage<DoubleStorage> ops = buildOps();

  /**
   * @param data the underlying data
   * @param size the number of items stored
   * @param isMissing a bit set denoting at index {@code i} whether or not the value at index {@code
   *     i} is missing.
   */
  public DoubleStorage(long[] data, int size, BitSet isMissing) {
    this.data = data;
    this.isMissing = isMissing;
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
    return isMissing.cardinality();
  }

  /**
   * @param idx an index
   * @return the data item contained at the given index.
   */
  public double getItem(long idx) {
    return Double.longBitsToDouble(data[(int) idx]);
  }

  @Override
  public double getItemDouble(int idx) {
    return getItem(idx);
  }

  @Override
  public Object getItemBoxed(int idx) {
    return isMissing.get(idx) ? null : Double.longBitsToDouble(data[idx]);
  }

  /** @inheritDoc */
  @Override
  public long getType() {
    return Type.DOUBLE;
  }

  /** @inheritDoc */
  @Override
  public boolean isNa(long idx) {
    return isMissing.get((int) idx);
  }

  @Override
  public boolean isOpVectorized(String op) {
    return ops.isSupported(op);
  }

  @Override
  protected Storage runVectorizedMap(String name, Object argument) {
    return ops.runMap(name, this, argument);
  }

  @Override
  protected Storage runVectorizedZip(String name, Storage argument) {
    return ops.runZip(name, this, argument);
  }

  private Storage fillMissingDouble(double arg) {
    final var builder = NumericBuilder.createDoubleBuilder(size());
    long rawArg = Double.doubleToRawLongBits(arg);
    for (int i = 0; i < size(); i++) {
      if (isMissing.get(i)) {
        builder.appendRawNoGrow(rawArg);
      } else {
        builder.appendRawNoGrow(data[i]);
      }
    }
    return builder.seal();
  }

  @Override
  public Storage fillMissing(Object arg) {
    if (arg instanceof Double) {
      return fillMissingDouble((Double) arg);
    } else if (arg instanceof Long) {
      return fillMissingDouble((Long) arg);
    } else {
      return super.fillMissing(arg);
    }
  }

  @Override
  public DoubleStorage mask(BitSet mask, int cardinality) {
    BitSet newMissing = new BitSet();
    long[] newData = new long[cardinality];
    int resIx = 0;
    for (int i = 0; i < size; i++) {
      if (mask.get(i)) {
        if (isMissing.get(i)) {
          newMissing.set(resIx++);
        } else {
          newData[resIx++] = data[i];
        }
      }
    }
    return new DoubleStorage(newData, cardinality, newMissing);
  }

  @Override
  public Storage applyMask(OrderMask mask) {
    int[] positions = mask.getPositions();
    long[] newData = new long[positions.length];
    BitSet newMissing = new BitSet();
    for (int i = 0; i < positions.length; i++) {
      if (positions[i] == Index.NOT_FOUND || isMissing.get(positions[i])) {
        newMissing.set(i);
      } else {
        newData[i] = data[positions[i]];
      }
    }
    return new DoubleStorage(newData, positions.length, newMissing);
  }

  @Override
  public Storage countMask(int[] counts, int total) {
    long[] newData = new long[total];
    BitSet newMissing = new BitSet();
    int pos = 0;
    for (int i = 0; i < counts.length; i++) {
      if (isMissing.get(i)) {
        newMissing.set(pos, pos + counts[i]);
        pos += counts[i];
      } else {
        for (int j = 0; j < counts[i]; j++) {
          newData[pos++] = data[i];
        }
      }
    }
    return new DoubleStorage(newData, total, newMissing);
  }

  @Override
  public Comparator getDefaultComparator() {
    return Comparator.<Double>naturalOrder();
  }

  public BitSet getIsMissing() {
    return isMissing;
  }

  private static MapOpStorage<DoubleStorage> buildOps() {
    MapOpStorage<DoubleStorage> ops = new MapOpStorage<>();
    ops.add(
            new DoubleNumericOp(Maps.ADD) {
              @Override
              protected double doDouble(double a, double b) {
                return a + b;
              }
            })
        .add(
            new DoubleNumericOp(Maps.SUB) {
              @Override
              protected double doDouble(double a, double b) {
                return a - b;
              }
            })
        .add(
            new DoubleNumericOp(Maps.MUL) {
              @Override
              protected double doDouble(double a, double b) {
                return a * b;
              }
            })
        .add(
            new DoubleNumericOp(Maps.DIV) {
              @Override
              protected double doDouble(double a, double b) {
                return a / b;
              }
            })
        .add(
            new DoubleNumericOp(Maps.MOD) {
              @Override
              protected double doDouble(double a, double b) {
                return a % b;
              }
            })
        .add(
            new DoubleBooleanOp(Maps.LT) {
              @Override
              protected boolean doDouble(double a, double b) {
                return a < b;
              }
            })
        .add(
            new DoubleBooleanOp(Maps.LTE) {
              @Override
              protected boolean doDouble(double a, double b) {
                return a <= b;
              }
            })
        .add(
            new DoubleBooleanOp(Maps.EQ) {
              @Override
              protected boolean doDouble(double a, double b) {
                return a == b;
              }

              @Override
              protected boolean doObject(double a, Object o) {
                return false;
              }
            })
        .add(
            new DoubleBooleanOp(Maps.GT) {
              @Override
              protected boolean doDouble(double a, double b) {
                return a > b;
              }
            })
        .add(
            new DoubleBooleanOp(Maps.GTE) {
              @Override
              protected boolean doDouble(double a, double b) {
                return a >= b;
              }
            })
        .add(
            new UnaryMapOperation<>(Maps.IS_MISSING) {
              @Override
              public Storage run(DoubleStorage storage) {
                return new BoolStorage(storage.isMissing, new BitSet(), storage.size, false);
              }
            });
    return ops;
  }

  @Override
  public DoubleStorage slice(int offset, int limit) {
    int newSize = Math.min(size - offset, limit);
    long[] newData = new long[newSize];
    System.arraycopy(data, offset, newData, 0, newSize);
    BitSet newMask = isMissing.get(offset, offset + limit);
    return new DoubleStorage(newData, newSize, newMask);
  }

  @Override
  public String getPresentCsvString(int index, Function<Object, String> toCsvString) {
    return String.valueOf(getItem(index));
  }

  @Override
  public void writeSpreadsheetCell(int index, Cell cell, BiConsumer<Object, Cell> writeCell) {
    cell.setCellValue(getItem(index));
  }
}

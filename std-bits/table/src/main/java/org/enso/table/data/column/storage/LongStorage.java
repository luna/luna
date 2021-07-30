package org.enso.table.data.column.storage;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.LongStream;

import org.apache.poi.ss.usermodel.Cell;
import org.enso.table.data.column.builder.object.NumericBuilder;
import org.enso.table.data.column.operation.aggregate.Aggregator;
import org.enso.table.data.column.operation.aggregate.numeric.LongToLongAggregator;
import org.enso.table.data.column.operation.map.MapOpStorage;
import org.enso.table.data.column.operation.map.UnaryMapOperation;
import org.enso.table.data.column.operation.map.numeric.LongBooleanOp;
import org.enso.table.data.column.operation.map.numeric.LongNumericOp;
import org.enso.table.data.index.Index;
import org.enso.table.data.mask.OrderMask;

/** A column storing 64-bit integers. */
public class LongStorage extends NumericStorage {
  private final long[] data;
  private final BitSet isMissing;
  private final int size;
  private static final MapOpStorage<LongStorage> ops = buildOps();

  /**
   * @param data the underlying data
   * @param size the number of items stored
   * @param isMissing a bit set denoting at index {@code i} whether or not the value at index {@code
   *     i} is missing.
   */
  public LongStorage(long[] data, int size, BitSet isMissing) {
    this.data = data;
    this.isMissing = isMissing;
    this.size = size;
  }

  public LongStorage(long[] data) {
    this(data, data.length, new BitSet());
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
  public long getItem(int idx) {
    return data[idx];
  }

  @Override
  public double getItemDouble(int idx) {
    return getItem(idx);
  }

  @Override
  public Object getItemBoxed(int idx) {
    return isMissing.get(idx) ? null : data[idx];
  }

  /** @inheritDoc */
  @Override
  public long getType() {
    return Type.LONG;
  }

  /** @inheritDoc */
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

  @Override
  protected Aggregator getVectorizedAggregator(String name, int resultSize) {
    switch (name) {
      case Aggregators.SUM:
        return new LongToLongAggregator(this, resultSize) {
          @Override
          protected void runGroup(LongStream items) {
            long[] elements = items.toArray();
            if (elements.length == 0) {
              submitMissing();
            } else {
              submit(LongStream.of(elements).sum());
            }
          }
        };
      case Aggregators.MAX:
        return new LongToLongAggregator(this, resultSize) {
          @Override
          protected void runGroup(LongStream items) {
            OptionalLong r = items.max();
            if (r.isPresent()) {
              submit(r.getAsLong());
            } else {
              submitMissing();
            }
          }
        };
      case Aggregators.MIN:
        return new LongToLongAggregator(this, resultSize) {
          @Override
          protected void runGroup(LongStream items) {
            OptionalLong r = items.min();
            if (r.isPresent()) {
              submit(r.getAsLong());
            } else {
              submitMissing();
            }
          }
        };
      default:
        return super.getVectorizedAggregator(name, resultSize);
    }
  }

  private Storage fillMissingDouble(double arg) {
    final var builder = NumericBuilder.createDoubleBuilder(size());
    long rawArg = Double.doubleToRawLongBits(arg);
    for (int i = 0; i < size(); i++) {
      if (isMissing.get(i)) {
        builder.appendRawNoGrow(rawArg);
      } else {
        double coerced = data[i];
        builder.appendRawNoGrow(Double.doubleToRawLongBits(coerced));
      }
    }
    return builder.seal();
  }

  private Storage fillMissingLong(long arg) {
    final var builder = NumericBuilder.createLongBuilder(size());
    for (int i = 0; i < size(); i++) {
      if (isMissing.get(i)) {
        builder.appendRawNoGrow(arg);
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
      return fillMissingLong((Long) arg);
    } else {
      return super.fillMissing(arg);
    }
  }

  @Override
  public LongStorage mask(BitSet mask, int cardinality) {
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
    return new LongStorage(newData, cardinality, newMissing);
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
    return new LongStorage(newData, positions.length, newMissing);
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
    return new LongStorage(newData, total, newMissing);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Comparator getDefaultComparator() {
    return Comparator.<Long>naturalOrder();
  }

  public BitSet getIsMissing() {
    return isMissing;
  }

  private static MapOpStorage<LongStorage> buildOps() {
    MapOpStorage<LongStorage> ops = new MapOpStorage<>();
    ops.add(
            new LongNumericOp(Maps.ADD) {
              @Override
              public double doDouble(long in, double arg) {
                return in + arg;
              }

              @Override
              public long doLong(long in, long arg) {
                return in + arg;
              }
            })
        .add(
            new LongNumericOp(Maps.SUB) {
              @Override
              public double doDouble(long in, double arg) {
                return in - arg;
              }

              @Override
              public long doLong(long in, long arg) {
                return in - arg;
              }
            })
        .add(
            new LongNumericOp(Maps.MUL) {
              @Override
              public double doDouble(long in, double arg) {
                return in * arg;
              }

              @Override
              public long doLong(long in, long arg) {
                return in * arg;
              }
            })
        .add(
            new LongNumericOp(Maps.MOD) {
              @Override
              public double doDouble(long in, double arg) {
                return in % arg;
              }

              @Override
              public long doLong(long in, long arg) {
                return in % arg;
              }
            })
        .add(
            new LongNumericOp(Maps.DIV, true) {
              @Override
              public double doDouble(long in, double arg) {
                return in / arg;
              }

              @Override
              public long doLong(long in, long arg) {
                return in / arg;
              }
            })
        .add(
            new LongBooleanOp(Maps.GT) {
              @Override
              protected boolean doLong(long a, long b) {
                return a > b;
              }

              @Override
              protected boolean doDouble(long a, double b) {
                return a > b;
              }
            })
        .add(
            new LongBooleanOp(Maps.GTE) {
              @Override
              protected boolean doLong(long a, long b) {
                return a >= b;
              }

              @Override
              protected boolean doDouble(long a, double b) {
                return a >= b;
              }
            })
        .add(
            new LongBooleanOp(Maps.LT) {
              @Override
              protected boolean doLong(long a, long b) {
                return a < b;
              }

              @Override
              protected boolean doDouble(long a, double b) {
                return a > b;
              }
            })
        .add(
            new LongBooleanOp(Maps.LTE) {
              @Override
              protected boolean doLong(long a, long b) {
                return a <= b;
              }

              @Override
              protected boolean doDouble(long a, double b) {
                return a <= b;
              }
            })
        .add(
            new LongBooleanOp(Maps.EQ) {
              @Override
              protected boolean doLong(long a, long b) {
                return a == b;
              }

              @Override
              protected boolean doDouble(long a, double b) {
                return a == b;
              }

              @Override
              protected boolean doObject(long x, Object o) {
                return false;
              }
            })
        .add(
            new UnaryMapOperation<>(Maps.IS_MISSING) {
              @Override
              public Storage run(LongStorage storage) {
                return new BoolStorage(storage.isMissing, new BitSet(), storage.size, false);
              }
            });
    return ops;
  }

  @Override
  public LongStorage slice(int offset, int limit) {
    int newSize = Math.min(size - offset, limit);
    long[] newData = new long[newSize];
    System.arraycopy(data, offset, newData, 0, newSize);
    BitSet newMask = isMissing.get(offset, offset + limit);
    return new LongStorage(newData, newSize, newMask);
  }

  @Override
  protected String getPresentCsvString(int index, Function<Object, String> toCsvString) {
    return String.valueOf(getItem(index));
  }

  @Override
  public void writeSpreadsheetCell(int index, Cell cell, BiConsumer<Object, Cell> writeCell) {
    cell.setCellValue(getItem(index));
  }
}

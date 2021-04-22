package org.enso.interpreter.instrument;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The synchronization state of runtime updates.
 *
 * <p>The thread executing the program can be interrupted at any moment. For example, the interrupt
 * may happen when expression is computed and the runtime state is updated but before the update is
 * prepared and sent to the user. This class is supposed to keep in sync the runtime state and the
 * update messages. The update is marked as synchronized when it is sent to the user.
 *
 * <p>The state consists of the following components:
 *
 * <ul>
 *   <li>Expressions state. Tracks all message updates that are sent when the expression metadata
 *       (e.g. the type or the underlying method pointer) is changed.
 *   <li>Method pointers state. Tracks message updates containing method pointers. Messages with
 *       method pointers are tracked separately because they have different invalidation rules. E.g.
 *       they should be always re-sent when the execution item is popped from the stack.
 *   <li>Visualisations state. Tracks the state of visualisation updates.
 */
public class UpdatesSynchronizationState {

  private final Set<UUID> expressionsState = new HashSet<>();
  private final Set<UUID> visualisationsState = new HashSet<>();
  private final Set<UUID> methodPointersState = new HashSet<>();

  @Override
  public String toString() {
    return "UpdatesSynchronizationState{"
        + "expressionsState="
        + expressionsState
        + ", visualisationsState="
        + visualisationsState
        + ", methodPointersState="
        + methodPointersState
        + '}';
  }

  /**
   * Invalidate the state of the given expression.
   *
   * @param key the expression id.
   */
  public void invalidate(UUID key) {
    expressionsState.remove(key);
    visualisationsState.remove(key);
    methodPointersState.remove(key);
  }

  /* Expressions */

  /**
   * Checks if the given expression update is synchronized.
   *
   * @param key the expression id.
   * @return {@code true} if the expression update is synchronized.
   */
  public boolean isExpressionSync(UUID key) {
    return expressionsState.contains(key);
  }

  /**
   * Marks the given expression update as unsynchronized.
   *
   * @param key the expression id.
   */
  public void setExpressionUnsync(UUID key) {
    expressionsState.remove(key);
  }

  /**
   * Marks the given expression update as synchronized.
   *
   * @param key the expression id.
   */
  public void setExpressionSync(UUID key) {
    expressionsState.add(key);
  }

  /* Visualisations */

  /**
   * Checks if the given visualisation update is synchronized.
   *
   * @param key the expression id.
   * @return {@code true} if the visualisation update is synchronized.
   */
  public boolean isVisualisationSync(UUID key) {
    return visualisationsState.contains(key);
  }

  /**
   * Marks the given visualisation update as unsynchronized.
   *
   * @param key the expression id.
   */
  public void setVisualisationUnsync(UUID key) {
    visualisationsState.remove(key);
  }

  /**
   * Marks the given visualisation update as synchronized.
   *
   * @param key the expression id.
   */
  public void setVisualisationSync(UUID key) {
    visualisationsState.add(key);
  }

  /* Method pointers */

  /**
   * Checks if the given method pointer is synchronized.
   *
   * @param key the expression id.
   * @return {@code true} if the method pointer update is synchronized.
   */
  public boolean isMethodPointerSync(UUID key) {
    return methodPointersState.contains(key);
  }

  /**
   * Marks the method pointer as synchronized.
   *
   * @param key the expression id.
   */
  public void setMethodPointerSync(UUID key) {
    methodPointersState.add(key);
  }

  /** Clears the synchronization state of all method pointers. */
  public void clearMethodPointersState() {
    methodPointersState.clear();
  }
}

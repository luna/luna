package org.enso.interpreter.node.expression.builtin.number.smallInteger;

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Language;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;

@BuiltinMethod(type = "Small_Integer", name = "==", description = "Equality on numbers.")
public abstract class EqualsNode extends Node {

  abstract boolean execute(Object _this, Object that);

  static EqualsNode build() {
    return EqualsNodeGen.create();
  }

  @Specialization
  boolean doLong(long _this, long that) {
    return _this == that;
  }

  @Specialization
  boolean doDouble(long _this, double that) {
    return (double) _this == that;
  }

  @Specialization
  boolean doAtom(
      Atom _this,
      Atom that,
      @CachedContext(Language.class) ContextReference<Context> ctxRef,
      @Cached("getNumberConstructor(ctxRef)") AtomConstructor numberCons,
      @Cached("getIntegerConstructor(ctxRef)") AtomConstructor integerCons,
      @Cached("getSmallIntegerConstructor(ctxRef)") AtomConstructor smallIntCons) {
    return ((that.getConstructor() == numberCons)
        || (that.getConstructor() == integerCons)
        || (that.getConstructor() == smallIntCons)) && (_this.getConstructor() == that.getConstructor());
  }

  @Specialization
  boolean doAtom(
      long _this,
      Atom that,
      @CachedContext(Language.class) ContextReference<Context> ctxRef,
      @Cached("getNumberConstructor(ctxRef)") AtomConstructor numberCons,
      @Cached("getIntegerConstructor(ctxRef)") AtomConstructor integerCons,
      @Cached("getSmallIntegerConstructor(ctxRef)") AtomConstructor smallIntCons) {
    return (that.getConstructor() == numberCons)
        || (that.getConstructor() == integerCons)
        || (that.getConstructor() == smallIntCons);
  }

  @Fallback
  boolean doOther(Object _this, Object that) {
    return false;
  }

  AtomConstructor getNumberConstructor(ContextReference<Context> ctxRef) {
    return ctxRef.get().getBuiltins().number().getNumber();
  }

  AtomConstructor getIntegerConstructor(ContextReference<Context> ctxRef) {
    return ctxRef.get().getBuiltins().number().getInteger();
  }

  AtomConstructor getSmallIntegerConstructor(ContextReference<Context> ctxRef) {
    return ctxRef.get().getBuiltins().number().getBigInteger();
  }
}

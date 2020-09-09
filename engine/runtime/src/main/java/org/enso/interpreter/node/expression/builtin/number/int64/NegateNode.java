package org.enso.interpreter.node.expression.builtin.number.int64;

import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.dsl.BuiltinMethod;

@BuiltinMethod(type = "Int_64", name = "negate", description = "Negation for numbers.")
public class NegateNode extends Node {
  long execute(long _this) {
    return -_this;
  }
}

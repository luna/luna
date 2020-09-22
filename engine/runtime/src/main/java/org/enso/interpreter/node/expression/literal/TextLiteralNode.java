package org.enso.interpreter.node.expression.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.data.Text;

/** Node representing a constant String value. */
@NodeInfo(shortName = "StringLiteral", description = "Constant string literal expression")
public class TextLiteralNode extends ExpressionNode {
  private final Text value;

  private TextLiteralNode(String value) {
    this.value = Text.create(value);
  }

  /**
   * Creates an instance of this node.
   *
   * @param value the textual value to represent
   * @return a node representing the literal {@code value}
   */
  public static TextLiteralNode build(String value) {
    return new TextLiteralNode(value);
  }

  /**
   * Returns the constant value of this string literal.
   *
   * @param frame the stack frame for execution
   * @return the string value this node was created with
   */
  @Override
  public Text executeGeneric(VirtualFrame frame) {
    return value;
  }
}

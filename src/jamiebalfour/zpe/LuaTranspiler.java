package jamiebalfour.zpe;

import jamiebalfour.zpe.core.*;

public class LuaTranspiler {

  int indentation = 0;

  public String Transpile(IAST code, String s) {
    StringBuilder output = new StringBuilder();
    IAST current = code;

    while (current != null) {
      output.append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    if (output.toString().contains("function main()")) {
      output.append("\nmain()\n");
    }

    return output.toString();
  }

  private String addIndentation() {
    return "  ".repeat(indentation);
  }

  private String innerTranspile(IAST n) {
    switch (n.type) {
      case YASSByteCodes.FUNCTION:
        return transpileFunction(n);
      case YASSByteCodes.VAR:
      case YASSByteCodes.VAR_BY_REF:
      case YASSByteCodes.CONST:
        return transpile_var(n);
      case YASSByteCodes.NULL:
        return "nil";
      case YASSByteCodes.BOOL:
        return n.value.toString().equalsIgnoreCase("true") ? "true" : "false";
      case YASSByteCodes.STRING:
        return "\"" + n.value.toString().replace("\"", "\\\"") + "\"";
      case YASSByteCodes.INT:
      case YASSByteCodes.DOUBLE:
        return n.value.toString();
      case YASSByteCodes.PLUS:
        return innerTranspile(n.left) + " + " + innerTranspile(n.next);
      case YASSByteCodes.MINUS:
        return innerTranspile(n.left) + " - " + innerTranspile(n.next);
      case YASSByteCodes.MULT:
        return innerTranspile(n.left) + " * " + innerTranspile(n.next);
      case YASSByteCodes.DIVIDE:
        return innerTranspile(n.left) + " / " + innerTranspile(n.next);
      case YASSByteCodes.MODULO:
        return innerTranspile(n.left) + " % " + innerTranspile(n.next);
      case YASSByteCodes.EQUAL:
        return innerTranspile(n.left) + " == " + innerTranspile(n.middle);
      case YASSByteCodes.NEQUAL:
        return innerTranspile(n.left) + " ~= " + innerTranspile(n.middle);
      case YASSByteCodes.GT:
        return innerTranspile(n.left) + " > " + innerTranspile(n.middle);
      case YASSByteCodes.LT:
        return innerTranspile(n.left) + " < " + innerTranspile(n.middle);
      case YASSByteCodes.GTE:
        return innerTranspile(n.left) + " >= " + innerTranspile(n.middle);
      case YASSByteCodes.LTE:
        return innerTranspile(n.left) + " <= " + innerTranspile(n.middle);
      case YASSByteCodes.RETURN:
        return "return " + innerTranspile(n.left);
      case YASSByteCodes.LIST:
        return "{" + generateParameters((IAST) n.value) + "}";
      case YASSByteCodes.ASSOCIATION:
        return transpileMap(n);
      case YASSByteCodes.EXPRESSION:
        return innerTranspile((IAST) n.value);
      case YASSByteCodes.IF:
        return transpileIf(n);
      case YASSByteCodes.WHILE:
        return transpileWhile(n);
      case YASSByteCodes.FOR:
      case YASSByteCodes.FOR_TO:
        return transpileFor(n);
      case YASSByteCodes.IDENTIFIER:
        return n.id + "(" + generateParameters((IAST) n.value) + ")";
      case YASSByteCodes.EMPTY:
        return "#" + innerTranspile((IAST) n.left) + " == 0";
      default:
        return "-- unsupported node type: " + n.type;
    }
  }

  private String transpile_var(IAST n) {
    String id = n.id;
    return id.startsWith("$") ? id.substring(1) : id;
  }

  private String transpileFunction(IAST n) {
    StringBuilder output = new StringBuilder();
    String id = n.id;
    String params = generateParameters((IAST) n.value);

    output.append("function ").append(id).append("(").append(params).append(")\n");
    indentation++;

    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    indentation--;
    output.append("end");

    return output.toString();
  }

  private String generateParameters(IAST n) {
    StringBuilder output = new StringBuilder();
    IAST current = n;
    while (current != null) {
      output.append(transpile_var(current));
      current = current.next;
      if (current != null) {
        output.append(", ");
      }
    }
    return output.toString();
  }

  private String transpileMap(IAST n) {
    StringBuilder output = new StringBuilder("{");
    IAST current = (IAST) n.value;
    while (current != null) {
      output.append(innerTranspile(current)).append(" = ");
      current = current.next;
      output.append(innerTranspile(current));
      current = current.next;
      if (current != null) {
        output.append(", ");
      }
    }
    output.append("}");
    return output.toString();
  }

  private String transpileIf(IAST n) {
    StringBuilder output = new StringBuilder("if " + innerTranspile((IAST) n.value) + " then\n");
    indentation++;

    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    indentation--;
    output.append("end");

    return output.toString();
  }

  private String transpileWhile(IAST n) {
    StringBuilder output = new StringBuilder("while " + innerTranspile((IAST) n.value) + " do\n");
    indentation++;

    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    indentation--;
    output.append("end");

    return output.toString();
  }

  private String transpileFor(IAST n) {
    String var = transpile_var(n.middle.left.middle);
    String start = innerTranspile((IAST) n.middle.left.value);
    String end = innerTranspile(((IAST) ((IAST) n.value).value).middle);
    String step = (n.middle.value instanceof String) ? ", " + n.middle.value : "";

    StringBuilder output = new StringBuilder("for " + var + " = " + start + ", " + end + step + " do\n");
    indentation++;

    IAST current = n.left.next;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    indentation--;
    output.append("end");

    return output.toString();
  }
}

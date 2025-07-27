package jamiebalfour.zpe;

import jamiebalfour.HelperFunctions;
import jamiebalfour.zpe.core.*;

import java.util.*;

public class LuaTranspiler {

  int indentation = 0;
  boolean inClassDef = false;

  HashMap<String, String> yassToLuaFunctionMapping = new HashMap<>();
  ArrayList<String> usedFunctions = new ArrayList<>();
  ArrayList<String> addedFunctions = new ArrayList<>();
  ArrayList<String> builtInFunctions = new ArrayList<>();

  public String Transpile(IAST code, String s) {

    yassToLuaFunctionMapping.put("std_in", "print");
    yassToLuaFunctionMapping.put("floor", "math.floor");
    yassToLuaFunctionMapping.put("factorial", "math.factorial");
    yassToLuaFunctionMapping.put("list_get_length", "#");
    yassToLuaFunctionMapping.put("time", "os.time");
    yassToLuaFunctionMapping.put("character_to_integer", "string.byte");
    yassToLuaFunctionMapping.put("integer_to_character", "string.char");

    try {
      for (String fun : HelperFunctions.getResource("/jamiebalfour/zpe/additional_functions_lua.txt", this.getClass()).split("--")) {
        fun = fun.trim();
        String[] lines = fun.split(System.lineSeparator());
        builtInFunctions.add(lines[0]);
      }
    } catch (Exception e) {
      // Ignore
    }

    StringBuilder output = new StringBuilder();
    IAST current = code;
    while (current != null) {
      output.append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    StringBuilder additionalFuncs = new StringBuilder();
    try {
      for (String fun : HelperFunctions.getResource("/jamiebalfour/zpe/additional_functions_lua.txt", this.getClass()).split("--")) {
        fun = fun.trim();
        String[] lines = fun.split(System.lineSeparator());
        String funcName = lines[0];

        StringBuilder funBuilder = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
          funBuilder.append(lines[i]).append("\n");
        }

        if (usedFunctions.contains(funcName) && !addedFunctions.contains(funcName)) {
          additionalFuncs.append(funBuilder);
        }
      }
    } catch (Exception e) {
      // Ignore
    }

    if (output.toString().contains("function main")) {
      output.append("\nmain()\n");
    }

    return additionalFuncs + output.toString();
  }

  private String addIndentation() {
    return "  ".repeat(indentation);
  }

  private String innerTranspile(IAST n) {
    switch (n.type) {
      case YASSByteCodes.IDENTIFIER: {
        return transpileIdentifier(n);
      }
      case YASSByteCodes.VAR:
      case YASSByteCodes.CONST:
      case YASSByteCodes.VAR_BY_REF: {
        return transpileVar(n);
      }
      case YASSByteCodes.TYPED_PARAMETER:{
        return fixId(n.left.id);
      }
      case YASSByteCodes.BOOL: {
        return n.value.toString().toLowerCase();
      }
      case YASSByteCodes.NULL: {
        return "nil";
      }
      case YASSByteCodes.NEGATIVE:{
        return "-" + innerTranspile((IAST) n.value);
      }
      case YASSByteCodes.INT:
      case YASSByteCodes.DOUBLE: {
        return n.value.toString();
      }
      case YASSByteCodes.STRING: {
        return "\"" + n.value.toString().replace("\"", "\\\"") + "\"";
      }
      case YASSByteCodes.LIST: {
        return "{" + generateParameters((IAST) n.value) + "}";
      }
      case YASSByteCodes.RETURN: {
        return "return " + innerTranspile(n.left);
      }
      case YASSByteCodes.TYPE:{
        return "type (" + innerTranspile((IAST)n.value) + ")";
      }
      case YASSByteCodes.PLUS: {
        return innerTranspile(n.left) + " + " + innerTranspile(n.next);
      }
      case YASSByteCodes.MINUS: {
        return innerTranspile(n.left) + " - " + innerTranspile(n.next);
      }
      case YASSByteCodes.MULT: {
        return innerTranspile(n.left) + " * " + innerTranspile(n.next);
      }
      case YASSByteCodes.DIVIDE: {
        return innerTranspile(n.left) + " / " + innerTranspile(n.next);
      }
      case YASSByteCodes.MODULO: {
        return innerTranspile(n.left) + " % " + innerTranspile(n.next);
      }
      case YASSByteCodes.EQUAL: {
        return innerTranspile(n.left) + " == " + innerTranspile(n.middle);
      }
      case YASSByteCodes.NEQUAL: {
        return innerTranspile(n.left) + " ~= " + innerTranspile(n.middle);
      }
      case YASSByteCodes.LT: {
        return innerTranspile(n.left) + " < " + innerTranspile(n.middle);
      }
      case YASSByteCodes.LTE: {
        return innerTranspile(n.left) + " <= " + innerTranspile(n.middle);
      }
      case YASSByteCodes.GT: {
        return innerTranspile(n.left) + " > " + innerTranspile(n.middle);
      }
      case YASSByteCodes.GTE: {
        return innerTranspile(n.left) + " >= " + innerTranspile(n.middle);
      }
      case YASSByteCodes.LAND: {
        return innerTranspile(n.left) + " and " + innerTranspile(n.next);
      }
      case YASSByteCodes.LOR: {
        return innerTranspile(n.left) + " or " + innerTranspile(n.next);
      }
      case YASSByteCodes.FUNCTION: {
        return transpileFunction(n);
      }
      case YASSByteCodes.IF: {
        return transpileIf(n);
      }
      case YASSByteCodes.WHILE: {
        return transpileWhile(n);
      }
      case YASSByteCodes.FOR: {
        return transpileFor(n);
      }
      case YASSByteCodes.FOR_TO: {
        return transpileForTo(n);
      }
      case YASSByteCodes.MODULE: {
        return transpileModule(n);
      }
      case YASSByteCodes.EXPRESSION: {
        return transpileExpression(n);
      }
      default:
        return "";
    }
  }

  private String transpileExpression(IAST n) {
    return innerTranspile((IAST) n.value);
  }

  private String transpileModule(IAST n) {

    StringBuilder output = new StringBuilder();

    IAST current = (IAST) n.value;
    while (current != null) {
      output.append(innerTranspile(current)).append(System.lineSeparator());
      current = current.next;
    }

    return output.toString();
  }

  private String generateParameters(IAST n) {
    StringBuilder output = new StringBuilder();
    IAST current = n;
    while (current != null) {
      output.append(innerTranspile(current));
      current = current.next;
      if (current != null) output.append(", ");
    }
    return output.toString();
  }

  private String transpileIdentifier(IAST n) {
    String id = fixId(n.id);
    if (yassToLuaFunctionMapping.containsKey(id)) {
      id = yassToLuaFunctionMapping.get(id);
    }
    if (ZPEKit.internalFunctionExists(id) || builtInFunctions.contains(id)) {
      usedFunctions.add(id);
    }
    return id + "(" + generateParameters((IAST) n.value) + ")";
  }

  private String transpileVar(IAST n) {
    return fixId(n.id);
  }

  private String transpileFunction(IAST n) {
    String id = fixId(n.id);
    addedFunctions.add(id);
    StringBuilder output = new StringBuilder();
    output.append("function ").append(id).append("(").append(generateParameters((IAST) n.value)).append(")\n");
    indentation++;
    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }
    indentation--;
    output.append("end\n");
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
    if (n.middle != null) {
      if(n.middle.type == YASSByteCodes.ELSEIF){
        output.append(addIndentation()).append("elseif " + innerTranspile((IAST) n.middle.value) + " then\n");
      } else{
        output.append(addIndentation()).append("else\n");
      }

      indentation++;
      current = n.middle;
      if(n.middle.type == YASSByteCodes.ELSEIF){
        current = n.middle.left;
      }
      while (current != null) {
        output.append(addIndentation()).append(innerTranspile(current)).append("\n");
        current = current.next;
      }
      indentation--;
    }
    output.append(addIndentation()).append("end");
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
    output.append(addIndentation()).append("end");
    return output.toString();
  }

  private String transpileFor(IAST n) {
    String var = innerTranspile(n.middle.left.middle);
    String start = innerTranspile((IAST) n.middle.left.value);
    String end = innerTranspile(((IAST) ((IAST) n.value).value).middle);
    String step = (n.middle.value instanceof String) ? (", " + n.middle.value) : "";
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

  private String transpileForTo(IAST n) {
    String var = innerTranspile(n.middle.left.middle);
    String start = innerTranspile((IAST) n.middle.left.value);
    String end = innerTranspile((IAST) ((IAST) n.value).value);
    StringBuilder output = new StringBuilder("for " + var + " = " + start + ", " + end + " do\n");
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

  private String fixId(String id) {
    if(id.contains("$")) id = id.replace("$", "");
    if (id.contains("/")) id = id.replace("/", "_");
    if (id.contains("::")) id = id.substring(id.indexOf("::") + 2);
    if (id.contains("~")) id = id.substring(id.indexOf("~") + 1);
    return id;
  }
}
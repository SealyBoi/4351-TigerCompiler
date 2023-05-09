package Mips;
import Temp.Temp;
import Temp.TempList;
import Temp.Label;
import Temp.LabelList;
import java.util.Hashtable;

public class Codegen {
  MipsFrame frame;
  public Codegen(MipsFrame f) {frame = f;}

  private Assem.InstrList ilist = null, last = null;

  private void emit(Assem.Instr inst) {
    if (last != null)
      last = last.tail = new Assem.InstrList(inst, null);
    else {
      if (ilist != null)
	throw new Error("Codegen.emit");
      last = ilist = new Assem.InstrList(inst, null);
    }
  }

  Assem.InstrList codegen(Tree.Stm s) {
    munchStm(s);
    Assem.InstrList l = ilist;
    ilist = last = null;
    return l;
  }

  static Assem.Instr OPER(String a, TempList d, TempList s, LabelList j) {
    return new Assem.OPER("\t" + a, d, s, j);
  }
  static Assem.Instr OPER(String a, TempList d, TempList s) {
    return new Assem.OPER("\t" + a, d, s);
  }
  static Assem.Instr MOVE(String a, Temp d, Temp s) {
    return new Assem.MOVE("\t" + a, d, s);
  }

  static TempList L(Temp h) {
    return new TempList(h, null);
  }
  static TempList L(Temp h, TempList t) {
    return new TempList(h, t);
  }

  void munchStm(Tree.Stm s) {
    if (s instanceof Tree.MOVE) 
      munchStm((Tree.MOVE)s);
    else if (s instanceof Tree.UEXP)
      munchStm((Tree.UEXP)s);
    else if (s instanceof Tree.JUMP)
      munchStm((Tree.JUMP)s);
    else if (s instanceof Tree.CJUMP)
      munchStm((Tree.CJUMP)s);
    else if (s instanceof Tree.LABEL)
      munchStm((Tree.LABEL)s);
    else
      throw new Error("Codegen.munchStm");
  }

  void munchStm(Tree.MOVE s) {
    emit(new Assem.MOVE("move `d0, `s0", munchExp(s.dst), munchExp(s.src)));
  }

  void munchStm(Tree.UEXP s) {
    munchExp(s.exp);
  }

  void munchStm(Tree.JUMP s) {
    emit(new Assem.OPER("j " + s.targets.head, null, null, s.targets));
  }

  private static String[] CJUMP = new String[10];
  static {
    CJUMP[Tree.CJUMP.EQ ] = "beq";
    CJUMP[Tree.CJUMP.NE ] = "bne";
    CJUMP[Tree.CJUMP.LT ] = "blt";
    CJUMP[Tree.CJUMP.GT ] = "bgt";
    CJUMP[Tree.CJUMP.LE ] = "ble";
    CJUMP[Tree.CJUMP.GE ] = "bge";
    CJUMP[Tree.CJUMP.ULT] = "bltu";
    CJUMP[Tree.CJUMP.ULE] = "bleu";
    CJUMP[Tree.CJUMP.UGT] = "bgtu";
    CJUMP[Tree.CJUMP.UGE] = "bgeu";
  }

  void munchStm(Tree.CJUMP s) {
    String op = CJUMP[s.relop];
    TempList values = L(munchExp(s.left), L(munchExp(s.right)));
    LabelList jumps = new LabelList(s.iftrue, new LabelList(s.iffalse, null));
    emit(new Assem.OPER(op + "`s0, `s1, `j0", null, values, jumps));
  }

  void munchStm(Tree.LABEL l) {
    emit(new Assem.LABEL(l.label.toString() + ":", l.label));
  }

  Temp munchExp(Tree.Exp s) {
    if (s instanceof Tree.CONST)
      return munchExp((Tree.CONST)s);
    else if (s instanceof Tree.NAME)
      return munchExp((Tree.NAME)s);
    else if (s instanceof Tree.TEMP)
      return munchExp((Tree.TEMP)s);
    else if (s instanceof Tree.BINOP)
      return munchExp((Tree.BINOP)s);
    else if (s instanceof Tree.MEM)
      return munchExp((Tree.MEM)s);
    else if (s instanceof Tree.CALL)
      return munchExp((Tree.CALL)s);
    else
      throw new Error("Codegen.munchExp");
  }

  Temp munchExp(Tree.CONST e) {
    if (e.value != 0) {
      Temp tmp = new Temp();
      TempList l = L(tmp);
      emit(new Assem.OPER("li `d0," + e.value, l, null));
      return tmp;
    } else {
      return frame.ZERO;
    }
  }

  Temp munchExp(Tree.NAME e) {
    Temp tmp = new Temp();
    emit(OPER("la `d0 " + e.label.toString(), L(tmp), null));
    return tmp;
  }

  Temp munchExp(Tree.TEMP e) {
    if (e.temp == frame.FP) {
      Temp t = new Temp();
      emit(OPER("addu `d0 `s0 " + frame.name + "_framesize",
		L(t), L(frame.SP)));
      return t;
    }
    return e.temp;
  }

  private static String[] BINOP = new String[10];
  static {
    BINOP[Tree.BINOP.PLUS   ] = "add";
    BINOP[Tree.BINOP.MINUS  ] = "sub";
    BINOP[Tree.BINOP.MUL    ] = "mulo";
    BINOP[Tree.BINOP.DIV    ] = "div";
    BINOP[Tree.BINOP.AND    ] = "and";
    BINOP[Tree.BINOP.OR     ] = "or";
    BINOP[Tree.BINOP.LSHIFT ] = "sll";
    BINOP[Tree.BINOP.RSHIFT ] = "srl";
    BINOP[Tree.BINOP.ARSHIFT] = "sra";
    BINOP[Tree.BINOP.XOR    ] = "xor";
  }

  private static int shift(int i) {
    int shift = 0;
    if ((i >= 2) && ((i & (i - 1)) == 0)) {
      while (i > 1) {
	shift += 1;
	i >>= 1;
      }
    }
    return shift;
  }

  Temp munchExp(Tree.BINOP e) {
    Temp tmp = new Temp();
    Temp lTmp = munchExp(e.left);
    Temp rTmp = munchExp(e.right);
    TempList l = L(tmp);
    String op = BINOP[e.binop];
    if (e.left instanceof Tree.CONST && e.right instanceof Tree.CONST) {
      Tree.CONST left = (Tree.CONST) e.left;
      Tree.CONST right = (Tree.CONST) e.right;
      emit(new Assem.OPER(op + " `d0," + left.value + "," + right.value, l, null));
    } else if (e.left instanceof Tree.CONST) {
      Tree.CONST left = (Tree.CONST) e.left;
      TempList rTmpList = L(rTmp);
      emit(new Assem.OPER(op + " `d0," + left.value + ", `s0", l, rTmpList));
    } else if (e.right instanceof Tree.CONST) {
      Tree.CONST right = (Tree.CONST) e.right;
      TempList lTmpList = L(lTmp);
      emit(new Assem.OPER(op + " `d0, `s0," + right.value, l, lTmpList));
    } else {
      TempList rlTmpList = L(lTmp, L(rTmp, null));
      emit(new Assem.OPER(op + " `d0, `s0,`s1", l, rlTmpList));
    }
    return tmp;
  }

  Temp munchExp(Tree.MEM e) {
    Temp tmp = new Temp();
    if (e.exp instanceof Tree.CONST) {
      Tree.CONST exp = (Tree.CONST) e.exp;
      emit(OPER("lw `d0 " + exp.value, L(tmp), null));
    } else {
      emit(OPER("lw `d0 (`s0)", L(tmp), L(munchExp(e.exp))));
    }
    return tmp;
  }

  Temp munchExp(Tree.CALL s) {
    if (s.func instanceof Tree.NAME) {
      Tree.NAME name = (Tree.NAME) s.func;
      emit(OPER("jal " + name, frame.calldefs, munchArgs(0, s.args)));
    } else {
      emit(OPER("jal `d0 `s0", frame.calldefs, L(munchExp(s.func), munchArgs(0, s.args))));
    }
    return frame.V0;
  }

  private TempList munchArgs(int i, Tree.ExpList args) {
    if (args == null)
      return null;
    Temp src = munchExp(args.head);
    if (i > frame.maxArgs)
      frame.maxArgs = i;
    switch (i) {
    case 0:
      emit(MOVE("move `d0 `s0", frame.A0, src));
      break;
    case 1:
      emit(MOVE("move `d0 `s0", frame.A1, src));
      break;
    case 2:
      emit(MOVE("move `d0 `s0", frame.A2, src));
      break;
    case 3:
      emit(MOVE("move `d0 `s0", frame.A3, src));
      break;
    default:
      emit(OPER("sw `s0 " + (i-1)*frame.wordSize() + "(`s1)",
		null, L(src, L(frame.SP))));
      break;
    }
    return L(src, munchArgs(i+1, args.tail));
  }
}

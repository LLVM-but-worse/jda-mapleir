package org.mapleir.jdaplugin;

import club.bytecode.the.jda.decompilers.bytecode.*;
import club.bytecode.the.jda.settings.JDADecompilerSettings;
import com.google.common.collect.Iterators;
import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.ImmediateEdge;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.*;
import org.mapleir.ir.code.stmt.*;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.algorithms.ExtendedDfs;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.*;

public class ILDecompiler extends BytecodeDecompiler implements MapleComponent {
    public ILDecompiler() {
        settings.registerSetting(new JDADecompilerSettings.SettingsEntry("kill-dead-code", "Eliminate dead code", true));
        settings.registerSetting(new JDADecompilerSettings.SettingsEntry("simplify-arithmetic", "Simplify and deobfuscate constant arithmetic", true));
    }

    @Override
    protected MethodNodeDecompiler getMethodNodeDecompiler(PrefixedStringBuilder sb, ClassNode cn, Iterator<MethodNode> it) {
        return new ILMethodDecompiler(this, sb, it.next(), cn);
    }

    @Override
    public String getName() {
        return "MapleIL";
    }
}

class ILMethodDecompiler extends MethodNodeDecompiler {
    public ILMethodDecompiler(BytecodeDecompiler parent, PrefixedStringBuilder sb, MethodNode mn, ClassNode cn) {
        super(parent, sb, mn, cn);
        printDetailedMetadata = false;
    }

    @Override
    protected InstructionPrinter getInstructionPrinter(MethodNode m, TypeAndName[] args) {
        return new ILInstructionPrinter(this, m, args);
    }
}

class ILInstructionPrinter extends InstructionPrinter {
    DeobfuscateFilter deobfuscator = new DeobfuscateFilter();

    public ILInstructionPrinter(MethodNodeDecompiler parent, MethodNode m, TypeAndName[] args) {
        super(parent, m, args);
    }

    @Override
    public ArrayList<String> createPrint() {
        ControlFlowGraph cfg = ControlFlowGraphBuilder.build(ASMAdaptor.wrapMethodNode(mNode));
        if (parent.getParent().getSettings().getEntry("simplify-arithmetic").getBool()) {
            deobfuscator.simplifyArithmetic(cfg);
        }
        if (parent.getParent().getSettings().getEntry("kill-dead-code").getBool()) {
            deobfuscator.killDeadCode(cfg);
        }
        BoissinotDestructor.leaveSSA(cfg);
        LocalsReallocator.realloc(cfg);

        TabbedStringWriter sw = new TabbedStringWriter();
        printCode(sw, cfg);
        return new ArrayList<>(Arrays.asList(sw.toString().split(System.lineSeparator())));
    }

    private void printCode(TabbedStringWriter sw, ControlFlowGraph cfg) {
        List<BasicBlock> verticesInOrder = new ExtendedDfs<BasicBlock>(cfg, ExtendedDfs.TOPO) {
            @Override
            protected Iterable<? extends FastGraphEdge<BasicBlock>> order(Set<? extends FastGraphEdge<BasicBlock>> edges) {
                // Java 11 has Predicate.not... so FunCTiONAl
                return () -> Iterators.concat(
                        // visit immediate edges last, so (topoorder = reverse postorder) will have immediates first
                        edges.stream().filter(e -> !(e instanceof ImmediateEdge)).iterator(),
                        edges.stream().filter(ImmediateEdge.class::isInstance).iterator());
            }
        }.run(cfg.getEntries().iterator().next()).getTopoOrder();
        for (BasicBlock b : verticesInOrder) {
            printBlock(sw, b);
        }
    }

    private void printBlock(TabbedStringWriter sw, BasicBlock b) {
        sw.print(b.getDisplayName()).print(":");
        int handlerCount = 0;
        for (ExceptionRange<BasicBlock> erange : b.getGraph().getRanges()) {
            if (erange.containsVertex(b)) {
                if (handlerCount++ > 0) sw.newline();
                else sw.print(" ");
                sw.print("// Exception handler: Block ").print(erange.getHandler().getDisplayName()).print(" [");
                int typeCount = 0;
                for (Type exceptionType : erange.getTypes()) {
                    sw.print(exceptionType.getClassName());
                    if (++typeCount != erange.getTypes().size())
                        sw.print(", ");
                }
                sw.print("]");
            }
        }
        sw.tab();
        for (Stmt stmt : b) {
            sw.newline();
            printStmt(sw, stmt);
        }
        sw.untab();
        sw.newline();
    }


    public void printStmt(TabbedStringWriter sw, Stmt stmt) {
        int opcode = stmt.getOpcode();

        switch (opcode) {
            case Opcode.LOCAL_STORE:
            case Opcode.PHI_STORE: {
                AbstractCopyStmt cvs = (AbstractCopyStmt) stmt;

                this.printExpr(sw, cvs.getVariable());
                sw.print(" = ");
                if (cvs.isSynthetic()) {
                    // logic duplicated from InstructionPrinter lol
                    int varIndex = cvs.getVariable().getIndex();
                    if (varIndex == 0 && !Modifier.isStatic(mNode.access)) {
                        sw.print("this");
                    } else {
                        final int refIndex = varIndex - (Modifier.isStatic(mNode.access) ? 0 : 1);
                        if (refIndex >= 0 && refIndex < args.length) {
                            sw.print(args[refIndex].name);
                        }
                    }
                } else {
                    this.printExpr(sw, cvs.getExpression());
                }
                break;
            }

            case Opcode.ARRAY_STORE: {
                ArrayStoreStmt ars = (ArrayStoreStmt) stmt;

                Expr arrayExpr = ars.getArrayExpression();
                Expr indexExpr = ars.getIndexExpression();
                Expr valexpr = ars.getValueExpression();

                int accessPriority = Expr.Precedence.ARRAY_ACCESS.ordinal();
                int basePriority = arrayExpr.getPrecedence();
                if (basePriority > accessPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, arrayExpr);
                if (basePriority > accessPriority) {
                    sw.print(')');
                }
                sw.print('[');
                this.printExpr(sw, indexExpr);
                sw.print(']');
                sw.print(" = ");
                this.printExpr(sw, valexpr);
                break;
            }
            case Opcode.FIELD_STORE: {
                FieldStoreStmt fss = (FieldStoreStmt) stmt;

                Expr valExpr = fss.getValueExpression();
                if (!fss.isStatic()) {
                    int selfPriority = Expr.Precedence.MEMBER_ACCESS.ordinal();
                    Expr instanceExpr = fss.getInstanceExpression();
                    int basePriority = instanceExpr.getPrecedence();
                    if (basePriority > selfPriority) {
                        sw.print('(');
                    }
                    this.printExpr(sw, instanceExpr);
                    if (basePriority > selfPriority) {
                        sw.print(')');
                    }
                } else {
                    sw.print(fss.getOwner().replace("/", "."));
                }
                sw.print('.');
                sw.print(fss.getName());
                sw.print(" = ");
                this.printExpr(sw, valExpr);
                break;
            }
            case Opcode.COND_JUMP: {
                ConditionalJumpStmt cjs = (ConditionalJumpStmt) stmt;

                sw.print("if (");
                this.printExpr(sw, cjs.getLeft());
                sw.print(" ").print(cjs.getComparisonType().getSign()).print(" ");
                this.printExpr(sw, cjs.getRight());
                sw.print(")");
                sw.tab().newline().print("goto ")
                        .print(cjs.getTrueSuccessor().getDisplayName()).untab();
                break;
            }
            case Opcode.UNCOND_JUMP: {
                UnconditionalJumpStmt ujs = (UnconditionalJumpStmt) stmt;
                sw.print("goto ").print(ujs.getTarget().getDisplayName());
                break;
            }
            case Opcode.THROW: {
                ThrowStmt ts = (ThrowStmt) stmt;
                sw.print("throw ");
                this.printExpr(sw, ts.getExpression());
                break;
            }
            case Opcode.MONITOR: {
                MonitorStmt ms = (MonitorStmt) stmt;

                switch (ms.getMode()) {
                    case ENTER: {
                        sw.print("synchronized_enter ");
                        break;
                    }
                    case EXIT: {
                        sw.print("synchronized_exit ");
                        break;
                    }
                }

                this.printExpr(sw, ms.getExpression());
                break;
            }
            case Opcode.POP: {
                PopStmt ps = (PopStmt) stmt;
                this.printExpr(sw, ps.getExpression());
                break;
            }
            case Opcode.RETURN: {
                ReturnStmt rs = (ReturnStmt) stmt;
                sw.print("return");
                if (rs.getExpression() != null) {
                    sw.print(" ");
                    this.printExpr(sw, rs.getExpression());
                }
                break;
            }
            case Opcode.SWITCH_JUMP: {
                SwitchStmt ss = (SwitchStmt) stmt;
                sw.print("switch(");
                this.printExpr(sw, ss.getExpression());
                sw.print(") {");
                sw.tab();
                for (Map.Entry<Integer, BasicBlock> e : ss.getTargets().entrySet()) {
                    sw.newline().print("case ").print(String.valueOf(e.getKey()))
                            .print(": goto ").print(e.getValue().getDisplayName());
                }
                sw.newline().print(".default: goto ")
                        .print(ss.getDefaultTarget().getDisplayName());
                sw.untab().newline().print("}");
                break;
            }
            default: {
                throw new UnsupportedOperationException("Got: " + Opcode.opname(opcode));
            }
        }
    }

    private String literalToString(Object o) {
        if (o == null) {
            return "null";
        } else if (o instanceof String) {
            return "\"" + o.toString() + "\"";
        } else {
            return o.toString();
        }
    }

    private void printExpr(TabbedStringWriter sw, Expr e) {
        int opcode = e.getOpcode();

        switch (opcode) {
            case Opcode.CONST_LOAD: {
                ConstantExpr ce = (ConstantExpr) e;
                sw.print(literalToString(ce.getConstant()));
                break;
            }
            case Opcode.LOCAL_LOAD: {
                VarExpr ve = (VarExpr) e;
                // TODO: display name
                sw.print(ve.getLocal().toString());
                break;
            }
            case Opcode.FIELD_LOAD: {
                FieldLoadExpr fle = (FieldLoadExpr) e;

                if (fle.isStatic()) {
                    sw.print(fle.getOwner().replace("/", "."));
                } else {
                    Expr instanceExpr = fle.getInstanceExpression();
                    int selfPriority = fle.getPrecedence();
                    int basePriority = instanceExpr.getPrecedence();
                    if (basePriority > selfPriority) {
                        sw.print('(');
                    }
                    this.printExpr(sw, instanceExpr);
                    if (basePriority > selfPriority) {
                        sw.print(')');
                    }
                }

                sw.print(".").print(fle.getName());
                break;
            }
            case Opcode.ARRAY_LOAD: {
                ArrayLoadExpr ale = (ArrayLoadExpr) e;

                Expr arrayExpr = ale.getArrayExpression();
                Expr indexExpr = ale.getIndexExpression();

                int selfPriority = ale.getPrecedence();
                int expressionPriority = arrayExpr.getPrecedence();
                if (expressionPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, arrayExpr);
                if (expressionPriority > selfPriority) {
                    sw.print(')');
                }
                sw.print('[');
                this.printExpr(sw, indexExpr);
                sw.print(']');
                break;
            }
            case Opcode.INVOKE: {
                InvocationExpr ie = (InvocationExpr) e;

                if (ie.isDynamic()) {
                    sw.print("dynamic_invoke<");
                    sw.print(((DynamicInvocationExpr) ie).getProvidedFuncType().getClassName().replace("/", "."));
                    sw.print(">(");
                }
                
                if (ie.isStatic()) {
                    sw.print(ie.getOwner().replace("/", "."));
                } else {
                    int memberAccessPriority = Expr.Precedence.MEMBER_ACCESS.ordinal();
                    Expr instanceExpression = ie.getPhysicalReceiver();
                    int instancePriority = instanceExpression.getPrecedence();
                    if (instancePriority > memberAccessPriority) {
                        sw.print('(');
                    }
                    this.printExpr(sw, instanceExpression);
                    if (instancePriority > memberAccessPriority) {
                        sw.print(')');
                    }
                }

                sw.print('.').print(ie.getName()).print('(');

                Expr[] args = ie.getPrintedArgs();
                for (int i = 0; i < args.length; i++) {
                    this.printExpr(sw, args[i]);
                    if ((i + 1) < args.length) {
                        sw.print(", ");
                    }
                }

                sw.print(')');
                if (ie.isDynamic()) {
                    sw.print(')');
                }
                break;
            }
            case Opcode.ARITHMETIC: {
                ArithmeticExpr ae = (ArithmeticExpr) e;

                Expr left = ae.getLeft();
                Expr right = ae.getRight();

                int selfPriority = ae.getPrecedence();
                int leftPriority = left.getPrecedence();
                int rightPriority = right.getPrecedence();
                if (leftPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, left);
                if (leftPriority > selfPriority) {
                    sw.print(')');
                }
                sw.print(" " + ae.getOperator().getSign() + " ");
                if (rightPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, right);
                if (rightPriority > selfPriority) {
                    sw.print(')');
                }

                break;
            }
            case Opcode.NEGATE: {
                NegationExpr ne = (NegationExpr) e;

                Expr expr = ne.getExpression();
                int selfPriority = ne.getPrecedence();
                int exprPriority = expr.getPrecedence();
                sw.print('-');
                if (exprPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, expr);
                if (exprPriority > selfPriority) {
                    sw.print(')');
                }

                break;
            }
            case Opcode.ALLOC_OBJ: {
                AllocObjectExpr aoe = (AllocObjectExpr) e;
                sw.print("new ").print(aoe.getType().getClassName().replace("/", "."));
                break;
            }
            case Opcode.INIT_OBJ: {
                InitialisedObjectExpr ioe = (InitialisedObjectExpr) e;

                sw.print("new ");
                sw.print(ioe.getOwner().replace("/", "."));
                sw.print('(');

                Expr[] args = ioe.getParameterExprs();
                for (int i = 0; i < args.length; i++) {
                    boolean needsComma = (i + 1) < args.length;
                    this.printExpr(sw, args[i]);
                    if (needsComma) {
                        sw.print(", ");
                    }
                }
                sw.print(')');

                break;
            }
            case Opcode.NEW_ARRAY: {
                NewArrayExpr nae = (NewArrayExpr) e;

                Type type = nae.getType();
                sw.print("new " + type.getElementType().getClassName());

                Expr[] bounds = nae.getBounds();
                for (int dim = 0; dim < type.getDimensions(); dim++) {
                    sw.print('[');
                    if (dim < bounds.length) {
                        this.printExpr(sw, bounds[dim]);
                    }
                    sw.print(']');
                }
                break;
            }
            case Opcode.ARRAY_LEN: {
                ArrayLengthExpr ale = (ArrayLengthExpr) e;

                Expr expr = ale.getExpression();
                int selfPriority = ale.getPrecedence();
                int expressionPriority = expr.getPrecedence();
                if (expressionPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, expr);
                if (expressionPriority > selfPriority) {
                    sw.print(')');
                }
                sw.print(".length");
                break;
            }
            case Opcode.CAST: {
                CastExpr ce = (CastExpr) e;

                Expr expr = ce.getExpression();
                int selfPriority = ce.getPrecedence();
                int exprPriority = expr.getPrecedence();
                sw.print('(');
                Type type = ce.getType();
                sw.print(type.getClassName());
                sw.print(')');
                if (exprPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, expr);
                if (exprPriority > selfPriority) {
                    sw.print(')');
                }
                break;
            }
            case Opcode.INSTANCEOF: {
                InstanceofExpr ioe = (InstanceofExpr) e;

                Expr expr = ioe.getExpression();

                int selfPriority = ioe.getPrecedence();
                int expressionPriority = expr.getPrecedence();
                if (expressionPriority > selfPriority) {
                    sw.print('(');
                }
                this.printExpr(sw, expr);
                if (expressionPriority > selfPriority) {
                    sw.print(')');
                }
                sw.print(" instanceof ");
                sw.print(ioe.getType().getClassName());
                break;
            }
            case Opcode.COMPARE: {
                ComparisonExpr ce = (ComparisonExpr) e;
                sw.print("compare(");
                this.printExpr(sw, ce.getLeft());
                switch (ce.getComparisonType()) {
                    case CMP: {
                        sw.print("==");
                        break;
                    }
                    case LT: {
                        sw.print("<");
                        break;
                    }
                    case GT: {
                        sw.print(">");
                        break;
                    }
                }
                this.printExpr(sw, ce.getRight());
                sw.print(")");
                break;
            }
            case Opcode.CATCH: {
                sw.print("catch()");
                break;
            }
            default: {
                throw new UnsupportedOperationException("Got: " + Opcode.opname(opcode));
            }
        }
    }
}

package org.mapleir.jdaplugin;

import club.bytecode.the.jda.decompilers.filter.DecompileFilter;
import org.mapleir.asm.ClassHelper;
import org.mapleir.deob.intraproc.eval.ExpressionEvaluator;
import org.mapleir.deob.intraproc.eval.impl.ReflectiveFunctorFactory;
import org.mapleir.deob.passes.DeadCodeEliminationPass;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.codegen.ControlFlowGraphDumper;
import org.objectweb.asm.tree.ClassNode;

public class DeobfuscateFilter implements DecompileFilter, MapleComponent {
    private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(new ReflectiveFunctorFactory());
    private final DeadCodeEliminationPass deadCodeEliminationPass = new DeadCodeEliminationPass();

    @Override
    public void process(ClassNode cn) {
        if (cn == null)
            return;
        org.mapleir.asm.ClassNode wrappedCn = ClassHelper.create(cn);
        for (org.mapleir.asm.MethodNode mn : wrappedCn.getMethods()) {
            ControlFlowGraph cfg = ControlFlowGraphBuilder.build(mn);
            simplifyArithmetic(cfg);
            killDeadCode(cfg);
            BoissinotDestructor.leaveSSA(cfg);
            LocalsReallocator.realloc(cfg);
            (new ControlFlowGraphDumper(cfg, mn)).dump();
            System.out.println("Processed " + mn);
        }
    }

    /**
     * @param cfg ssa-form cfg
     */
    public void simplifyArithmetic(ControlFlowGraph cfg) {
        for (BasicBlock block : cfg.vertices()) {
            for (Stmt stmt : block) {
                for(CodeUnit cu : stmt.enumerateExecutionOrder()) {
                    if(cu instanceof Expr) {
                        Expr e = (Expr) cu;
                        CodeUnit par = e.getParent();
                        if(par != null) {

                            Expr val = expressionEvaluator.eval(cfg.getLocals(), e);
                            if(val != null && !val.equivalent(e)) {
                                cfg.writeAt(par, e, val);
                            } else if(e instanceof ArithmeticExpr) {
                                val = expressionEvaluator.simplifyArithmetic(cfg.getLocals(), (ArithmeticExpr)e);

                                if (val != null) {
                                    cfg.writeAt(par, e, val);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @param cfg ssa-form cfg
     */
    public void killDeadCode(ControlFlowGraph cfg) {
        deadCodeEliminationPass.process(cfg);
    }

    @Override
    public String getName() {
        return "Deobfuscator";
    }
}

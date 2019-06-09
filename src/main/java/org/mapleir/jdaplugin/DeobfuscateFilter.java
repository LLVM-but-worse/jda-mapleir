package org.mapleir.jdaplugin;

import club.bytecode.the.jda.decompilers.filter.DecompileFilter;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.codegen.ControlFlowGraphDumper;
import org.objectweb.asm.tree.ClassNode;

public class DeobfuscateFilter implements DecompileFilter, MapleComponent {
    @Override
    public void process(ClassNode cn) {
        if (cn == null)
            return;
        org.mapleir.asm.ClassNode wrappedCn = new org.mapleir.asm.ClassNode(cn);
        for (org.mapleir.asm.MethodNode mn : wrappedCn.getMethods()) {
            ControlFlowGraph cfg = ControlFlowGraphBuilder.build(mn);
            BoissinotDestructor.leaveSSA(cfg);
            LocalsReallocator.realloc(cfg);
            (new ControlFlowGraphDumper(cfg, mn)).dump();
            System.out.println("Processed " + mn);
        }
    }

    @Override
    public String getName() {
        return "Deobfuscator";
    }
}

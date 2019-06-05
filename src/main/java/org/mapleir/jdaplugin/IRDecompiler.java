package org.mapleir.jdaplugin;

import club.bytecode.the.jda.decompilers.bytecode.*;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class IRDecompiler extends BytecodeDecompiler implements MapleComponent {
    @Override
    protected MethodNodeDecompiler getMethodNodeDecompiler(PrefixedStringBuilder sb, ClassNode cn, Iterator<MethodNode> it) {
        return new IRMethodDecompiler(this, sb, it.next(), cn);
    }

    @Override
    public String getName() {
        return "MapleIR";
    }
}

class IRMethodDecompiler extends MethodNodeDecompiler {
    public IRMethodDecompiler(BytecodeDecompiler parent, PrefixedStringBuilder sb, MethodNode mn, ClassNode cn) {
        super(parent, sb, mn, cn);
    }

    @Override
    protected InstructionPrinter getInstructionPrinter(MethodNode m, TypeAndName[] args) {
        return new IRInstructionPrinter(this, m, args);
    }
}

class IRInstructionPrinter extends InstructionPrinter {
    public IRInstructionPrinter(MethodNodeDecompiler parent, MethodNode m, TypeAndName[] args) {
        super(parent, m, args);
    }

    @Override
    public ArrayList<String> createPrint() {
        final JSRInlinerAdapter adapter = new JSRInlinerAdapter(mNode, mNode.access, mNode.name, mNode.desc, mNode.signature, mNode.exceptions.toArray(new String[0]));
        adapter.owner = mNode.owner;
        mNode.accept(adapter);
        ControlFlowGraph cfg = ControlFlowGraphBuilder.build(adapter);
        BoissinotDestructor.leaveSSA(cfg);
        LocalsReallocator.realloc(cfg);
        String result = cfg.toString();
        return new ArrayList<>(Arrays.asList(result.split("\n")));
    }
}

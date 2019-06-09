package org.mapleir.jdaplugin;

import club.bytecode.the.jda.FileContainer;
import club.bytecode.the.jda.api.JDANamespace;
import club.bytecode.the.jda.decompilers.JDADecompiler;
import org.mapleir.ir.algorithms.BoissinotDestructor;
import org.mapleir.ir.algorithms.LocalsReallocator;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.printer.ClassPrinter;
import org.mapleir.ir.printer.FieldNodePrinter;
import org.mapleir.ir.printer.MethodNodePrinter;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.util.PropertyHelper;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ILDecompiler extends JDADecompiler implements MapleComponent {
    @Override
    public String decompileClassNode(FileContainer container, ClassNode cn) {
        TabbedStringWriter sw = new TabbedStringWriter();
        sw.setTabString("  ");
        IPropertyDictionary settings = PropertyHelper.createDictionary();
        final FieldNodePrinter fieldPrinter = new FieldNodePrinter(sw, settings);
        final MethodNodePrinter methodPrinter = new MethodNodePrinter(sw, settings) {
            @Override
            protected ControlFlowGraph getCfg(MethodNode mn) {
                final JSRInlinerAdapter adapter = new JSRInlinerAdapter(mn, mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.toArray(new String[0]));
                mn.accept(adapter);
                ControlFlowGraph cfg = ControlFlowGraphBuilder.build(new org.mapleir.asm.MethodNode(adapter, new org.mapleir.asm.ClassNode()));
                BoissinotDestructor.leaveSSA(cfg);
                LocalsReallocator.realloc(cfg);
                return cfg;
            }
        };
        ClassPrinter cp = new ClassPrinter(sw, settings, fieldPrinter, methodPrinter);
        cp.print(cn);
        return sw.toString();
    }

    @Override
    public String getName() {
        return "MapleIL";
    }

    @Override
    public JDANamespace getNamespace() {
        return MaplePlugin.getInstance().getNamespace();
    }
}

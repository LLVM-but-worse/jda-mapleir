package org.mapleir.jdaplugin;

import club.bytecode.the.jda.util.BytecodeUtils;
import org.mapleir.asm.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ASMAdaptor {
    public static org.mapleir.asm.MethodNode wrapMethodNode(MethodNode asmMn) {
        return new org.mapleir.asm.MethodNode(BytecodeUtils.applyJsrInlineAdapter(asmMn), new ClassNode());
    }
}

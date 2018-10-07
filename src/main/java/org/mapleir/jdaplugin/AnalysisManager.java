package org.mapleir.jdaplugin;

import club.bytecode.the.jda.FileContainer;
import club.bytecode.the.jda.JDA;
import club.bytecode.the.jda.gui.fileviewer.ViewerFile;
import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.deob.dataflow.LiveDataFlowAnalysisImpl;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.stream.Collectors;

public class AnalysisManager {
    public final Map<FileContainer, AnalysisContext> cxts = new HashMap<>();

    public void load(FileContainer fileContainer) {
        Set<ClassNode> classes = new HashSet<>();
        for (String file : fileContainer.getFiles().keySet()) {
            if (!file.endsWith(".class"))
                continue;
            try {
                classes.add(fileContainer.loadClassFile(file));
            } catch(Exception e) {
                System.err.println("[MapleIR] Failed to load class " + file + ":");
                e.printStackTrace();
            }
        }
        System.out.printf("[MapleIR] Loaded %d classes\n", classes.size());

        ApplicationClassSource app = new ApplicationClassSource(fileContainer.name, classes);

        IRCache irFactory = new IRCache(ControlFlowGraphBuilder::build);
        AnalysisContext newCxt = new BasicAnalysisContext.BasicContextBuilder()
                .setApplication(app)
                // .setInvocationResolver(new DefaultInvocationResolver(app))
                .setCache(irFactory)
                .setApplicationContext(new SimpleApplicationContext(app))
                .setDataFlowAnalysis(new LiveDataFlowAnalysisImpl(irFactory))
                .build();

        for (ClassNode cn : newCxt.getApplication().iterate()) {
            for (MethodNode m : cn.methods) {
                try {
                    newCxt.getIRCache().getFor(m);
                } catch(Exception e) {
                    System.err.println("[MapleIR] Failed to build IR for " + m.getJavaDesc() + ":");
                    e.printStackTrace();
                }
            }
        }
        System.out.printf("[MapleIR] Computed %d cfgs\n", newCxt.getIRCache().size());

        // when we get around to it, do tracing, IPA stuff here.
        cxts.put(fileContainer, newCxt);
    }

    public void unload(FileContainer fc) {
        cxts.remove(fc);
    }

    public List<ViewerFile> search(String needle) {
        List<ViewerFile> matches = new ArrayList<>();
        for (FileContainer fc : JDA.getOpenFiles()) {
            AnalysisContext cxt = cxts.get(fc);
            matches.addAll(cxt.getDataflowAnalysis().enumerateConstants()
                    .filter(Objects::nonNull)
                    .filter(constantExpr -> String.valueOf(constantExpr.getConstant()).contains(needle))
                    .map(constantExpr -> new ViewerFile(fc, constantExpr.getRootParent().getBlock().getGraph().getOwner() + ".class"))
                    .collect(Collectors.toList()));
        }
        return matches;
    }
}

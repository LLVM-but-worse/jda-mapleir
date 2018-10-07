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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static club.bytecode.the.jda.util.GuiUtils.sleep;

public class AnalysisManager {
    public final Map<FileContainer, AnalysisContext> cxts = new HashMap<>();

    private final AtomicInteger queuedAnalysisItems = new AtomicInteger(0);
    private final Map<FileContainer, Thread> analysisJobs = new HashMap<>();

    public void load(FileContainer fileContainer) {
        Thread analysisThread = new Thread(() -> analyzeBinaryThread(fileContainer));
        analysisJobs.put(fileContainer, analysisThread);
        analysisThread.start();
        System.out.println("[MapleIR] " + fileContainer + " analyzing in background");
    }

    private void analyzeBinaryThread(FileContainer fileContainer) {
        queuedAnalysisItems.incrementAndGet();
        JDA.setBusy(true);
        AnalysisContext newCxt = analyzeBinary(fileContainer);
        try {
            cxts.put(fileContainer, newCxt);
        } finally {
            JDA.setBusy(false);
            queuedAnalysisItems.decrementAndGet();
            analysisJobs.remove(fileContainer);
        }
    }

    private AnalysisContext analyzeBinary(FileContainer fileContainer) {
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
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("[MapleIR] Analysis interrupted");
                return null;
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
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("[MapleIR] Analysis interrupted");
                    return null;
                }
            }
        }
        System.out.printf("[MapleIR] Computed %d cfgs\n", newCxt.getIRCache().size());
        return newCxt;
    }

    public void unload(FileContainer fc) {
        stopAnalysis(fc);
        cxts.remove(fc);
    }

    private void stopAnalysis(FileContainer fc) {
        analysisJobs.get(fc).interrupt();
        for (int i = 0; i < 100 && analysisJobs.get(fc) != null; i++) {
            sleep(10L);
        }
        if (analysisJobs.get(fc) != null) { // apply violence
            analysisJobs.get(fc).stop();
        }
    }

    public boolean isAnalysisComplete() {
        return queuedAnalysisItems.get() == 0;
    }

    public List<ViewerFile> search(String needle) {
        List<ViewerFile> matches = new ArrayList<>();
        for (FileContainer fc : JDA.getOpenFiles()) {
            AnalysisContext cxt = cxts.get(fc);
            if (cxt == null) // incomplete analysis
                continue;
            matches.addAll(cxt.getDataflowAnalysis().enumerateConstants()
                    .filter(Objects::nonNull)
                    .filter(constantExpr -> String.valueOf(constantExpr.getConstant()).contains(needle))
                    .map(constantExpr -> new ViewerFile(fc, constantExpr.getRootParent().getBlock().getGraph().getOwner() + ".class"))
                    .collect(Collectors.toList()));
        }
        return matches;
    }
}

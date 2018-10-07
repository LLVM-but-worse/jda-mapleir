package org.mapleir.jdaplugin;

import club.bytecode.the.jda.FileContainer;
import club.bytecode.the.jda.JDA;
import club.bytecode.the.jda.api.JDAPlugin;
import club.bytecode.the.jda.api.JDAPluginNamespace;
import club.bytecode.the.jda.decompilers.Decompilers;
import club.bytecode.the.jda.decompilers.filter.DecompileFilters;
import org.mapleir.DefaultInvocationResolver;
import org.mapleir.app.client.SimpleApplicationContext;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.context.AnalysisContext;
import org.mapleir.context.BasicAnalysisContext;
import org.mapleir.context.IRCache;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.jdaplugin.gui.AboutDialog;
import org.mapleir.jdaplugin.gui.GuiIntegration;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class MaplePlugin implements JDAPlugin {
    private static MaplePlugin instance;
    private static GuiIntegration guiIntegration;

    public final AnalysisManager analysisEngine;

    public final JDAPluginNamespace namespace = new JDAPluginNamespace(this);

    public MaplePlugin() {
        instance = this;
        analysisEngine = new AnalysisManager();
    }

    public static void main(String[] args) {
        throw new NotImplementedException();
    }

    public static MaplePlugin getInstance() {
        return instance;
    }

    @Override
    public String getName() {
        return "MapleIR";
    }

    @Override
    public JDAPluginNamespace getNamespace() {
        return namespace;
    }

    @Override
    public void onLoad() {
        Decompilers.registerDecompiler(new IRDecompiler());
        Decompilers.registerDecompiler(new ILDecompiler());
        DecompileFilters.registerFilter(new DeobfuscateFilter());
        System.out.println("MapleIR decompilers registered");
    }

    @Override
    public void onUnload() {

    }

    @Override
    public void onGUILoad() {
        guiIntegration = new GuiIntegration(JDA.viewer);
        System.out.println("MapleIR plugin loaded");
    }

    @Override
    public void onExit() {
    }

    @Override
    public void onOpenFile(FileContainer fileContainer) {
        analysisEngine.load(fileContainer);
    }

    @Override
    public void onCloseFile(FileContainer fc) {
        analysisEngine.unload(fc);
    }

    @Override
    public void onPluginButton() {
        new AboutDialog().show();
    }
}

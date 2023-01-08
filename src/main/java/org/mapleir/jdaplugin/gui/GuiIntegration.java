package org.mapleir.jdaplugin.gui;

import club.bytecode.the.jda.JDA;
import club.bytecode.the.jda.gui.MainViewerGUI;
import club.bytecode.the.jda.gui.fileviewer.ViewerFile;
import club.bytecode.the.jda.gui.search.SearchDialog;
import org.mapleir.jdaplugin.MaplePlugin;
import org.mapleir.stdlib.util.JavaDesc;

import javax.swing.*;
import java.util.List;

public class GuiIntegration {
    private final MainViewerGUI gui;

    public GuiIntegration(MainViewerGUI mainViewerGUI) {
        gui = mainViewerGUI;

        addSearchHooks();
    }

    private void addSearchHooks() {
        JMenu searchMenu = gui.searchMenu;
        JMenuItem javaConstantButton = new JMenuItem("Java constant");
        javaConstantButton.addActionListener((e) -> doJavaSearchConstantDialog());
        searchMenu.add(javaConstantButton);
        gui.searchMenu.add(javaConstantButton);
    }

    private void checkwarnIncompleteAnalysis() {
        if (!MaplePlugin.getInstance().analysisEngine.isAnalysisComplete()) {
            JOptionPane.showMessageDialog(gui, "Analysis is't complete yet, results may be inaccurate", "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private List<ViewerFile> doSearchMethod(String methodName) {
        checkwarnIncompleteAnalysis();
        return MaplePlugin.getInstance().analysisEngine.search(methodName, JavaDesc.DescType.METHOD);
    }

    private List<ViewerFile> doSearchConstant(String methodName) {
        checkwarnIncompleteAnalysis();
        return MaplePlugin.getInstance().analysisEngine.searchConstant(methodName);
    }

    private void doJavaSearchConstantDialog() {
        checkwarnIncompleteAnalysis();
        String constant = JOptionPane.showInputDialog("Enter a constant...");
        if (constant == null || constant.isEmpty()) {
            return;
        }
        new SearchDialog(constant, MaplePlugin.getInstance().analysisEngine.searchConstant(constant)).setVisible(true);
    }
}

package org.mapleir.jdaplugin.gui;

import club.bytecode.the.jda.FileContainer;
import club.bytecode.the.jda.JDA;
import club.bytecode.the.jda.api.JDAPlugin;
import club.bytecode.the.jda.gui.MainViewerGUI;
import club.bytecode.the.jda.gui.fileviewer.ViewerFile;
import club.bytecode.the.jda.gui.search.SearchDialog;
import org.mapleir.jdaplugin.MaplePlugin;

import javax.swing.*;
import java.util.ArrayList;
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
        javaConstantButton.addActionListener((e) -> doJavaSearchDialog());
        searchMenu.add(javaConstantButton);
        gui.searchMenu.add(javaConstantButton);
    }

    public void doJavaSearchDialog() {
        String constant = JOptionPane.showInputDialog("Enter a constant...");
        if (constant == null || constant.isEmpty()) {
            return;
        }
        new SearchDialog(constant, MaplePlugin.getInstance().analysisEngine.search(constant)).setVisible(true);
    }
}

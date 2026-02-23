package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Represents the GlobalShortcuts component in the nonprofit bookkeeping application.
 */
public final class GlobalShortcuts
{
    private GlobalShortcuts() {}

    public static void install(Scene scene, MainWindow window)
    {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), window::saveActivePanel);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), window::openSearch);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), window::newItemInActivePanel);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN), window::copySelection);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN), window::paste);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), window::closeInspector);
    }
}

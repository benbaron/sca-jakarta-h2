package org.nonprofitbookkeeping.ui;

import javafx.application.Application;

/**
 * JavaFX entry point. Run with:
 *   mvn -q -Pui javafx:run
 */
public class FxMain
{
    public static void main(String[] args)
    {
        launch(args, System.getProperties(), launchArgs -> Application.launch(MainApp.class, launchArgs));
    }

    static void launch(String[] args, java.util.Properties properties,
                       java.util.function.Consumer<String[]> applicationLauncher)
    {
        if (JavaFxRenderingPolicy.configure(properties))
        {
            System.out.println("[NPBK] Windows rendering default: prism.order=sw (software).");
        }
        applicationLauncher.accept(args);
    }
}

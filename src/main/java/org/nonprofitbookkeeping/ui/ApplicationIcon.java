package org.nonprofitbookkeeping.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.awt.Taskbar;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Installs application branding while leaving placement to the native operating system. */
final class ApplicationIcon
{
    private static final int[] SIZES = {16, 32, 48, 64, 128, 256, 512};

    private ApplicationIcon()
    {
    }

    static void install(Stage stage)
    {
        var images = new ArrayList<Image>();
        for (int size : SIZES)
        {
            try (InputStream input = resource(size))
            {
                Image image = new Image(input);
                if (image.isError())
                {
                    throw new IllegalStateException("Cannot decode application icon: " + size, image.getException());
                }
                images.add(image);
            }
            catch (IOException ex)
            {
                throw new IllegalStateException("Cannot load application icon: " + size, ex);
            }
        }
        stage.getIcons().setAll(images);
        installProcessIcon();
    }

    private static void installProcessIcon()
    {
        try
        {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return;
            try (InputStream input = resource(512))
            {
                taskbar.setIconImage(ImageIO.read(input));
            }
        }
        catch (IOException | UnsupportedOperationException | SecurityException ex)
        {
            System.getLogger(ApplicationIcon.class.getName()).log(System.Logger.Level.WARNING,
                    "Native process icon unavailable; JavaFX window icons remain installed.", ex);
        }
    }

    private static InputStream resource(int size)
    {
        String path = "/ui/icons/application-" + size + ".png";
        return Objects.requireNonNull(ApplicationIcon.class.getResourceAsStream(path),
                "Missing application icon: " + path);
    }
}

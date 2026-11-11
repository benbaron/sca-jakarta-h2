package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationIconTest
{
    @Test
    void packagedArtworkHasNativeSizesTransparencyAndLegibleBrandColors() throws Exception
    {
        for (int size : new int[] {16, 32, 48, 64, 128, 256, 512})
        {
            try (var input = getClass().getResourceAsStream("/ui/icons/application-" + size + ".png"))
            {
                assertNotNull(input, "Packaged icon " + size);
                var image = ImageIO.read(input);
                assertNotNull(image);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
                assertEquals(0, image.getRGB(0, 0) >>> 24);
                int blue = 0, white = 0, gold = 0;
                for (int y = 0; y < size; y++)
                {
                    for (int x = 0; x < size; x++)
                    {
                        int pixel = image.getRGB(x, y);
                        if ((pixel >>> 24) < 220) continue;
                        int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
                        if (b > r * 1.5 && b > g) blue++;
                        if (r > 230 && g > 230 && b > 230) white++;
                        if (r > 150 && g > 80 && r > b * 2 && g > b * 2) gold++;
                    }
                }
                assertTrue(blue > size && white > size && gold > 0,
                        "Ledger artwork must remain visible at " + size + "px");
            }
        }
    }
}

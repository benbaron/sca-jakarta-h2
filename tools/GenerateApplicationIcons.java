import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Editable vector artwork. Run: java tools/GenerateApplicationIcons.java [output-directory] */
public final class GenerateApplicationIcons
{
    public static void main(String[] args) throws Exception
    {
        Path directory = Path.of(args.length == 0 ? "src/main/resources/ui/icons" : args[0]);
        Files.createDirectories(directory);
        for (int size : new int[] {16, 32, 48, 64, 128, 256, 512})
        {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.scale(size / 256.0, size / 256.0);
                g.setColor(new Color(0x1456A0));
                g.fillRoundRect(8, 8, 240, 240, 52, 52);
                g.setColor(new Color(0x0A3263));
                g.fillRoundRect(35, 65, 186, 144, 14, 14);
                Path2D pages = new Path2D.Double();
                pages.moveTo(40, 54);
                pages.curveTo(73, 45, 102, 48, 128, 63);
                pages.curveTo(154, 48, 183, 45, 216, 54);
                pages.lineTo(216, 190);
                pages.curveTo(180, 181, 153, 186, 128, 202);
                pages.curveTo(103, 186, 76, 181, 40, 190);
                pages.closePath();
                g.setColor(Color.WHITE);
                g.fill(pages);
                g.setColor(new Color(0xC1D6EB));
                g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(128, 67, 128, 184);
                g.setColor(new Color(0x51749B));
                g.drawLine(59, 83, 107, 89);
                g.drawLine(59, 111, 107, 117);
                g.drawLine(59, 139, 107, 145);
                g.setColor(new Color(0xD99A13));
                g.fillRoundRect(146, 132, 14, 38, 3, 3);
                g.fillRoundRect(168, 111, 14, 59, 3, 3);
                g.fillRoundRect(190, 86, 14, 84, 3, 3);
            }
            finally
            {
                g.dispose();
            }
            if (!ImageIO.write(image, "png", directory.resolve("application-" + size + ".png").toFile()))
            {
                throw new IllegalStateException("PNG encoder unavailable");
            }
        }
    }
}

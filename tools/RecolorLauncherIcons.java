import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Derives launcher palettes from the original pixels, without resampling or redrawing. */
public final class RecolorLauncherIcons {
    private static final Path RESOURCES = Path.of("app/src/main/res/drawable-nodpi");

    public static void main(String[] args) throws IOException {
        BufferedImage original = ImageIO.read(RESOURCES.resolve("ic_launcher_fanfan.png").toFile());
        if (original == null) throw new IOException("Original launcher icon is unreadable");
        write(original, "blue", 0, 0.35, false);
        write(original, "pink", 125.0 / 360, 0.62, false);
        write(original, "purple", 55.0 / 360, 0.60, false);
        write(original, "green", -40.0 / 360, 0.58, false);
        write(original, "black", 0, 0, true);
    }

    private static void write(BufferedImage original, String name, double hueOffset, double whiteMix, boolean grayscale)
            throws IOException {
        BufferedImage result = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int pixel = original.getRGB(x, y);
                result.setRGB(x, y, recolor(pixel, hueOffset, whiteMix, grayscale));
            }
        }
        Path destination = RESOURCES.resolve("ic_launcher_fanfan_" + name + ".png");
        ImageIO.write(result, "png", destination.toFile());
        BufferedImage saved = ImageIO.read(destination.toFile());
        verify(original, saved, hueOffset, whiteMix, grayscale);
        System.out.println("Verified original dimensions, alpha, neutral pixels and color-only transform: " + destination
                + " (" + Files.size(destination) + " bytes)");
    }

    private static int recolor(int pixel, double hueOffset, double whiteMix, boolean grayscale) {
        int r8 = (pixel >> 16) & 255, g8 = (pixel >> 8) & 255, b8 = pixel & 255;
        // Preserve transparent pixels and the original white/gray background byte for byte.
        if ((pixel >>> 24) == 0 || (r8 == g8 && g8 == b8)) return pixel;
        double r = r8 / 255.0, g = g8 / 255.0, b = b8 / 255.0;
        if (grayscale) {
            // Luminance keeps the wave layers visible; HSV desaturation would wash them out.
            int gray = (int) Math.round(0.2126 * r8 + 0.7152 * g8 + 0.0722 * b8);
            return (pixel & 0xff000000) | (gray << 16) | (gray << 8) | gray;
        }
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double chroma = max - min;
        double hue = max == r ? ((g - b) / chroma) : max == g ? ((b - r) / chroma + 2) : ((r - g) / chroma + 4);
        hue = ((hue / 6 + hueOffset) % 1 + 1) % 1;
        double sector = hue * 6;
        double intermediate = chroma * (1 - Math.abs(sector % 2 - 1));
        double nr, ng, nb;
        if (sector < 1) { nr = chroma; ng = intermediate; nb = 0; }
        else if (sector < 2) { nr = intermediate; ng = chroma; nb = 0; }
        else if (sector < 3) { nr = 0; ng = chroma; nb = intermediate; }
        else if (sector < 4) { nr = 0; ng = intermediate; nb = chroma; }
        else if (sector < 5) { nr = intermediate; ng = 0; nb = chroma; }
        else { nr = chroma; ng = 0; nb = intermediate; }
        // A uniform white blend softens the palette while preserving the wave gradients and geometry.
        return (pixel & 0xff000000) | (channel((nr + min) * (1 - whiteMix) + whiteMix) << 16)
                | (channel((ng + min) * (1 - whiteMix) + whiteMix) << 8)
                | channel((nb + min) * (1 - whiteMix) + whiteMix);
    }

    private static int channel(double value) { return (int) Math.round(value * 255); }

    private static void verify(BufferedImage original, BufferedImage result, double hueOffset, double whiteMix, boolean grayscale) {
        if (result.getWidth() != original.getWidth() || result.getHeight() != original.getHeight()) {
            throw new IllegalStateException("Icon dimensions changed");
        }
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int before = original.getRGB(x, y), after = result.getRGB(x, y);
                if ((before >>> 24) != (after >>> 24) || after != recolor(before, hueOffset, whiteMix, grayscale)) {
                    throw new IllegalStateException("Unexpected pixel change at " + x + "," + y);
                }
                if (!grayscale) {
                    int[] a = {(before >> 16) & 255, (before >> 8) & 255, before & 255};
                    int[] b = {(after >> 16) & 255, (after >> 8) & 255, after & 255};
                    java.util.Arrays.sort(a);
                    java.util.Arrays.sort(b);
                    boolean preserved = a[0] == a[2] || (before >>> 24) == 0;
                    int expectedMin = preserved ? a[0] : channel(a[0] / 255.0 * (1 - whiteMix) + whiteMix);
                    int expectedMax = preserved ? a[2] : channel(a[2] / 255.0 * (1 - whiteMix) + whiteMix);
                    // RGB quantization can round a hue-sector boundary by one channel value.
                    if (Math.abs(expectedMin - b[0]) > 1 || Math.abs(expectedMax - b[2]) > 1) {
                        throw new IllegalStateException("Unexpected tonal change");
                    }
                }
            }
        }
    }
}

package org.vivecraft.gametest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.helpers.graphics.GraphicsHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * takes screenshots of the actual VR eye images, the fabric screenshots render a separate vanilla frame instead.
 * unlike vanilla screenshots this keeps the alpha channel, which shows where passthrough would be visible
 */
public class VRScreenshot {

    /**
     * result of an eye screenshot
     *
     * @param path               path to the checkerboard image
     * @param transparentPixels  number of pixels with alpha 0
     * @param glowingPixels      number of pixels with alpha 0 but a color, those would add light over the passthrough
     * @param topCenterAlpha     alpha of the pixel at the top center, which is usually sky
     * @param blackPixels        number of opaque black pixels, like the overlay when the head is inside a block
     * @param totalPixels        number of pixels in the image
     */
    public record EyeImage(Path path, int transparentPixels, int glowingPixels, int topCenterAlpha, int blackPixels,
                           int totalPixels)
    {
        /**
         * @return how much of the image is opaque black, from 0 to 1
         */
        public double blackFraction() {
            return (double) this.blackPixels / this.totalPixels;
        }
    }

    /**
     * saves the left eye image as {@code screenshots/vr_<name>_rgba.png}, and a copy where transparent parts are
     * shown as a checkerboard as {@code screenshots/vr_<name>.png}
     *
     * @return info about the transparency of the image
     */
    public static EyeImage takeLeftEye(ClientGameTestContext context, String name) {
        // let a few VR frames render
        context.waitTicks(2);
        CompletableFuture<EyeImage> future = context.computeOnClient(
            mc -> readBack(ClientDataHolderVR.getInstance().vrRenderer.framebufferEye[0], name));
        while (!future.isDone()) {
            context.waitTick();
        }
        return future.join();
    }

    private static CompletableFuture<EyeImage> readBack(RenderTarget target, String name) {
        int width = target.width;
        int height = target.height;
        GpuTexture texture = target.getColorTexture();
        int pixelSize = texture.getFormat().blockSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Vivecraft test eye readback",
            GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * pixelSize);

        // OpenGL images are stored bottom up, vulkan ones top down
        boolean bottomUp = !GraphicsHelper.INSTANCE.flipEyeVertically();
        CompletableFuture<EyeImage> result = new CompletableFuture<>();
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView read = buffer.map(true, false);
                 NativeImage rgba = new NativeImage(width, height, false);
                 NativeImage checker = new NativeImage(width, height, false))
            {
                int transparent = 0;
                int glowing = 0;
                int black = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int abgr = read.data().getInt((x + y * width) * pixelSize);
                        int row = bottomUp ? height - y - 1 : y;
                        rgba.setPixelABGR(x, row, abgr);
                        if ((abgr >>> 24) == 0xFF && (abgr & 0xF8F8F8) == 0) {
                            black++;
                        }
                        if ((abgr >>> 24) == 0) {
                            transparent++;
                            if ((abgr & 0xFFFFFF) != 0) {
                                glowing++;
                            }
                        }
                        checker.setPixelABGR(x, row, overCheckerboard(abgr, x, row));
                    }
                }
                Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
                Files.createDirectories(dir);
                rgba.writeToFile(dir.resolve("vr_" + name + "_rgba.png"));
                Path checkerPath = dir.resolve("vr_" + name + ".png");
                checker.writeToFile(checkerPath);
                int topCenterAlpha = (rgba.getPixel(width / 2, 0) >>> 24) & 0xFF;
                result.complete(
                    new EyeImage(checkerPath, transparent, glowing, topCenterAlpha, black, width * height));
            } catch (IOException e) {
                result.completeExceptionally(new UncheckedIOException(e));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            } finally {
                buffer.close();
            }
        }, 0);
        return result;
    }

    /**
     * composites a premultiplied ABGR pixel over a grey checkerboard
     */
    private static int overCheckerboard(int abgr, int x, int y) {
        int checker = ((x / 32 + y / 32) % 2 == 0) ? 204 : 128;
        int alpha = (abgr >>> 24) & 0xFF;
        int inverse = 255 - alpha;
        int r = Math.min(255, (abgr & 0xFF) + checker * inverse / 255);
        int g = Math.min(255, ((abgr >>> 8) & 0xFF) + checker * inverse / 255);
        int b = Math.min(255, ((abgr >>> 16) & 0xFF) + checker * inverse / 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}

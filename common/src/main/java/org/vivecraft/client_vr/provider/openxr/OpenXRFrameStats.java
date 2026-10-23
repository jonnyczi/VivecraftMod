package org.vivecraft.client_vr.provider.openxr;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.tabletop.TabletopManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * debug frame timing for OpenXR, logs averages every few seconds to find where frame time is spent
 */
public class OpenXRFrameStats {
    private static final long LOG_INTERVAL_NS = 5_000_000_000L;

    private static long lastFrameStart;
    private static long intervalStart;
    private static int frames;
    private static long frameTimeTotal;
    private static long frameTimeMax;
    private static long waitFrameTotal;
    private static long endFrameTotal;
    private static long swapchainWaitTotal;

    // debug profiling, started when this file exists, the launcher strips jvm arguments
    private static final Path PROFILE_TRIGGER = Path.of("/tmp/vivecraft-probe/profile.enable");
    private static final Path PROFILE_OUTPUT = Path.of("/tmp/vivecraft-probe/vivecraft.jfr");
    private static final long PROFILE_DUMP_INTERVAL_NS = 30_000_000_000L;
    private static Recording RECORDING;
    private static boolean PROFILE_CHECKED;
    private static long LAST_PROFILE_DUMP;

    /**
     * starts a flight recording if requested, and saves a snapshot of it every 30 seconds,
     * so that it survives the game being killed
     */
    private static void updateProfiling(long now) {
        try {
            if (!PROFILE_CHECKED) {
                PROFILE_CHECKED = true;
                if (Files.exists(PROFILE_TRIGGER)) {
                    RECORDING = new Recording(Configuration.getConfiguration("profile"));
                    RECORDING.setMaxAge(Duration.ofMinutes(10));
                    RECORDING.setToDisk(true);
                    RECORDING.start();
                    LAST_PROFILE_DUMP = now;
                    VRSettings.LOGGER.info("Vivecraft: started flight recording");
                }
            } else if (RECORDING != null && now - LAST_PROFILE_DUMP > PROFILE_DUMP_INTERVAL_NS) {
                LAST_PROFILE_DUMP = now;
                RECORDING.dump(PROFILE_OUTPUT);
            }
        } catch (Exception e) {
            VRSettings.LOGGER.error("Vivecraft: flight recording failed", e);
            RECORDING = null;
        }
    }

    /**
     * @param waitNs time spent in xrWaitFrame
     */
    public static void frameStarted(long waitNs) {
        long now = System.nanoTime();
        updateProfiling(now);
        if (lastFrameStart != 0) {
            long frameTime = now - lastFrameStart;
            frameTimeTotal += frameTime;
            frameTimeMax = Math.max(frameTimeMax, frameTime);
            frames++;
        }
        lastFrameStart = now;
        waitFrameTotal += waitNs;

        if (intervalStart == 0) {
            intervalStart = now;
        } else if (now - intervalStart > LOG_INTERVAL_NS && frames > 0) {
            VRSettings.LOGGER.info(
                "Vivecraft frame stats: {} fps, frame avg {} ms max {} ms | xrWaitFrame {} ms | swapchain wait {} ms | endFrame total {} ms | cpu other {} ms | render scale {}, tabletop {}, mc fps {}",
                String.format("%.1f", frames * 1e9 / (now - intervalStart)),
                ms(frameTimeTotal / frames), ms(frameTimeMax),
                ms(waitFrameTotal / frames), ms(swapchainWaitTotal / frames), ms(endFrameTotal / frames),
                ms((frameTimeTotal - waitFrameTotal - endFrameTotal) / frames),
                ClientDataHolderVR.getInstance().vrSettings.renderScaleFactor, TabletopManager.isActive(),
                Minecraft.getInstance().getFps());
            intervalStart = now;
            frames = 0;
            frameTimeTotal = 0;
            frameTimeMax = 0;
            waitFrameTotal = 0;
            endFrameTotal = 0;
            swapchainWaitTotal = 0;
        }
    }

    /**
     * @param totalNs     time spent in the whole endFrame
     * @param swapchainNs time spent acquiring and waiting for the swapchain image
     */
    public static void frameEnded(long totalNs, long swapchainNs) {
        endFrameTotal += totalNs;
        swapchainWaitTotal += swapchainNs;
    }

    private static String ms(long ns) {
        return String.format("%.2f", ns / 1e6);
    }
}

package org.vivecraft.client_vr.provider;

import org.vivecraft.client_vr.provider.control.ControllerType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class HapticScheduler {
    // a daemon thread, otherwise the game can't exit after the first haptic pulse
    protected ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Vivecraft Haptics");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * schedules a haptic pulse on the give controller at the given delay
     *
     * @param controller      controller to trigger on
     * @param durationSeconds duration in seconds
     * @param frequency       frequency in Hz
     * @param amplitude       strength 0.0 - 1.0
     * @param delaySeconds    delay for when to trigger in seconds
     */
    public abstract void queueHapticPulse(
        ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds);
}

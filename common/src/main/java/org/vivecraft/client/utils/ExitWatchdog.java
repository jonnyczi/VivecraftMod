package org.vivecraft.client.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Map;

/**
 * writes the stack of every thread to a file, when the game doesn't exit some time after it started closing.
 * this helps to find out what keeps the game from closing, since the logger is already shut down at that point
 */
public class ExitWatchdog {

    private static final long TIMEOUT_MS = 15000L;
    private static boolean STARTED = false;

    /**
     * starts the watchdog, does nothing if it is already running
     *
     * @param logDir folder to write the thread dump to
     */
    public static synchronized void start(Path logDir) {
        if (STARTED) {
            return;
        }
        STARTED = true;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_MS);
            } catch (InterruptedException e) {
                return;
            }
            dumpThreads(logDir.resolve("vivecraft-exit-hang.txt"));
        }, "Vivecraft Exit Watchdog");
        // doesn't keep the game alive by itself
        thread.setDaemon(true);
        thread.start();
    }

    private static void dumpThreads(Path file) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            writer.println("Game still running " + TIMEOUT_MS / 1000 + "s after it started closing, at " +
                LocalTime.now());
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                writer.println();
                writer.println("\"" + thread.getName() + "\" daemon=" + thread.isDaemon() + " state=" +
                    thread.getState());
                for (StackTraceElement element : entry.getValue()) {
                    writer.println("    at " + element);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

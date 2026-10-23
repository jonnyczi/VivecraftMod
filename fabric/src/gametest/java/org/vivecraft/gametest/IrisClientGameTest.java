package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * tabletop mode with an iris shader pack, only runs when iris is installed, with -PgametestIris
 */
public class IrisClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!IrisHelper.isLoaded()) {
            LOGGER.info("Vivecraft gametest: iris isn't installed, skipping the iris test");
            return;
        }
        VRTestHelper.startNullVR(context);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runCommand("time set noon");
            VRTestHelper.waitForWorld(context, singleplayer);

            context.runOnClient(mc -> selectTestShaderPack());
            context.runOnClient(mc -> IrisHelper.setShadersActive(true));
            context.waitFor(mc -> IrisHelper.isShaderActive(), 200);
            context.waitTicks(20);
            VRScreenshot.takeLeftEye(context, "iris_vr");

            // the shaders that cut the world at the tabletop edges don't work with a shader pack, so tabletop mode
            // isn't entered, and the view mode goes back to VR
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);
            context.waitTicks(5);
            check(!context.computeOnClient(mc -> TabletopManager.isActive()),
                "tabletop mode shouldn't start with a shader pack");
            check(context.computeOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode) == ViewMode.VR,
                "the view mode should go back to VR with a shader pack");

            // turning shaders on in tabletop mode leaves it, without rendering a frame of tabletop with the shaders
            context.runOnClient(mc -> IrisHelper.setShadersActive(false));
            context.waitFor(mc -> !IrisHelper.isShaderActive(), 200);
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            context.waitTicks(5);
            context.runOnClient(mc -> IrisHelper.setShadersActive(true));
            context.waitFor(mc -> IrisHelper.isShaderActive(), 200);
            context.waitTicks(20);
            check(!context.computeOnClient(mc -> TabletopManager.isActive()),
                "tabletop mode should end when a shader pack is turned on");
            VRScreenshot.takeLeftEye(context, "iris_after_tabletop");

            context.runOnClient(mc -> IrisHelper.setShadersActive(false));
            context.waitFor(mc -> !IrisHelper.isShaderActive(), 200);
        }

        VRTestHelper.stopVR(context);
    }

    /**
     * an empty shader pack, iris then renders everything with its own default programs.
     * iris isn't on the compile classpath of the tests, so this goes through reflection
     */
    private static void selectTestShaderPack() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Path packs = (Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
            Path shaders = packs.resolve("vivecraft-test").resolve("shaders");
            Files.createDirectories(shaders);
            Files.writeString(shaders.resolve("shaders.properties"), "# empty shader pack for the vivecraft tests\n");
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            config.getClass().getMethod("setShaderPackName", String.class).invoke(config, "vivecraft-test");
        } catch (ReflectiveOperationException | IOException e) {
            throw new AssertionError("can't select the test shader pack", e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

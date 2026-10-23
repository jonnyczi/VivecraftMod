package org.vivecraft.mod_compat_vr.sodium;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SodiumTabletopClipTest {

    // minimal versions of sodium's chunk shaders, with the lines the patch looks for
    private static final String VERTEX = """
        #version 330 core
        out vec4 v_Color;
        out vec2 v_TexCoord;
        void main() {
            vec3 translation = u_RegionOffset;
            vec3 position = _vert_position + translation;
            gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);
        }
        """;
    private static final String FRAGMENT = """
        #version 330 core
        in vec4 v_Color;
        out vec4 fragColor;
        void main() {
            fragColor = v_Color;
        }
        """;

    private static final Identifier VERTEX_LOCATION = Identifier.fromNamespaceAndPath("sodium",
        "shaders/blocks/block_layer_opaque.vsh");
    private static final Identifier FRAGMENT_LOCATION = Identifier.fromNamespaceAndPath("sodium",
        "shaders/blocks/block_layer_opaque.fsh");

    @Test
    public void vertexPassesThePosition() {
        String patched = SodiumTabletopClip.patch(VERTEX_LOCATION, VERTEX, files(VERTEX, FRAGMENT));
        Assertions.assertTrue(patched.contains("out vec3 v_VivecraftClipPos;"));
        // the position needs to be written after it is calculated
        Assertions.assertTrue(patched.indexOf("v_VivecraftClipPos = position;") >
            patched.indexOf("vec3 position = _vert_position + translation;"));
        Assertions.assertTrue(SodiumTabletopClip.isPatched());
    }

    @Test
    public void fragmentDiscardsOutsideTheBox() {
        String patched = SodiumTabletopClip.patch(FRAGMENT_LOCATION, FRAGMENT, files(VERTEX, FRAGMENT));
        Assertions.assertTrue(patched.contains("uniform " + SodiumTabletopClip.UBO_NAME + " {"));
        Assertions.assertTrue(patched.contains("vec4 u_VivecraftClipMin;"));
        Assertions.assertTrue(patched.contains("vec4 u_VivecraftClipMax;"));
        Assertions.assertTrue(patched.contains("in vec3 v_VivecraftClipPos;"));
        // the discard needs to happen at the start of main
        Assertions.assertTrue(patched.indexOf("discard;") > patched.indexOf("void main() {"));
        Assertions.assertTrue(patched.indexOf("discard;") < patched.indexOf("fragColor = v_Color;"));
    }

    @Test
    public void bothStayUnchangedWhenOneIsUnknown() {
        // the fragment shader would need a vertex output that an unknown vertex shader doesn't have
        String unknown = "#version 330 core\nvoid main() {}\n";
        Map<Identifier, Resource> files = files(unknown, FRAGMENT);
        Assertions.assertEquals(unknown, SodiumTabletopClip.patch(VERTEX_LOCATION, unknown, files));
        Assertions.assertEquals(FRAGMENT, SodiumTabletopClip.patch(FRAGMENT_LOCATION, FRAGMENT, files));
        Assertions.assertFalse(SodiumTabletopClip.isPatched());
    }

    @Test
    public void otherShadersStayUnchanged() {
        Identifier other = Identifier.fromNamespaceAndPath("minecraft", "shaders/core/terrain.vsh");
        Assertions.assertEquals(VERTEX, SodiumTabletopClip.patch(other, VERTEX, files(VERTEX, FRAGMENT)));
    }

    private static Map<Identifier, Resource> files(String vertex, String fragment) {
        return Map.of(VERTEX_LOCATION, resource(vertex), FRAGMENT_LOCATION, resource(fragment));
    }

    private static Resource resource(String source) {
        return new Resource(null, () -> new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    }
}

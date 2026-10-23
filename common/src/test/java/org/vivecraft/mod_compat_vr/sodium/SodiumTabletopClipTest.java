package org.vivecraft.mod_compat_vr.sodium;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

    @Test
    public void vertexPassesThePosition() {
        String patched = SodiumTabletopClip.patchVertex(VERTEX);
        Assertions.assertTrue(patched.contains("out vec3 v_VivecraftClipPos;"));
        // the position needs to be written after it is calculated
        Assertions.assertTrue(patched.indexOf("v_VivecraftClipPos = position;") >
            patched.indexOf("vec3 position = _vert_position + translation;"));
    }

    @Test
    public void fragmentDiscardsOutsideTheBox() {
        String patched = SodiumTabletopClip.patchFragment(FRAGMENT);
        Assertions.assertTrue(patched.contains("uniform " + SodiumTabletopClip.UBO_NAME + " {"));
        Assertions.assertTrue(patched.contains("vec4 u_VivecraftClipMin;"));
        Assertions.assertTrue(patched.contains("vec4 u_VivecraftClipMax;"));
        Assertions.assertTrue(patched.contains("in vec3 v_VivecraftClipPos;"));
        // the discard needs to happen at the start of main
        Assertions.assertTrue(patched.indexOf("discard;") > patched.indexOf("void main() {"));
        Assertions.assertTrue(patched.indexOf("discard;") < patched.indexOf("fragColor = v_Color;"));
    }

    @Test
    public void unknownShadersStayUnchanged() {
        String unknown = "#version 330 core\nvoid main() {}\n";
        Assertions.assertEquals(unknown, SodiumTabletopClip.patchVertex(unknown));
        Assertions.assertEquals(unknown, SodiumTabletopClip.patchFragment(unknown));
    }

}

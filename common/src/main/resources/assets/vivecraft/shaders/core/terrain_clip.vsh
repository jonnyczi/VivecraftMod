#version 330

// copy of the vanilla terrain shader, that also passes the position on to clip the world to the tabletop frame

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

layout(std140) uniform VivecraftClip {
    // integer block the clip box is relative to, so that the clip test is precise anywhere in the world
    ivec4 ClipAnchor;
    vec4 ClipMin;
    vec4 ClipMax;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 clipPos;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    clipPos = Position + vec3(ChunkPosition - ClipAnchor.xyz);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}

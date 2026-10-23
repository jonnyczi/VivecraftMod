#version 330

uniform sampler2D DiffuseSampler;

in vec2 texCoordinates;

out vec4 fragColor;

// shows transparent parts of the image as a checkerboard, to see where passthrough would be visible
void main(){
    vec4 color = texture(DiffuseSampler, texCoordinates.st);
    ivec2 cell = ivec2(gl_FragCoord.xy) / 16;
    float checker = (cell.x + cell.y) % 2 == 0 ? 0.8 : 0.5;
    // the image has premultiplied alpha
    fragColor = vec4(color.rgb + vec3(checker) * (1.0 - color.a), 1.0);
}

#version 150

in vec4 Position;
in vec2 UV;

uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * Position;
    texCoord = UV;
}

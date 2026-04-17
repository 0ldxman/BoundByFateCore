#version 150

uniform sampler2D DiffuseSampler;

// Pixels darker than this threshold get desaturated (grayscale).
// 0.4 means pixels below 40% brightness become gray.
uniform float DarkvisionThreshold;

// Overall strength of the darkvision effect (0.0 = off, 1.0 = full).
uniform float DarkvisionStrength;

// How much to brighten dark pixels (simulates "seeing darkness as dim light").
// 1.0 = no boost, 3.0 = triple brightness for dark areas.
uniform float DarkvisionBrightness;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Perceptual luminance (ITU-R BT.709)
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Brightness boost for dark pixels — simulates seeing darkness as dim light.
    // Dark pixels get brightened, bright pixels are unaffected.
    float darkFactor = 1.0 - smoothstep(0.0, DarkvisionThreshold, luminance);
    vec3 brightened = color.rgb + color.rgb * darkFactor * (DarkvisionBrightness - 1.0);

    // Recalculate luminance after brightening
    float brightenedLuminance = dot(brightened, vec3(0.2126, 0.7152, 0.0722));

    // Grayscale version of the brightened pixel
    vec3 grayscale = vec3(brightenedLuminance);

    // Desaturate dark pixels — the darker the pixel, the more grayscale it becomes.
    float desatAmount = 1.0 - smoothstep(0.0, DarkvisionThreshold, brightenedLuminance);
    vec3 result = mix(brightened, grayscale, desatAmount * DarkvisionStrength);

    fragColor = vec4(result, color.a);
}

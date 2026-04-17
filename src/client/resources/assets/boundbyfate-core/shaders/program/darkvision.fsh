#version 150

uniform sampler2D DiffuseSampler;

// Pixels darker than this threshold get desaturated.
uniform float DarkvisionThreshold;

// Overall strength of the desaturation effect (0.0 = off, 1.0 = full grayscale for dark pixels).
uniform float DarkvisionStrength;

// Gamma exponent for brightness boost. Lower = brighter dark areas.
// 0.3 means darkness is raised to the power of 0.3 (strong boost).
// 1.0 = no boost.
uniform float DarkvisionGamma;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Apply gamma correction to boost dark areas non-linearly.
    // pow(x, gamma) where gamma < 1.0 brightens dark pixels strongly.
    vec3 boosted = pow(max(color.rgb, vec3(0.0001)), vec3(DarkvisionGamma));

    // Perceptual luminance of the boosted image
    float luminance = dot(boosted, vec3(0.2126, 0.7152, 0.0722));

    // Grayscale version
    vec3 grayscale = vec3(luminance);

    // Desaturate dark pixels — the darker the pixel, the more grayscale it becomes.
    float desatAmount = 1.0 - smoothstep(0.0, DarkvisionThreshold, luminance);
    vec3 result = mix(boosted, grayscale, desatAmount * DarkvisionStrength);

    fragColor = vec4(result, color.a);
}

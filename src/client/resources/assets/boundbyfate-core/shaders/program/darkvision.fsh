#version 150

uniform sampler2D DiffuseSampler;

// Pixels darker than this threshold get desaturated (grayscale).
uniform float DarkvisionThreshold;

// Overall strength of the desaturation effect (0.0 = off, 1.0 = full).
uniform float DarkvisionStrength;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Perceptual luminance (ITU-R BT.709)
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Grayscale version
    vec3 grayscale = vec3(luminance);

    // Dark pixels become grayscale, bright pixels stay colorful.
    // smoothstep gives a soft transition around the threshold.
    float desatAmount = 1.0 - smoothstep(0.0, DarkvisionThreshold, luminance);
    vec3 result = mix(color.rgb, grayscale, desatAmount * DarkvisionStrength);

    fragColor = vec4(result, color.a);
}

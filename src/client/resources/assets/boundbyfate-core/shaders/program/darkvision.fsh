#version 150

uniform sampler2D DiffuseSampler;

// How dark a pixel must be (0.0-1.0) before grayscale kicks in.
// 0.5 means pixels darker than 50% brightness become grayscale.
uniform float DarkvisionThreshold;

// Overall strength of the darkvision effect (0.0 = off, 1.0 = full).
// Used for smooth fade-in/out.
uniform float DarkvisionStrength;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Perceptual luminance (standard ITU-R BT.709 coefficients)
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Grayscale version of the pixel
    vec3 grayscale = vec3(luminance);

    // How much to desaturate: pixels below threshold get more grayscale.
    // smoothstep gives a soft transition around the threshold.
    float desatAmount = 1.0 - smoothstep(0.0, DarkvisionThreshold, luminance);

    // Mix original color with grayscale based on darkness
    vec3 result = mix(color.rgb, grayscale, desatAmount * DarkvisionStrength);

    fragColor = vec4(result, color.a);
}

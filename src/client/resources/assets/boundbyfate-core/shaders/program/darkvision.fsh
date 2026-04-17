#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Maximum darkvision range in blocks (60ft ≈ 18 blocks).
uniform float DarkvisionRange;

// Near and far clip planes for depth reconstruction.
uniform float NearPlane;
uniform float FarPlane;

in vec2 texCoord;
out vec4 fragColor;

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Perceptual luminance (ITU-R BT.709)
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Desaturation based on pixel brightness:
    // - Dark pixels (low luminance) = dark in the world = fully gray
    // - Bright pixels (high luminance) = lit in the world = full color
    //
    // Wide smooth transition:
    //   luminance < 0.05  → fully gray (light level ~0-2)
    //   luminance > 0.85  → full color (light level ~12-15)
    //   smooth gradient between them
    float colorAmount = smoothstep(0.05, 0.85, luminance);
    vec3 grayscale = vec3(luminance);
    vec3 desaturated = mix(grayscale, color.rgb, colorAmount);

    // Range fade: beyond darkvision range, darken back to normal darkness
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float rangeFade = 1.0;
    if (depth < 0.9999) {
        float dist = linearDepth(depth);
        rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);
    }

    // Beyond range: fade back to original dark image
    vec3 result = mix(color.rgb, desaturated, rangeFade);

    fragColor = vec4(result, color.a);
}

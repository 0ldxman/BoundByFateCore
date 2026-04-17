#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Maximum darkvision range in blocks (60ft ≈ 18 blocks).
uniform float DarkvisionRange;

// Near and far clip planes for depth reconstruction.
uniform float NearPlane;
uniform float FarPlane;

// Actual light level at the player's position (0-15).
// Used to determine how much color to show:
//   0-2  → fully grayscale (deep darkness)
//   3-11 → smooth gradient
//   12+  → full color (bright light)
uniform float PlayerLightLevel;

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

    // Desaturation based on the player's actual light level (not pixel brightness).
    // This correctly makes dark-world areas gray and lit areas colorful.
    //   light 0-2  → fully gray
    //   light 3-11 → smooth gradient
    //   light 12+  → full color
    float colorAmount = smoothstep(2.0, 12.0, PlayerLightLevel);
    vec3 grayscale = vec3(luminance);
    vec3 desaturated = mix(grayscale, color.rgb, colorAmount);

    // Range fade: beyond darkvision range, fade back to original (dark) image
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float rangeFade = 1.0;
    if (depth < 0.9999) {
        float dist = linearDepth(depth);
        rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);
    }

    // Beyond range: show original unmodified image
    vec3 result = mix(color.rgb, desaturated, rangeFade);

    fragColor = vec4(result, color.a);
}

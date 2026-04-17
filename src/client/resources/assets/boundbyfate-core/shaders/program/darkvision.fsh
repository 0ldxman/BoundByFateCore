#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Maximum darkvision range in blocks (60ft ≈ 18 blocks).
uniform float DarkvisionRange;

// Near and far clip planes for depth reconstruction.
uniform float NearPlane;
uniform float FarPlane;

// Block light level at the player's position (0-15).
// We use BLOCK light (not sky light) because sky light raw value is always 15
// even at night — Minecraft applies the time-of-day factor inside the lightmap.
// Block light = 0 at night on surface or in unlit cave → fully gray
// Block light = 15 near torches/lava → full color
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

    // Desaturation based on block light level at player position:
    //   block light 0-2  → fully gray (deep darkness / night surface)
    //   block light 3-11 → smooth gradient
    //   block light 12+  → full color (near torches/lava)
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

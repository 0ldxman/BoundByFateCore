#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Maximum darkvision range in blocks (60ft ≈ 18 blocks).
// Beyond this distance the darkvision effect fades out — darkness returns.
uniform float DarkvisionRange;

// Near and far clip planes for depth reconstruction.
uniform float NearPlane;
uniform float FarPlane;

in vec2 texCoord;
out vec4 fragColor;

// Reconstruct linear depth (distance from camera in blocks) from depth buffer.
float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Get distance from camera
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    // depth == 1.0 means sky/infinity — don't fade sky
    if (depth >= 0.9999) {
        fragColor = color;
        return;
    }

    float dist = linearDepth(depth);

    // Beyond darkvision range: fade back to normal darkness (no lightmap boost).
    // The lightmap mixin already brightened everything — we need to darken it back
    // for pixels beyond the range.
    float rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);

    // Mix between the darkvision-boosted image and a darkened version
    // rangeFade=1.0 → full darkvision (keep as-is from lightmap)
    // rangeFade=0.0 → beyond range, darken back toward normal
    vec3 darkened = color.rgb * 0.15; // approximate normal darkness
    vec3 result = mix(darkened, color.rgb, rangeFade);

    fragColor = vec4(result, color.a);
}

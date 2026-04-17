#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

// Pixels darker than this threshold get desaturated.
uniform float DarkvisionThreshold;

// Overall strength of the desaturation effect (0.0 = off, 1.0 = full).
uniform float DarkvisionStrength;

// Maximum darkvision range in blocks (60ft ≈ 18 blocks).
// Beyond this distance the darkvision effect fades out.
uniform float DarkvisionRange;

// Near and far clip planes for depth reconstruction.
uniform float NearPlane;
uniform float FarPlane;

in vec2 texCoord;
out vec4 fragColor;

// Reconstruct linear depth (distance from camera in blocks) from depth buffer.
float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0; // NDC
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Get distance from camera
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float dist = linearDepth(depth);

    // Fade darkvision effect beyond range (soft falloff over last 3 blocks)
    float rangeFade = 1.0 - smoothstep(DarkvisionRange - 3.0, DarkvisionRange, dist);

    // Perceptual luminance (ITU-R BT.709)
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Grayscale version
    vec3 grayscale = vec3(luminance);

    // Desaturate dark pixels — the darker the pixel, the more grayscale it becomes.
    float desatAmount = 1.0 - smoothstep(0.0, DarkvisionThreshold, luminance);
    vec3 result = mix(color.rgb, grayscale, desatAmount * DarkvisionStrength * rangeFade);

    fragColor = vec4(result, color.a);
}

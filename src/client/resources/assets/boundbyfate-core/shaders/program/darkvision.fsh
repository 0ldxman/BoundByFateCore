#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float DarkvisionRange;
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
    
    // Perceptual luminance
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // DESATURATION based on PIXEL brightness (not player position)
    // Dark pixels = grayscale (like looking at dark areas in darkness)
    // Bright pixels = full color (like looking at torches, lava, bright textures)
    //
    // Thresholds:
    // - Below 0.08 (~light level 2-3): fully grayscale
    // - Above 0.5 (~light level 12-13): full color
    // - Smooth gradient between
    
    float colorAmount = smoothstep(0.08, 0.5, luminance);
    vec3 grayscale = vec3(luminance);
    vec3 result = mix(grayscale, color.rgb, colorAmount);
    
    // Range fade based on depth
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float rangeFade = 1.0;
    if (depth < 0.9999) {
        float dist = linearDepth(depth);
        rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);
    }
    
    // Beyond range: fade back to original image
    result = mix(color.rgb, result, rangeFade);
    
    fragColor = vec4(result, color.a);
}

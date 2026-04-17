#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float DarkvisionRange;
uniform float PlayerLightLevel;
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
    
    // DESATURATION based on PERCEIVED light level (after darkvision boost)
    // PlayerLightLevel is the light level AFTER darkvision boost:
    // - Real darkness (0-7) → perceived as dim (7-14)
    // - Real dim (8-14) → perceived as bright (15)
    // - Real bright (15) → perceived as bright (15)
    //
    // Desaturation thresholds:
    // - Perceived light 0-7: grayscale (shouldn't happen with darkvision, but just in case)
    // - Perceived light 8-12: partial color
    // - Perceived light 13-15: full color
    
    float colorAmount = smoothstep(8.0, 13.0, PlayerLightLevel);
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

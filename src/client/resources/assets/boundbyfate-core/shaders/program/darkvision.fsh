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
    
    // Get depth
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    
    // DEBUG: Remove this after confirming it works
    if (PlayerLightLevel < 0.1) {
        // Uniform not set - show as MAGENTA for debugging
        fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }
    
    // DESATURATION based on REAL light level (not perceived)
    // 
    // D&D Rules: In darkness you cannot distinguish colors
    // - Light 0-6 (darkness/dim) = grayscale
    // - Light 7+ (bright) = full color
    // - Very bright pixels (luminance > 0.5) = always full color (sky, torches, lava)
    
    float colorAmount;
    
    if (luminance > 0.5) {
        // Very bright pixels (sky, torches, lava, emissives) - always colorful
        colorAmount = 1.0;
    } else {
        // Desaturation based on real light level
        // smoothstep creates smooth transition from grayscale to color
        colorAmount = smoothstep(3.0, 8.0, PlayerLightLevel);
    }
    
    vec3 grayscale = vec3(luminance);
    vec3 result = mix(grayscale, color.rgb, colorAmount);
    
    // Range fade based on depth (disabled for now - testing)
    // float rangeFade = 1.0;
    // if (depth < 0.9999) {
    //     float dist = linearDepth(depth);
    //     rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);
    // }
    // result = mix(color.rgb, result, rangeFade);
    
    fragColor = vec4(result, color.a);
}

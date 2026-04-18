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
    
    // Get depth to detect sky
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    bool isSky = (depth >= 0.9999); // Sky has depth = 1.0
    
    // DEBUG: Remove this after confirming it works
    if (PlayerLightLevel < 0.1) {
        // Uniform not set - show as MAGENTA for debugging
        fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }
    
    // DESATURATION based on PERCEIVED light level AND pixel brightness
    // 
    // Rules:
    // 1. Sky = always full color (no desaturation)
    // 2. Very bright pixels (luminance > 0.7) = always full color (torches, lava, emissives)
    // 3. Perceived light 13-15 = full color
    // 4. Perceived light 8-12 = partial color
    // 5. Perceived light 0-7 = grayscale
    
    float colorAmount;
    
    if (isSky) {
        // Sky always keeps color
        colorAmount = 1.0;
    } else if (luminance > 0.7) {
        // Very bright pixels (torches, lava, emissives) - always colorful
        colorAmount = 1.0;
    } else {
        // Normal desaturation based on perceived light level
        colorAmount = smoothstep(8.0, 13.0, PlayerLightLevel);
    }
    
    vec3 grayscale = vec3(luminance);
    vec3 result = mix(grayscale, color.rgb, colorAmount);
    
    // Range fade based on depth
    float rangeFade = 1.0;
    if (!isSky && depth < 0.9999) {
        float dist = linearDepth(depth);
        rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);
    }
    
    // Beyond range: fade back to original image
    result = mix(color.rgb, result, rangeFade);
    
    fragColor = vec4(result, color.a);
}

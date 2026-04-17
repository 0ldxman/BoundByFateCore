#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float DarkvisionRange;
uniform float NearPlane;
uniform float FarPlane;
uniform float PlayerLightLevel;

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
    
    // EXPOSURE BOOST for dark pixels (like HDR/night vision camera)
    // This pulls detail out of shadows instead of just brightening black to gray
    float targetLuminance = 0.5; // Target middle gray
    float exposureBoost = 1.0;
    
    if (luminance < targetLuminance && luminance > 0.001) {
        // Calculate how much to boost: darker pixels get more boost
        // Max boost of 8x for very dark pixels, tapering off for brighter ones
        exposureBoost = min(targetLuminance / luminance, 8.0);
    }
    
    // Apply exposure boost
    vec3 boosted = color.rgb * exposureBoost;
    
    // Clamp to prevent over-exposure
    boosted = min(boosted, vec3(1.0));
    
    // Recalculate luminance after boost
    float boostedLuminance = dot(boosted, vec3(0.2126, 0.7152, 0.0722));
    
    // DESATURATION based on boosted luminance
    // Dark areas (even after boost) = gray
    // Bright areas = full color
    // Threshold: 0.25-0.75 for smooth transition
    float colorAmount = smoothstep(0.25, 0.75, boostedLuminance);
    vec3 grayscale = vec3(boostedLuminance);
    vec3 desaturated = mix(grayscale, boosted, colorAmount);
    
    // Range fade based on depth
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

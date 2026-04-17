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
    
    // EXPOSURE BOOST - only for dark pixels
    // Bright pixels (luminance > 0.5) are left unchanged
    // Dark pixels get boosted to pull out shadow detail
    vec3 result;
    
    if (luminance < 0.5) {
        // Dark pixel: apply exposure boost
        float targetLuminance = 0.5;
        float exposureBoost = 1.0;
        
        if (luminance > 0.001) {
            // Calculate boost: max 6x for very dark, tapering off
            exposureBoost = min(targetLuminance / luminance, 6.0);
        } else {
            // Pitch black: set to dim gray
            exposureBoost = 6.0;
        }
        
        // Apply boost and clamp
        vec3 boosted = min(color.rgb * exposureBoost, vec3(1.0));
        float boostedLuminance = dot(boosted, vec3(0.2126, 0.7152, 0.0722));
        
        // Desaturate dark areas (even after boost)
        // 0.2-0.7 range for smooth transition
        float colorAmount = smoothstep(0.2, 0.7, boostedLuminance);
        vec3 grayscale = vec3(boostedLuminance);
        result = mix(grayscale, boosted, colorAmount);
        
    } else {
        // Bright pixel: leave unchanged, full color
        result = color.rgb;
    }
    
    // Range fade based on depth
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float rangeFade = 1.0;
    if (depth < 0.9999) {
        float dist = linearDepth(depth);
        rangeFade = 1.0 - smoothstep(DarkvisionRange - 4.0, DarkvisionRange, dist);
    }
    
    // Beyond range: fade back to original dark image
    result = mix(color.rgb, result, rangeFade);
    
    fragColor = vec4(result, color.a);
}

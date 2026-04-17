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
    
    // GAMMA ADJUSTMENT instead of exposure boost
    // Gamma < 1.0 brightens dark areas while preserving contrast and detail
    // This is how real night vision and low-light cameras work
    //
    // Apply gamma only to dark/dim areas (luminance < 0.6)
    // Bright areas (luminance >= 0.6) are left unchanged
    
    vec3 result;
    
    if (luminance < 0.6) {
        // Apply gamma correction: output = input^(1/gamma)
        // gamma = 0.4 gives strong brightening for dark areas
        float gamma = 0.4;
        vec3 brightened = pow(color.rgb, vec3(1.0 / gamma));
        
        // Recalculate luminance after gamma
        float newLuminance = dot(brightened, vec3(0.2126, 0.7152, 0.0722));
        
        // DESATURATION based on original luminance (before gamma)
        // Very dark (< 0.2): fully grayscale
        // Dark-to-dim (0.2-0.6): smooth transition to color
        float colorAmount = smoothstep(0.1, 0.6, luminance);
        vec3 grayscale = vec3(newLuminance);
        result = mix(grayscale, brightened, colorAmount);
        
    } else {
        // Bright areas: no change
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

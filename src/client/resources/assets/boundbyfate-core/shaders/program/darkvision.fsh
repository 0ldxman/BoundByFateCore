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
    
    // Map luminance to approximate Minecraft light levels (0-15)
    // This is approximate because luminance is affected by textures, time of day, etc.
    // Rough mapping after testing:
    //   luminance 0.0-0.15  ≈ light 0-6  (darkness)
    //   luminance 0.15-0.40 ≈ light 7-10 (dim light)
    //   luminance 0.40-1.0  ≈ light 11-15 (bright light)
    
    vec3 result;
    float boostedLuminance;
    
    if (luminance < 0.15) {
        // DARKNESS (light 0-6): boost to appear as DIM LIGHT (light 7-8)
        // Target luminance ~0.30 (dim light appearance)
        float targetLum = 0.30;
        float boost = (luminance > 0.001) ? min(targetLum / luminance, 10.0) : 10.0;
        
        vec3 boosted = min(color.rgb * boost, vec3(1.0));
        boostedLuminance = dot(boosted, vec3(0.2126, 0.7152, 0.0722));
        
        // Full grayscale in darkness
        result = vec3(boostedLuminance);
        
    } else if (luminance < 0.40) {
        // DIM LIGHT (light 7-10): boost to appear as BRIGHT LIGHT (light 15)
        // Target luminance ~0.70 (bright light appearance)
        float targetLum = 0.70;
        float boost = min(targetLum / luminance, 3.0);
        
        vec3 boosted = min(color.rgb * boost, vec3(1.0));
        boostedLuminance = dot(boosted, vec3(0.2126, 0.7152, 0.0722));
        
        // Partial desaturation in dim light (smooth transition to color)
        // At luminance 0.15: mostly gray
        // At luminance 0.40: mostly color
        float colorAmount = smoothstep(0.15, 0.40, luminance);
        vec3 grayscale = vec3(boostedLuminance);
        result = mix(grayscale, boosted, colorAmount);
        
    } else {
        // BRIGHT LIGHT (light 11-15): no change, full color
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

#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float PlayerLightLevel;
uniform float IsUnderwater;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    
    // Sky (depth == 1.0) and underwater: always pass through unchanged
    if (depth >= 0.9999 || IsUnderwater > 0.5) {
        fragColor = color;
        return;
    }
    
    // Perceptual luminance
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // Very bright pixels (torches, lava, emissives) - always colorful
    if (luminance > 0.7) {
        fragColor = color;
        return;
    }
    
    // Desaturation: light 0 = full grayscale, light 8+ = full color
    float colorAmount = smoothstep(0.0, 8.0, PlayerLightLevel);
    
    vec3 grayscale = vec3(luminance);
    vec3 result = mix(grayscale, color.rgb, colorAmount);
    
    fragColor = vec4(result, color.a);
}

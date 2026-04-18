#version 150

uniform sampler2D DiffuseSampler;

uniform float PlayerLightLevel;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    
    // Perceptual luminance
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // DEBUG: Remove this after confirming it works
    if (PlayerLightLevel < 0.1) {
        // Uniform not set - show as MAGENTA for debugging
        fragColor = vec4(1.0, 0.0, 1.0, 1.0);
        return;
    }
    
    // DESATURATION based on REAL light level
    // - Light 0-8 = gradual transition from grayscale to color
    // - Light 8+ = full color
    // - Very bright pixels (luminance > 0.5) = always full color (sky, torches, lava)
    
    float colorAmount;
    
    if (luminance > 0.5) {
        colorAmount = 1.0;
    } else {
        colorAmount = smoothstep(0.0, 8.0, PlayerLightLevel);
    }
    
    vec3 grayscale = vec3(luminance);
    vec3 result = mix(grayscale, color.rgb, colorAmount);
    
    fragColor = vec4(result, color.a);
}

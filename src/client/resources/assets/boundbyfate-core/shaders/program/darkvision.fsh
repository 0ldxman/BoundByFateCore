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
    
    // DESATURATION based on player's current light level
    // In darkness (light 0-7): grayscale
    // In dim light (light 8-14): partial color
    // In bright light (light 15): full color
    //
    // Light level 0-2: fully gray
    // Light level 12-15: full color
    // Smooth gradient between
    
    float colorAmount = smoothstep(2.0, 12.0, PlayerLightLevel);
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

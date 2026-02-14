#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uDye;
uniform float uBloomIntensity;
uniform float uBloomThreshold;
uniform float uNeonGlow;
uniform float uChromaticAberration;
uniform float uBackground;

void main() {
    float ca = uChromaticAberration * 0.005;

    // Chromatic Aberration — sampling offset for RGB channels
    vec3 color;
    color.r = texture(uDye, vUV - vec2(ca, 0.0)).r;
    color.g = texture(uDye, vUV).g;
    color.b = texture(uDye, vUV + vec2(ca, 0.0)).b;

    // Tone mapping — gamma lift for vibrancy
    color = pow(color, vec3(0.85));

    // Bloom — brighten luminous areas
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    color += color * smoothstep(uBloomThreshold, uBloomThreshold + 1.0, luminance) * uBloomIntensity;

    // Neon glow — boost saturation on bright areas
    if (uNeonGlow > 0.5) {
        float nLum = dot(color, vec3(0.3, 0.59, 0.11));
        vec3 saturated = mix(vec3(nLum), color, 1.6);
        color = mix(color, saturated, smoothstep(0.15, 0.6, nLum));
        color *= 1.15;
    }

    // Vignette
    vec2 center = vUV - 0.5;
    float vignette = 1.0 - dot(center, center) * 0.4;
    color *= vignette;

    // Background (0.0 = AMOLED pure black, >0 = ambient)
    vec3 bg = vec3(uBackground);
    color = max(color, bg);

    fragColor = vec4(color, 1.0);
}

#version 300 es
precision highp float;

in float vLife;
in float vAge;
out vec4 fragColor;

uniform vec3 uColor;

void main() {
    // Circular point sprite
    vec2 coord = gl_PointCoord - 0.5;
    float dist = length(coord);
    if (dist > 0.5) discard;

    // Soft edge
    float alpha = smoothstep(0.5, 0.0, dist);
    
    // Fade out based on life
    // Fade out based on life and reduce overall opacity
    alpha *= vLife * 0.6;

    fragColor = vec4(uColor, alpha);
}

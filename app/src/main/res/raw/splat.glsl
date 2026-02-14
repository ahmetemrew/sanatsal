#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uTarget;
uniform vec2 uPoint;
uniform vec3 uValue;
uniform float uRadius;
uniform float uAspectRatio;

void main() {
    vec4 base = texture(uTarget, vUV);
    vec2 d = vUV - uPoint;
    d.x *= uAspectRatio;
    float dist = dot(d, d);
    float factor = exp(-dist / uRadius);
    base.xyz += uValue * factor;
    fragColor = base;
}

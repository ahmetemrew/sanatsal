#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform vec2 uGravity;
uniform float uDt;

void main() {
    vec4 vel = texture(uVelocity, vUV);
    vel.xy += uGravity * uDt;
    fragColor = vel;
}

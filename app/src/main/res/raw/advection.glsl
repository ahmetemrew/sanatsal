#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform sampler2D uSource;
uniform vec2 uTexelSize;
uniform float uDt;
uniform float uDissipation;

void main() {
    vec2 vel = texture(uVelocity, vUV).xy;
    vec2 coord = vUV - uDt * vel * uTexelSize;
    fragColor = uDissipation * texture(uSource, coord);
}

#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform vec2 uTexelSize;

void main() {
    float vL = texture(uVelocity, vUV - vec2(uTexelSize.x, 0.0)).y;
    float vR = texture(uVelocity, vUV + vec2(uTexelSize.x, 0.0)).y;
    float uB = texture(uVelocity, vUV - vec2(0.0, uTexelSize.y)).x;
    float uT = texture(uVelocity, vUV + vec2(0.0, uTexelSize.y)).x;

    float curl = (vR - vL) - (uT - uB);
    fragColor = vec4(curl * 0.5, 0.0, 0.0, 1.0);
}

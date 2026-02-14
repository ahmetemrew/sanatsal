#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uPressure;
uniform sampler2D uVelocity;
uniform vec2 uTexelSize;

void main() {
    float L = texture(uPressure, vUV - vec2(uTexelSize.x, 0.0)).x;
    float R = texture(uPressure, vUV + vec2(uTexelSize.x, 0.0)).x;
    float B = texture(uPressure, vUV - vec2(0.0, uTexelSize.y)).x;
    float T = texture(uPressure, vUV + vec2(0.0, uTexelSize.y)).x;

    vec2 vel = texture(uVelocity, vUV).xy;
    vel -= vec2(R - L, T - B) * 0.5;
    fragColor = vec4(vel, 0.0, 1.0);
}

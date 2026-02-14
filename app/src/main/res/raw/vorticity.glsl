#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uVelocity;
uniform sampler2D uCurl;
uniform vec2 uTexelSize;
uniform float uVorticity;
uniform float uDt;

void main() {
    float cL = texture(uCurl, vUV - vec2(uTexelSize.x, 0.0)).x;
    float cR = texture(uCurl, vUV + vec2(uTexelSize.x, 0.0)).x;
    float cB = texture(uCurl, vUV - vec2(0.0, uTexelSize.y)).x;
    float cT = texture(uCurl, vUV + vec2(0.0, uTexelSize.y)).x;
    float cC = texture(uCurl, vUV).x;

    vec2 eta = vec2(abs(cR) - abs(cL), abs(cT) - abs(cB));
    float len = max(length(eta), 1e-5);
    eta /= len;

    vec2 force = uVorticity * vec2(eta.y * cC, -eta.x * cC);

    vec2 vel = texture(uVelocity, vUV).xy;
    vel += force * uDt;
    fragColor = vec4(vel, 0.0, 1.0);
}

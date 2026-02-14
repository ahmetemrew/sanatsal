#version 300 es
precision highp float;

layout(location = 0) in float aIndex; // Index 0..N-1

uniform sampler2D uParticles;
uniform int uParticlesRes; // sqrt(N)
uniform float uPointSize;
uniform float uAspectRatio;

out float vLife;
out float vAge;

void main() {
    // Compute UV from index
    int x = int(aIndex) % uParticlesRes;
    int y = int(aIndex) / uParticlesRes;
    vec2 uv = (vec2(x, y) + 0.5) / float(uParticlesRes);

    // Read position from texture
    vec4 pData = texture(uParticles, uv);
    vec2 pos = pData.xy;
    vLife = pData.z;
    vAge = pData.w;

    // Map 0..1 to clip space -1..1
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
    gl_PointSize = uPointSize * (0.5 + 0.5 * vLife); // Scale by life
}

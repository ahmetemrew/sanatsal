#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uParticles;
uniform sampler2D uVelocity;
uniform float uDt;
uniform float uRandomSeed;

// Pseudo-random generator
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

void main() {
    // Read current particle data (RG=pos, B=life, A=age)
    vec4 pData = texture(uParticles, vUV);
    vec2 pos = pData.xy;
    float life = pData.z;
    float age = pData.w;

    // Sample velocity at current position
    vec2 vel = texture(uVelocity, pos).xy;

    // Update position
    pos += vel * uDt;

    // Update life/age
    life -= uDt * 0.5; // decay
    age += uDt;

    // Reset if dead or out of bounds or random chance
    if (life <= 0.0 || pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0 || random(vUV + uRandomSeed) < 0.001) {
        pos = vec2(random(vUV + uRandomSeed * 1.1), random(vUV + uRandomSeed * 1.2));
        life = 1.0;
        age = 0.0;
    }

    fragColor = vec4(pos, life, age);
}

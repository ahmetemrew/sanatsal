#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uDye;
uniform sampler2D uVelocity; // for normals?
uniform vec2 uResolution;
uniform float uTime;
uniform int uRaySteps;
uniform float uLightIntensity;
uniform float uOpacity;
uniform float uDensityScale; // Multiplier for density

// Standard pseudo-random
float hash(float n) { return fract(sin(n) * 43758.5453); }
float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n = p.x + p.y * 57.0 + p.z * 113.0;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
                   mix(hash(n + 57.0), hash(n + 58.0), f.x), f.y),
               mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
                   mix(hash(n + 170.0), hash(n + 171.0), f.x), f.y), f.z);
}

// Sample density from texture
float getDensity(vec3 p) {
    if(p.x < -1.0 || p.x > 1.0 || p.y < -1.0 || p.y > 1.0 || abs(p.z) > 0.5) return 0.0;
    
    vec2 uv = p.xy * 0.5 + 0.5;
    vec4 tex = texture(uDye, uv);
    float d = length(tex.rgb) * 0.333;
    
    // Add vertical falloff
    float h = d * uDensityScale * 0.8; 
    float falloff = smoothstep(h, h * 0.2, abs(p.z));
    
    return d * falloff;
}

void main() {
    // Screen coords
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec2 p = uv * 2.0 - 1.0;
    p.x *= uResolution.x / uResolution.y;

    // Camera setup - Tilted view
    vec3 ro = vec3(0.0, -1.2, 1.2); 
    vec3 ta = vec3(0.0, 0.0, 0.0);
    vec3 fw = normalize(ta - ro);
    vec3 rt = normalize(cross(fw, vec3(0.0, 1.0, 0.0)));
    vec3 up = normalize(cross(rt, fw));
    vec3 rd = normalize(p.x * rt + p.y * up + 1.5 * fw);

    // Light setup
    vec3 lightDir = normalize(vec3(0.5, 0.5, 1.0));
    vec3 lightCol = vec3(1.0, 0.95, 0.9) * uLightIntensity;

    // Marching
    vec4 col = vec4(0.0);
    float t = 0.0;
    int steps = uRaySteps;
    float stepSize = 0.025; // Finer steps

    for(int i=0; i<steps; i++) {
        vec3 pos = ro + rd * t;
        float den = getDensity(pos);
        
        if (den > 0.01) {
            // Gradient Normal Calculation (Expensive but necessary for 3D look)
            float eps = 0.01;
            vec3 n = normalize(vec3(
                getDensity(pos - vec3(eps, 0, 0)) - getDensity(pos + vec3(eps, 0, 0)),
                getDensity(pos - vec3(0, eps, 0)) - getDensity(pos + vec3(0, eps, 0)),
                getDensity(pos - vec3(0, 0, eps)) - getDensity(pos + vec3(0, 0, eps)) // Z gradient
            ));
            
            // Lighting (Phong)
            float diff = max(0.0, dot(n, lightDir));
            float spec = pow(max(0.0, dot(reflect(-lightDir, n), -rd)), 16.0); // Shiny
            
            // Ambient
            float amb = 0.2;
            
            // Color mapping
            vec2 texUV = pos.xy * 0.5 + 0.5;
            vec3 albedo = texture(uDye, texUV).rgb;
            
            vec3 lighting = albedo * (diff + amb) + vec3(spec);
            
            float alpha = den * uOpacity * 0.2; // Accumulation factor
            col.rgb += lighting * alpha * (1.0 - col.a);
            col.a += alpha * (1.0 - col.a);
        }
        
        if (col.a > 0.98) break;
        t += stepSize;
    }

    // Background
    vec3 bg = vec3(0.05, 0.05, 0.08);
    col.rgb += bg * (1.0 - col.a);

    fragColor = vec4(col.rgb, 1.0);
}

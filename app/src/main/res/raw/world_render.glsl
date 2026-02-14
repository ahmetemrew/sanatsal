#version 300 es
precision highp float;

in vec2 vUV;
out vec4 fragColor;

uniform vec2 uResolution;
uniform float uTime;
uniform float uSpeed;
uniform float uDetail;      
uniform int uRaySteps; // NEW: Dynamic quality control
uniform float uColorShift;
uniform int uWorldType;   // 0=Web, 1=Geometric, 2=Rings, 3=Terrain
uniform int uCameraMode;  // 0=Straight, 1=Wander
uniform float uDistortion;
uniform float uPathWidth;
uniform float uWorldScale;
uniform float uLightIntensity;
uniform float uOpacity;
uniform float uRimIntensity;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;

// ... (Rest of utils/map functions unchanged) ...

// Main Removed from Top
// Utilities
mat2 rot(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

// 3D Random
float hash(vec3 p) {
    p  = fract( p*0.3183099 + .1 );
    p *= 17.0;
    return fract( p.x*p.y*p.z*(p.x+p.y+p.z) );
}

// Noise
float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);
    float n = p.x + p.y*57.0 + p.z*113.0;
    return mix(mix(mix( hash(p+vec3(0,0,0)), hash(p+vec3(1,0,0)),f.x),
                   mix( hash(p+vec3(0,1,0)), hash(p+vec3(1,1,0)),f.x),f.y),
               mix(mix( hash(p+vec3(0,0,1)), hash(p+vec3(1,0,1)),f.x),
                   mix( hash(p+vec3(0,1,1)), hash(p+vec3(1,1,1)),f.x),f.y),f.z);
}



// 1D Hash for Path Noise
float hash1(float n) { return fract(sin(n) * 43758.5453); }

// Smooth Noise
float snoise(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash1(i), hash1(i + 1.0), f);
}

vec3 getPath(float z) {
    if (uCameraMode == 0) return vec3(0.0);

    // Standard Organic Path (Base)
    float x = sin(z * 0.1) * 3.0;
    float y = cos(z * 0.15) * 3.0;
    
    // Add Layered Noise (Octaves) for Unpredictability
    // This prevents the "same angles" feeling
    x += snoise(z * 0.03) * 6.0; 
    y += snoise(z * 0.02 + 100.0) * 6.0;
    
    // Slight twist
    float twist = snoise(z * 0.01) * 2.0;
    
    return vec3(x * uWorldScale, y * uWorldScale, 0.0);
}

// ================= PRIMITIVES =================

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q,0.0)) + min(max(q.x,max(q.y,q.z)),0.0);
}

float sdOctahedron(vec3 p, float s) {
    p = abs(p);
    return (p.x+p.y+p.z-s)*0.57735027;
}

float sdGyroid(vec3 p, float scale, float thick, float bias) {
    p *= scale;
    return abs(dot(sin(p), cos(p.zxy)) - bias) / scale - thick;
}

// ================= WORLDS =================

// 0: SPIDERWEB
float mapWeb(vec3 p, vec3 pathOffset) {
    vec3 q = p;
    // q -= pathOffset * 0.5; // Web follows path loosely
    
    float scale = 1.5 / uWorldScale;
    float d = sdGyroid(q, scale, 0.03 * uWorldScale, 0.0);
    
    // Distortion
    d -= uDistortion * 0.2 * sin(p.z * 0.5);
    
    return d * 0.8;
}

// 1: GEOMETRIC FIELD
float mapGeometric(vec3 p, vec3 pathOffset) {
    vec3 q = p - pathOffset; // Objects relative to path center!
    
    // Grid repetition
    float cellSize = 3.0 * uWorldScale;
    vec3 id = floor(q / cellSize);
    vec3 local = mod(q, cellSize) - cellSize * 0.5;
    
    float h = hash(id);
    
    // Less dense center
    float distToCenter = length(id.xy); 
    
    // CONTROL: Spawn Density
    // h is random 0..1 per cell.
    // If h > uDetail: Render. (So uDetail 0.1 = 10% spawn, uDetail 1.0 = 100%)
    // Fix: Invert logic, if h > uDetail, EMPTY.
    if (h > uDetail && distToCenter > 1.0) return cellSize; 
    
    local.xy *= rot(uTime * (0.5 + h) + p.z * 0.1);
    local.xz *= rot(uTime * (0.3 + h));
    
    float d;
    float type = fract(h * 10.0);
    float size = (0.3 + h * 0.5) * uWorldScale;
    
    if (type < 0.33) d = length(local) - size; 
    else if (type < 0.66) d = sdBox(local, vec3(size * 0.8)); 
    else d = sdOctahedron(local, size * 1.1); 
    
    return d * 0.5;
}

// 2: NEON RINGS
float mapRings(vec3 p, vec3 pathOffset) {
    vec3 q = p - pathOffset;
    
    float space = 4.0 * uWorldScale;
    float z = mod(q.z + uTime, space) - space * 0.5;
    
    float radius = 3.0 * uWorldScale;
    float d = length(vec2(length(q.xy) - radius, z)) - (0.1 * uWorldScale);
    
    return d * 0.8;
}

// 3: ALIEN TERRAIN
float mapTerrain(vec3 p, vec3 pathOffset) {
    vec3 q = p - pathOffset;
    float h = sin(q.x * 0.3) * sin(q.z * 0.3) * uWorldScale;
    float ceiling = -q.y + 4.0 * uWorldScale + h;
    float floor   = q.y + 4.0 * uWorldScale + h;
    return min(ceiling, floor);
}

// DISPATCHER
float map(vec3 p) {
    // 1. Get Path Center at this Z depth
    vec3 pathPos = getPath(p.z);
    
    // 2. Sample World
    float d;
    if (uWorldType == 1) d = mapGeometric(p, pathPos);
    else if (uWorldType == 2) d = mapRings(p, pathPos);
    else if (uWorldType == 3) d = mapTerrain(p, pathPos);
    else d = mapWeb(p, pathPos); // Default 0
    
            // 3. CARVE SAFETY TUNNEL (The "Smart" part)
            // Distance to the calculated path center
            float distToPath = length(p.xy - pathPos.xy);
            
            // SMART OPTIMIZATION REMOVED: Render EVERYTHING.
            // float distToCam = length(p.z - getPath(uTime * (uSpeed * 6.0 + 3.0)).z);
            // if (distToCam > 120.0) return distToPath - uPathWidth; 
            
            // Create a smooth tunnel
    // "Negative" logic: Maximize d against the tunnel hole
    // - (distToPath - width) creates a hole
    float tunnel = -(distToPath - uPathWidth);
    
    d = max(d, tunnel);
    
    return d;
}

// ================= CAMERA =================


uniform float uPartCount;
uniform float uPartSize;
uniform float uPartSpeed;
uniform float uAmoled; // 0 or 1
uniform float uBlackThreshold; // 0.0 - 0.2

// Calculate Normal (Tetrahedron technique for efficiency)
vec3 calcNormal(vec3 p) {
    const vec2 k = vec2(1.0, -1.0);
    // Standard epsilon (0.002) for Smooth, clean surfaces
    // Too small (0.0001) caused the "wobble/noise" look
    return normalize(
        k.xyy * map(p + k.xyy * 0.002) +
        k.yyx * map(p + k.yyx * 0.002) +
        k.yxy * map(p + k.yxy * 0.002) +
        k.xxx * map(p + k.xxx * 0.002)
    );
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / uResolution.y;

    // Movement
    float speed = uSpeed * 6.0 + 3.0; 
    float tVal = uTime * speed;
    
    vec3 ro = getPath(tVal);
    ro.z = tVal; 
    
    vec3 lookAt = getPath(tVal + 1.0); 
    lookAt.z = tVal + 1.0;

    vec3 fwd = normalize(lookAt - ro);
    vec3 right = normalize(cross(fwd, vec3(0.0, 1.0, 0.0)));
    vec3 up = normalize(cross(right, fwd));
    // Wider FOV (0.7 focal length) makes objects appear further away
    // User requested "Move camera back"
    vec3 rd = normalize(fwd * 0.7 + right * uv.x + up * uv.y);
    
    // Ray Marching
    float t = 0.0;
    float maxDist = 120.0; // Increased Draw Distance (was 80.0)
    
    vec3 p;
    float d;
    float hit = 0.0;
    float glow = 0.0; 
    
    // Dithering to reduce banding
    float dither = hash(vec3(uv, uTime)) * 0.1;
    t += dither;

    for(int i=0; i<uRaySteps; i++) {
        p = ro + rd * t;
        d = map(p);
        
        // Accumulate glow 
        float prox = 1.0 / (1.0 + d * d * 50.0);
        glow += prox; 
        
        if (d < 0.002) {
            hit = 1.0;
            break;
        }
        
        t += d * 0.6; // Faster stepping (was 0.5) to cover more distance with same steps
        if (t > maxDist) break;
    }
    
    // ================= COLORING =================
    
    // ================= COLORING =================
    
    // BACKGROUND
    vec3 col = uColor1 * 0.1; // Deep base
    
    // Nebula / Atmosphere
    float nebula = pow(abs(rd.y), 2.0);
    col += uColor2 * nebula * 0.2;
    col += mix(uColor1, uColor2, sin(uColorShift + rd.x)*0.5+0.5) * 0.05;
    
    // AMOLED VOID OVERRIDE
    if (uAmoled > 0.5) {
        col = vec3(0.0); // Absolute black background
    }
    
    // DYNAMIC PARTICLES / STARS
    if (uPartCount > 0.01 && (uAmoled < 0.5 || uPartCount > 0.0)) { // Allow stars in AMOLED if explicitly enabled
        // Parallax layers
        for (float i=1.0; i<=3.0; i++) {
            vec3 starRd = rd;
            // Rotate layers over time for movement
            float starTime = uTime * uPartSpeed * 0.2 * i;
            starRd.xy *= rot(starTime * 0.1);
            
            float dens = uPartCount * 50.0 * i;
            float sz = uPartSize * 0.5;
            
            // Boosted brightness for visibility
            float s = pow(hash(floor(starRd * dens)), 20.0 - sz * 5.0) * i * 1.5; 
            // Blink
            s *= 0.5 + 0.5 * sin(uTime * 5.0 + hash(starRd)*10.0);
            
            vec3 pCol;
            if (uAmoled > 0.5) {
                // AMOLED: Pure Neon Stars matching the edges
                pCol = uColor3; 
            } else {
                // Standard: Atmospheric Mix
                pCol = mix(uColor2, uColor3, hash(starRd));
            }
            
            col += pCol * s;
        }
    }
    
    
    // ================= LIGHTING & MATERIAL =================
    
    // ================= LIGHTING & MATERIAL =================
    
    // RESTORED & ENHANCED: Sharpe 3D Definition (Unity-like)
    if (hit > 0.0) {
        vec3 N = calcNormal(p);
        
        // -- 1. Surface Detail --
        // Clean surface (removed noise) for "Unity Cube" look
        
        // -- 2. Material Properties --
        float colorPattern = sin(p.z * 0.2 + uColorShift * 1.5 + p.x * 0.5);
        vec3 baseColor = mix(uColor2, uColor3, colorPattern * 0.5 + 0.5);
        
        // -- 3. Lighting Vectors --
        // Move light slightly further ahead to illuminate faces better
        vec3 lightPos = getPath(tVal + 4.0); 
        lightPos.z = tVal + 4.0;
        
        vec3 L = normalize(lightPos - p);
        vec3 V = -rd;
        vec3 H = normalize(L + V);
        
        // -- 4. Ambient Occlusion (Simulated) --
        // Make corners darker to define geometry
        float ao = clamp(map(p + N * 0.2) * 5.0, 0.0, 1.0);
        
        // -- 5. Diffuse (Lambert) --
        float diff = max(dot(N, L), 0.0);
        
        // -- 6. Specular (Blinn-Phong) --
        float NdotH = max(dot(N, H), 0.0);
        float spec = pow(NdotH, 64.0); 
        
        // -- COMBINE --
        
        vec3 finalColor;
        
        if (uAmoled > 0.5) {
             // AMOLED Logic (Neon Edges)
            float neonRim = pow(1.0 - max(dot(N, V), 0.0), 3.0); 
            vec3 edgeCol = uColor3 * neonRim * uRimIntensity * 1.5; 
            vec3 shine = vec3(spec) * uColor2 * uLightIntensity;
            finalColor = edgeCol + shine;
             
        } else {
            // STANDARD MODE: High Quality Solid Objects
            // To look like "Unity Cubes":
            // 1. Strong Diffuse (Show faces)
            // 2. Clear Ambient (No pitch black shadows)
            // 3. Sharp Specular (Plastic/Metal feel)
            
            // Ambient
            vec3 ambient = baseColor * 0.3 * uColor1 * ao; 
            
            // Diffuse
            vec3 diffuse = baseColor * diff * uLightIntensity * 1.2;
            
            // Specular
            vec3 specular = vec3(spec) * uLightIntensity * 0.8;
            
            // Rim Light (Subtle Edge Definition)
            float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0); 
            vec3 rimColor = uColor3 * rim * uRimIntensity * 0.5;
            
            finalColor = ambient + diffuse + specular + rimColor;
        }
        
        // Simple shadows (march towards light)
        // float shadow = 1.0; 
        // ... (Skipping hard shadows for performance, AO covers it)
        
        
        col = mix(finalColor, col, clamp(d/maxDist, 0.0, 1.0)); // Fade to fog distance
        
        // Fog Logic
        vec3 fogColor = (uAmoled > 0.5) ? vec3(0.0) : mix(uColor1, uColor2, 0.5);
        
        if (uAmoled > 0.5) {
             // AMOLED: Smart Linear Fade to Black
             float fogDist = distance(ro, p);
             float fadeStart = maxDist * 0.5; 
             float fadeEnd = maxDist * 0.9;   
             float visibility = smoothstep(fadeEnd, fadeStart, fogDist);
             
             col = mix(fogColor, col, visibility);
             
        } else {
             // STANDARD: Exponential Atmospheric Fog
             // This gives the "thick atmosphere" look the user liked
             float fogDist = distance(ro, p);
             float fogDensity = 0.008; // Clearer atmosphere (was 0.015)
             float fogFactor = 1.0 - exp(-fogDensity * fogDist * 0.1); 
             // Height fog
             float heightFog = smoothstep(0.0, -10.0, p.y);
             fogFactor += heightFog * 0.5;
             
             fogFactor = clamp(fogFactor, 0.0, 1.0);
             col = mix(finalColor, fogColor, fogFactor);
             
             // Distance Clip Fade (Soft edge at world end)
             col = mix(col, fogColor, smoothstep(maxDist*0.8, maxDist, fogDist));
        }
    }
    
    // GLOW
    vec3 glowCol = mix(uColor3, uColor2, sin(uTime + uColorShift)*0.5+0.5);
    if (uAmoled > 0.5) glowCol *= 0.02; // Very weak glow in AMOLED to prevent wash
    
    col += glowCol * glow * 0.02 * uLightIntensity; 
    
    // ================= POST PROCESSING =================
    
    // Vignette
    col *= 1.0 - pow(length(uv) * 0.5, 3.0);
    
    // ACES Tone Mapping (The "Next Gen" Look)
    col = col * 0.6; // Exposure bias
    float a = 2.51;
    float b_aces = 0.03;
    float c_aces = 2.43;
    float d_aces = 0.59;
    float e = 0.14;
    col = clamp((col * (a * col + b_aces)) / (col * (c_aces * col + d_aces) + e), 0.0, 1.0);
    
    // Gamma Correction (Standard Rec.709)
    col = pow(col, vec3(1.0 / 2.2));
    
    // AMOLED: Deep Cleaner Blacks
    if (uAmoled > 0.5) {
        // Soft clipping: Push dark grays to 0
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        if (lum < uBlackThreshold) {
            col = vec3(0.0);
        } else {
            // Smooth falloff to prevent harsh banding
            col = (col - vec3(uBlackThreshold)) / (1.0 - uBlackThreshold);
        }
    }
    
    fragColor = vec4(col, uOpacity); 
}

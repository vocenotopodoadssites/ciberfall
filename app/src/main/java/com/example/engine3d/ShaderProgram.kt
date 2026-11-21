package com.example.engine3d

import android.opengl.GLES30

class CyberpunkLitShader {
    val programId: Int

    val aPos: Int
    val aNorm: Int

    val uMVP: Int
    val uModel: Int
    val uBaseColor: Int
    val uEmissiveColor: Int
    val uEmissiveStrength: Int
    val uShininess: Int
    val uAlpha: Int
    val uMaterialMode: Int // 0=Std, 1=WetTileFloor+Moss, 2=ConcreteWall+Moss, 3=RustedCorrugated, 4=Stairs
    val uCameraPos: Int
    val uFogColor: Int
    val uFogDensity: Int
    val uLightPos1: Int
    val uLightColor1: Int
    val uLightPos2: Int
    val uLightColor2: Int
    val uLightPos3: Int
    val uLightColor3: Int
    val uFloorMap: Int
    val uHasFloorMap: Int
    val uBuildingMap: Int
    val uHasBuildingMap: Int
    val uWallTiles: Int

    init {
        val vs = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNorm;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            out vec3 vWorldPos;
            out vec3 vNormal;
            out vec3 vLocalPos;
            out vec3 vLocalNorm;
            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize(mat3(uModel) * aNorm);
                vLocalPos = aPos;
                vLocalNorm = aNorm;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val fs = """
            #version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            in vec3 vNormal;
            in vec3 vLocalPos;
            in vec3 vLocalNorm;
            uniform vec3 uBaseColor;
            uniform vec3 uEmissiveColor;
            uniform float uEmissiveStrength;
            uniform float uShininess;
            uniform float uAlpha;
            uniform int uMaterialMode;
            uniform vec3 uCameraPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec3 uLightPos1;
            uniform vec3 uLightColor1;
            uniform vec3 uLightPos2;
            uniform vec3 uLightColor2;
            uniform vec3 uLightPos3;
            uniform vec3 uLightColor3;
            uniform sampler2D uFloorMap;
            uniform int uHasFloorMap;
            uniform sampler2D uBuildingMap;
            uniform int uHasBuildingMap;
            uniform vec2 uWallTiles;
            out vec4 fragColor;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float vnoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash21(i);
                float b = hash21(i + vec2(1.0, 0.0));
                float c = hash21(i + vec2(0.0, 1.0));
                float d = hash21(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float fbm(vec2 p) {
                float v = 0.0;
                v += 0.55 * vnoise(p);
                v += 0.30 * vnoise(p * 2.13);
                v += 0.15 * vnoise(p * 4.41);
                return v;
            }

            vec3 calcPointLight(vec3 lPos, vec3 lCol, vec3 norm, vec3 viewDir, vec3 albedo, float specBoost) {
                vec3 toL = lPos - vWorldPos;
                float dist = length(toL);
                vec3 lDir = toL / max(dist, 0.001);
                float atten = 1.0 / (1.0 + 0.032 * dist + 0.0045 * dist * dist);
                float diff = max(dot(norm, lDir), 0.0);
                vec3 halfVec = normalize(lDir + viewDir);
                float spec = pow(max(dot(norm, halfVec), 0.0), max(uShininess, 6.0)) * specBoost;
                return lCol * (diff * albedo + spec) * atten;
            }

            void main() {
                vec3 norm = normalize(vNormal);
                vec3 viewDir = normalize(uCameraPos - vWorldPos);
                vec3 albedo = uBaseColor;
                float specBoost = 0.55;

                vec3 floorGlow = vec3(0.0);

                if (uMaterialMode == 1) {
                    if (uHasFloorMap == 1) {
                        // World-space 3:2 projection matching the 1536x1024 cyberpunk street floor texture
                        vec2 fuv = vec2(vWorldPos.x / 15.0, vWorldPos.z / 10.0);
                        vec2 ff = fract(fuv);
                        float wx0 = smoothstep(0.32, 0.18, abs(ff.x - 0.5));
                        float wy0 = smoothstep(0.32, 0.18, abs(ff.y - 0.5));
                        float w00 = wx0 * wy0;
                        float w10 = (1.0 - wx0) * wy0;
                        float w01 = wx0 * (1.0 - wy0);
                        float w11 = (1.0 - wx0) * (1.0 - wy0);

                        vec3 texCol = vec3(0.0);
                        if (w00 > 0.001) texCol += texture(uFloorMap, fuv).rgb * w00;
                        if (w10 > 0.001) texCol += texture(uFloorMap, fuv + vec2(0.5, 0.0)).rgb * w10;
                        if (w01 > 0.001) texCol += texture(uFloorMap, fuv + vec2(0.0, 0.5)).rgb * w01;
                        if (w11 > 0.001) texCol += texture(uFloorMap, fuv + vec2(0.5, 0.5)).rgb * w11;

                        albedo = texCol * 1.16;
                        // Highlight wet neon puddle reflections & puddles specularly
                        float neonPuddle = max(max(texCol.r, texCol.b) - texCol.g * 0.65, 0.0);
                        floorGlow = max(texCol - vec3(0.32), vec3(0.0)) * 0.42 + texCol * 0.22;
                        specBoost = mix(0.65, 1.85, clamp(neonPuddle * 1.8, 0.0, 1.0));
                    } else {
                        // Procedural fallback: Wet Concrete Stone Tile Floor + Grime + Green Moss + Puddles
                        vec2 uv = vWorldPos.xz;
                        float nLarge = fbm(uv * 0.22);
                        float nFine = fbm(uv * 1.4);

                        vec2 tile = fract(uv * 0.55);
                        float seamX = smoothstep(0.0, 0.04, tile.x) * (1.0 - smoothstep(0.96, 1.0, tile.x));
                        float seamZ = smoothstep(0.0, 0.04, tile.y) * (1.0 - smoothstep(0.96, 1.0, tile.y));
                        float seam = seamX * seamZ;

                        vec3 dampConcrete = mix(vec3(0.10, 0.13, 0.17), vec3(0.18, 0.22, 0.27), nFine);
                        dampConcrete *= mix(0.55, 1.0, seam);

                        float mossMask = smoothstep(0.58, 0.78, nLarge + (1.0 - seam) * 0.25);
                        vec3 mossCol = vec3(0.14, 0.23, 0.15);
                        albedo = mix(dampConcrete, mossCol, mossMask * 0.68);

                        float puddle = smoothstep(0.52, 0.66, fbm(uv * 0.31 + 7.4));
                        albedo = mix(albedo, albedo * 0.52, puddle);
                        specBoost = mix(0.45, 1.65, puddle);
                    }
                } else if (uMaterialMode == 2) {
                    // Weathered Concrete Building Walls + Rain Streaks + Bottom Moss
                    vec2 wallUV = vec2(vWorldPos.x + vWorldPos.z, vWorldPos.y);
                    float grime = fbm(vec2(wallUV.x * 0.7, wallUV.y * 0.22));
                    float fine = fbm(wallUV * 1.8);
                    albedo = uBaseColor * mix(0.68, 1.18, fine) * mix(0.75, 1.05, grime);

                    // Moss creeping up from damp alley base
                    float baseMoss = (1.0 - smoothstep(0.0, 2.8, vWorldPos.y)) * smoothstep(0.42, 0.68, grime);
                    albedo = mix(albedo, vec3(0.15, 0.24, 0.15), baseMoss * 0.85);
                    specBoost = 0.35;
                } else if (uMaterialMode == 3) {
                    // Corrugated Rusted Metal & Containers
                    float ribs = 0.85 + 0.15 * sin((vWorldPos.x + vWorldPos.z) * 14.0);
                    float rustNoise = fbm(vWorldPos.xz * 0.9 + vWorldPos.y * 0.8);
                    vec3 rustCol = vec3(0.34, 0.17, 0.11);
                    albedo = mix(uBaseColor * ribs, rustCol, smoothstep(0.54, 0.78, rustNoise) * 0.72);
                    specBoost = 0.65;
                } else if (uMaterialMode == 4) {
                    // Concrete Steps / Stairs with bright edge wear
                    float stepStripe = smoothstep(0.45, 0.55, fract(vWorldPos.y * 3.8));
                    albedo = mix(uBaseColor * 0.65, uBaseColor * 1.25, stepStripe);
                    specBoost = 0.5;
                } else if (uMaterialMode == 5) {
                    if (uHasBuildingMap == 1 && abs(vLocalNorm.y) < 0.7) {
                        float uCoord = (abs(vLocalNorm.z) >= abs(vLocalNorm.x))
                            ? (vLocalNorm.z >= 0.0 ? (vLocalPos.x + 0.5) : (0.5 - vLocalPos.x))
                            : (vLocalNorm.x >= 0.0 ? (0.5 - vLocalPos.z) : (vLocalPos.z + 0.5));
                        float vCoord = clamp(0.5 - vLocalPos.y, 0.0, 1.0);
                        vec2 buv = vec2(uCoord * max(uWallTiles.x, 1.0), vCoord * max(uWallTiles.y, 1.0));
                        vec3 texCol = texture(uBuildingMap, buv).rgb;
                        albedo = texCol * 1.12;
                        float neonMask = max(max(texCol.r, texCol.b) - texCol.g * 0.45, 0.0);
                        floorGlow = max(texCol - vec3(0.42), vec3(0.0)) * 0.58 + texCol * (0.12 + neonMask * 0.25);
                        specBoost = mix(0.38, 1.15, clamp(neonMask * 1.6, 0.0, 1.0));
                    } else {
                        // Weathered rooftop concrete slab with puddles & HVAC grime
                        float roofGrime = fbm(vWorldPos.xz * 0.85);
                        albedo = mix(uBaseColor * 0.72, uBaseColor * 1.08, roofGrime);
                        specBoost = mix(0.35, 0.95, smoothstep(0.55, 0.78, roofGrime));
                    }
                }

                // Cool moonlit alley directional light
                vec3 dirLight = normalize(vec3(0.45, 0.85, 0.35));
                float ndotl = max(dot(norm, dirLight), 0.0);
                vec3 ambient = albedo * vec3(0.24, 0.31, 0.44);
                vec3 sunTerm = albedo * vec3(0.40, 0.50, 0.66) * ndotl;

                vec3 halfDir = normalize(dirLight + viewDir);
                float dirSpec = pow(max(dot(norm, halfDir), 0.0), max(uShininess, 8.0)) * 0.28 * specBoost;

                vec3 lit = ambient + sunTerm + vec3(dirSpec) + floorGlow;
                lit += calcPointLight(uLightPos1, uLightColor1, norm, viewDir, albedo, specBoost);
                lit += calcPointLight(uLightPos2, uLightColor2, norm, viewDir, albedo, specBoost);
                lit += calcPointLight(uLightPos3, uLightColor3, norm, viewDir, albedo, specBoost);
                lit += uEmissiveColor * uEmissiveStrength;

                float distCam = length(uCameraPos - vWorldPos);
                float fogFactor = clamp(exp(-pow(distCam * uFogDensity, 1.55)), 0.08, 1.0);
                vec3 finalCol = mix(uFogColor, lit, fogFactor);
                fragColor = vec4(finalCol, uAlpha);
            }
        """.trimIndent()

        programId = compileProgram(vs, fs)
        aPos = GLES30.glGetAttribLocation(programId, "aPos")
        aNorm = GLES30.glGetAttribLocation(programId, "aNorm")
        uMVP = GLES30.glGetUniformLocation(programId, "uMVP")
        uModel = GLES30.glGetUniformLocation(programId, "uModel")
        uBaseColor = GLES30.glGetUniformLocation(programId, "uBaseColor")
        uEmissiveColor = GLES30.glGetUniformLocation(programId, "uEmissiveColor")
        uEmissiveStrength = GLES30.glGetUniformLocation(programId, "uEmissiveStrength")
        uShininess = GLES30.glGetUniformLocation(programId, "uShininess")
        uAlpha = GLES30.glGetUniformLocation(programId, "uAlpha")
        uMaterialMode = GLES30.glGetUniformLocation(programId, "uMaterialMode")
        uCameraPos = GLES30.glGetUniformLocation(programId, "uCameraPos")
        uFogColor = GLES30.glGetUniformLocation(programId, "uFogColor")
        uFogDensity = GLES30.glGetUniformLocation(programId, "uFogDensity")
        uLightPos1 = GLES30.glGetUniformLocation(programId, "uLightPos1")
        uLightColor1 = GLES30.glGetUniformLocation(programId, "uLightColor1")
        uLightPos2 = GLES30.glGetUniformLocation(programId, "uLightPos2")
        uLightColor2 = GLES30.glGetUniformLocation(programId, "uLightColor2")
        uLightPos3 = GLES30.glGetUniformLocation(programId, "uLightPos3")
        uLightColor3 = GLES30.glGetUniformLocation(programId, "uLightColor3")
        uFloorMap = GLES30.glGetUniformLocation(programId, "uFloorMap")
        uHasFloorMap = GLES30.glGetUniformLocation(programId, "uHasFloorMap")
        uBuildingMap = GLES30.glGetUniformLocation(programId, "uBuildingMap")
        uHasBuildingMap = GLES30.glGetUniformLocation(programId, "uHasBuildingMap")
        uWallTiles = GLES30.glGetUniformLocation(programId, "uWallTiles")
    }
}

class ParticleLineShader {
    val programId: Int
    val aPos: Int
    val uMVP: Int
    val uColor: Int

    init {
        val vs = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            uniform mat4 uMVP;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val fs = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
        """.trimIndent()

        programId = compileProgram(vs, fs)
        aPos = GLES30.glGetAttribLocation(programId, "aPos")
        uMVP = GLES30.glGetUniformLocation(programId, "uMVP")
        uColor = GLES30.glGetUniformLocation(programId, "uColor")
    }
}

class TexturedGlbShader {
    val programId: Int

    val aPos: Int
    val aNorm: Int
    val aTexCoord: Int

    val uMVP: Int
    val uModel: Int
    val uCameraPos: Int
    val uFogColor: Int
    val uFogDensity: Int
    val uLightPos1: Int
    val uLightColor1: Int
    val uLightPos2: Int
    val uLightColor2: Int
    val uLightPos3: Int
    val uLightColor3: Int
    val uAlbedoMap: Int
    val uEmissiveMap: Int
    val uBaseColorFactor: Int
    val uEmissiveFactor: Int
    val uEmissiveTint: Int
    val uEmissiveBoost: Int
    val uAlphaCutoff: Int
    val uSimpleMaterial: Int
    val uUnlit: Int

    init {
        val vs = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNorm;
            layout(location = 2) in vec2 aTexCoord;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            out vec3 vWorldPos;
            out vec3 vNormal;
            out vec2 vTexCoord;
            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize(mat3(uModel) * aNorm);
                vTexCoord = aTexCoord;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val fs = """
            #version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            in vec3 vNormal;
            in vec2 vTexCoord;
            uniform vec3 uCameraPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec3 uLightPos1;
            uniform vec3 uLightColor1;
            uniform vec3 uLightPos2;
            uniform vec3 uLightColor2;
            uniform vec3 uLightPos3;
            uniform vec3 uLightColor3;
            uniform sampler2D uAlbedoMap;
            uniform sampler2D uEmissiveMap;
            uniform vec4 uBaseColorFactor;
            uniform vec3 uEmissiveFactor;
            uniform vec3 uEmissiveTint;
            uniform float uEmissiveBoost;
            uniform float uAlphaCutoff;
            uniform int uSimpleMaterial;
            uniform int uUnlit;
            out vec4 fragColor;

            vec3 calcPointLight(vec3 lPos, vec3 lCol, vec3 norm, vec3 viewDir, vec3 albedo) {
                vec3 toL = lPos - vWorldPos;
                float dist = length(toL);
                vec3 lDir = toL / max(dist, 0.001);
                float atten = 1.0 / (1.0 + 0.032 * dist + 0.0045 * dist * dist);
                float diff = max(dot(norm, lDir), 0.0);
                vec3 halfVec = normalize(lDir + viewDir);
                float spec = pow(max(dot(norm, halfVec), 0.0), 32.0) * 0.40;
                return lCol * (diff * albedo + spec) * atten;
            }

            void main() {
                vec3 norm = normalize(vNormal + vec3(0.0, 0.0001, 0.0));
                vec3 viewDir = normalize(uCameraPos - vWorldPos);

                vec3 albedo;
                vec3 emitSample;
                float outAlpha = 1.0;

                if (uSimpleMaterial == 1) {
                    // Clean neutral lit clay mode to inspect pure GLB mesh silhouette & proportions
                    albedo = vec3(0.72, 0.76, 0.82);
                    emitSample = vec3(0.0);
                } else {
                    vec4 texCol = texture(uAlbedoMap, vTexCoord) * uBaseColorFactor;
                    if (uAlphaCutoff > 0.0 && texCol.a < uAlphaCutoff) {
                        discard;
                    }
                    albedo = texCol.rgb;
                    outAlpha = texCol.a;
                    emitSample = texture(uEmissiveMap, vTexCoord).rgb * uEmissiveFactor;
                }

                vec3 lit;
                if (uUnlit == 1 && uSimpleMaterial == 0) {
                    lit = albedo + emitSample * uEmissiveTint * uEmissiveBoost;
                } else {
                    vec3 dirLight = normalize(vec3(0.45, 0.85, 0.35));
                    float ndotl = max(dot(norm, dirLight), 0.0);
                    lit = albedo * vec3(0.28, 0.34, 0.46) + albedo * vec3(0.48, 0.56, 0.70) * ndotl;
                    lit += calcPointLight(uLightPos1, uLightColor1, norm, viewDir, albedo);
                    lit += calcPointLight(uLightPos2, uLightColor2, norm, viewDir, albedo);
                    lit += calcPointLight(uLightPos3, uLightColor3, norm, viewDir, albedo);
                    lit += emitSample * uEmissiveTint * uEmissiveBoost;
                }

                float distCam = length(uCameraPos - vWorldPos);
                float fogFactor = clamp(exp(-pow(distCam * uFogDensity, 1.55)), 0.08, 1.0);
                vec3 finalCol = mix(uFogColor, lit, fogFactor);
                fragColor = vec4(finalCol, outAlpha);
            }
        """.trimIndent()

        programId = compileProgram(vs, fs)
        aPos = GLES30.glGetAttribLocation(programId, "aPos")
        aNorm = GLES30.glGetAttribLocation(programId, "aNorm")
        aTexCoord = GLES30.glGetAttribLocation(programId, "aTexCoord")
        uMVP = GLES30.glGetUniformLocation(programId, "uMVP")
        uModel = GLES30.glGetUniformLocation(programId, "uModel")
        uCameraPos = GLES30.glGetUniformLocation(programId, "uCameraPos")
        uFogColor = GLES30.glGetUniformLocation(programId, "uFogColor")
        uFogDensity = GLES30.glGetUniformLocation(programId, "uFogDensity")
        uLightPos1 = GLES30.glGetUniformLocation(programId, "uLightPos1")
        uLightColor1 = GLES30.glGetUniformLocation(programId, "uLightColor1")
        uLightPos2 = GLES30.glGetUniformLocation(programId, "uLightPos2")
        uLightColor2 = GLES30.glGetUniformLocation(programId, "uLightColor2")
        uLightPos3 = GLES30.glGetUniformLocation(programId, "uLightPos3")
        uLightColor3 = GLES30.glGetUniformLocation(programId, "uLightColor3")
        uAlbedoMap = GLES30.glGetUniformLocation(programId, "uAlbedoMap")
        uEmissiveMap = GLES30.glGetUniformLocation(programId, "uEmissiveMap")
        uBaseColorFactor = GLES30.glGetUniformLocation(programId, "uBaseColorFactor")
        uEmissiveFactor = GLES30.glGetUniformLocation(programId, "uEmissiveFactor")
        uEmissiveTint = GLES30.glGetUniformLocation(programId, "uEmissiveTint")
        uEmissiveBoost = GLES30.glGetUniformLocation(programId, "uEmissiveBoost")
        uAlphaCutoff = GLES30.glGetUniformLocation(programId, "uAlphaCutoff")
        uSimpleMaterial = GLES30.glGetUniformLocation(programId, "uSimpleMaterial")
        uUnlit = GLES30.glGetUniformLocation(programId, "uUnlit")
    }
}

private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)
    val status = IntArray(1)
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES30.glGetShaderInfoLog(shader)
        GLES30.glDeleteShader(shader)
        throw RuntimeException("Shader compile error: $log")
    }
    return shader
}

private fun compileProgram(vsSrc: String, fsSrc: String): Int {
    val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSrc)
    val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSrc)
    val prog = GLES30.glCreateProgram()
    GLES30.glAttachShader(prog, vs)
    GLES30.glAttachShader(prog, fs)
    GLES30.glLinkProgram(prog)
    val linkStatus = IntArray(1)
    GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
        val log = GLES30.glGetProgramInfoLog(prog)
        android.util.Log.e("ShaderProgram", "Program link error: $log")
    }
    GLES30.glDeleteShader(vs)
    GLES30.glDeleteShader(fs)
    return prog
}

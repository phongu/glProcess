#version 400 core

uniform sampler2D tex;
uniform float offset;
in vec2 texcoord;
out vec4 color;

void main(void) {

    float bestDistance = 9999.0;
    vec3 bestCoord;

    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec3 sampleValue = texture(tex, texcoord + vec2(x, y) * offset).xyz;
            float distance = length(sampleValue.xy - texcoord);
            if (
                (sampleValue.z != 0.0)&&
                (distance < bestDistance)
            ) {
                bestDistance = distance;
                bestCoord = sampleValue;
            }
        }
    }
    color = vec4(bestCoord, 0);
}

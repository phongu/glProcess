#version 400 core

uniform sampler2D tex;
in vec2 texcoord;
out vec4 color;

void main(void) {
    float value = texture(tex, texcoord).r;
    float threshold = step(0.999, value);
    color = vec4(threshold * texcoord, threshold, 0);
}

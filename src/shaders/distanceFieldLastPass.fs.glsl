#version 400 core

uniform sampler2D tex;
uniform float maxDistance;
in vec2 texcoord;
out float result;

void main(void) {
    vec2 value = texture(tex, texcoord).rg;
    result = clamp( (maxDistance-length(texcoord-value)) / maxDistance, 0, 1);
}
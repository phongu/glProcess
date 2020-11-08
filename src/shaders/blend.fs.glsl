#version 420 core

in vec2 texcoord;

layout(binding=0) uniform sampler2D tex;
layout(binding=1) uniform sampler2D otherTex;
layout(binding=2) uniform sampler2D alpha;
uniform int invert;

out float result;

void main()
{
    float value = texture2D( tex, texcoord).x;
    float otherValue = texture2D( otherTex, texcoord).x;
    float alphaValue = invert + texture2D( alpha, texcoord).x * (1 - 2*invert);
    result = (1-alphaValue) * value + alphaValue * otherValue;
}
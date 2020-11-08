#version 400 core

in vec2 texcoord;

uniform sampler2D tex;
uniform float     textureSize;
uniform float     radius;
uniform vec2      direction;

out float result;

void main()
{
    float value = texture2D( tex, texcoord).x;
    vec2 accumulatedValue = vec2( value , 1);

    for ( int x = 1; x <= radius; ++ x ) {
        float weight = exp(-float(x*x) / (0.17 * radius * radius));
        value = texture2D(tex, texcoord + (x * direction / textureSize)).x;
        accumulatedValue += vec2(value * weight, weight);
        value = texture2D(tex, texcoord - (x * direction / textureSize)).x;
        accumulatedValue += vec2(value * weight, weight);

    }
    accumulatedValue.x = accumulatedValue.x / accumulatedValue.y;
    result = accumulatedValue.r;
}
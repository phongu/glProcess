#version 400 core

uniform sampler2D tex;
in vec2 texcoord;
out vec4 color;

float sRGB(float x) {
  float threshold = step(0.00031308, x);
  return 12.92 * x * (1-threshold) + (threshold)*1.055*pow(x,(1.0 / 2.4) ) - 0.055;
}
vec4 sRGB_v4(vec4 c) {
  return vec4(sRGB(c.r),sRGB(c.g),sRGB(c.b), sRGB(c.a));
}
void main(void) {
  float value = sRGB(texture(tex, texcoord).r);
  color = vec4(value, value, value, 1);
  //  color = sRGB_v4(texture(tex, texcoord));
}

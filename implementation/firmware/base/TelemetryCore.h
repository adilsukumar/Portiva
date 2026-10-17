#pragma once
#include "ControlCore.h"
namespace rollingtable {
inline bool validTelemetry(const char* s,size_t n){
  if(n<38||n>159||s[0]!='S'||s[1]!=',')return false;
  const char* end=s+n-5;if(*end!='*')return false;uint16_t expected=0;
  for(uint8_t i=1;i<=4;i++){char h=end[i];uint8_t digit;if(h>='0'&&h<='9')digit=h-'0';else if(h>='A'&&h<='F')digit=h-'A'+10;else return false;expected=(expected<<4)|digit;}
  if(expected!=crc16(s,end-s))return false;
  const uint32_t limits[16]={0xffffffffUL,15,4000,4000,4000,4000,0xffffffffUL,0xffffffffUL,1,255,0xffffffffUL,0xffffffffUL,1,32768,1023,0};
  const char* p=s+2;
  for(uint8_t field=0;field<16;field++){
    bool negative=field==13&&p<end&&*p=='-';if(negative)p++;
    uint32_t value;if(!number(p,end,limits[field],value)||(field==13&&!negative&&value>32767))return false;
    if(field<15){if(p==end||*p++!=',')return false;}else if(p!=end)return false;
  }return true;
}
}

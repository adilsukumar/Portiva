#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>

namespace rollingtable {
inline uint16_t crc16(const char* s,size_t n) {
  uint16_t c=0xffff;
  for(size_t i=0;i<n;i++){c^=(uint16_t)(uint8_t)s[i]<<8;for(uint8_t j=0;j<8;j++)c=(c&0x8000)?(uint16_t)((c<<1)^0x1021):(uint16_t)(c<<1);}
  return c;
}
struct Command {uint32_t seq;uint16_t ttl;int16_t left,right;};
inline bool number(const char*& p,const char* end,uint32_t limit,uint32_t& value) {
  if(p==end||*p<'0'||*p>'9')return false;
  value=0;
  while(p<end&&*p>='0'&&*p<='9'){uint32_t digit=*p-'0';if(value>limit/10||(value==limit/10&&digit>limit%10))return false;value=value*10+digit;p++;}
  return true;
}
inline bool decode(const char* s,size_t n,Command& out) {
  if(n<14||n>70||strncmp(s,"D,",2)!=0)return false;
  const char* crcSep=nullptr;for(size_t i=0;i<n;i++)if(s[i]=='*')crcSep=s+i;
  if(!crcSep||s+n-crcSep!=5)return false;
  uint16_t expected=0;
  for(int i=1;i<=4;i++){char h=crcSep[i];unsigned digit;if(h>='0'&&h<='9')digit=h-'0';else if(h>='A'&&h<='F')digit=h-'A'+10;else return false;expected=(expected<<4)|digit;}
  if(expected!=crc16(s,crcSep-s))return false;
  const char* p=s+2;uint32_t value;
  if(!number(p,crcSep,0xffffffffu,out.seq)||p==crcSep||*p++!=',')return false;
  if(!number(p,crcSep,250,value)||value<20||p==crcSep||*p++!=',')return false;
  out.ttl=value;
  int16_t* motors[]={&out.left,&out.right};
  for(int i=0;i<2;i++){bool negative=p<crcSep&&*p=='-';if(negative)p++;
    if(!number(p,crcSep,80,value))return false;
    *motors[i]=negative?-(int16_t)value:(int16_t)value;
    if(i==0&&(p==crcSep||*p++!=','))return false;
  }
  return p==crcSep;
}
class ControlCore {
public:
  int16_t left=0,right=0;bool armed=false;const char* reason="startup";
  bool safetyClear=false,commissioned=false;
  void stop(const char* why){left=right=0;armed=false;reason=why;}
  void safety(bool clear){safetyClear=clear;if(!clear)stop("safety-input");}
  bool deliberateArm(uint32_t now) {
    if(!commissioned||!safetyClear)return false;
    left=right=0;armed=true;deadlineStart=now;ttl=250;reason="armed-at-rest";return true;
  }
  bool receive(const char* s,size_t n,uint32_t now) {
    Command c;
    if(!decode(s,n,c)){stop("invalid-packet");return false;}
    if(haveSequence&&(uint32_t)(c.seq-lastSequence)>=0x80000000u){stop("sequence-replay");return false;}
    if(haveSequence&&c.seq==lastSequence){stop("sequence-replay");return false;}
    haveSequence=true;lastSequence=c.seq;
    if(c.left==0&&c.right==0){stop("command-stop");return true;}
    tick(now);
    if(!armed||!safetyClear||!commissioned){stop("not-armed");return false;}
    // Reverse requests first stop and disarm; prevents immediate electrical reversal.
    if((left*c.left<0)||(right*c.right<0)){stop("direction-reversal");return false;}
    deadlineStart=now;ttl=c.ttl;left=c.left;right=c.right;reason="bounded-command";return true;
  }
  void tick(uint32_t now){if(armed&&(uint32_t)(now-deadlineStart)>=ttl)stop("watchdog");}
  void resetSession(){stop("session-reset");haveSequence=false;}
private:
  bool haveSequence=false;uint32_t lastSequence=0,deadlineStart=0;uint16_t ttl=250;
};
}

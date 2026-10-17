#pragma once
#include <stdint.h>
namespace rollingtable {
// Nonblocking sequential sonar scheduler. Distances are measurements, not obstacle clearance certification.
class SonarCore {
public:
  struct Action {int8_t channel,level;};
  uint16_t mm[4]={0,0,0,0};uint32_t sampled[4]={0,0,0,0};uint8_t validMask=0;
  uint8_t channel=0;
  bool busy()const{return phase!=0;}
  bool fresh(uint8_t i,uint32_t now)const{return i<4&&(validMask&(1<<i))&&(uint32_t)(now-sampled[i])<=350000UL;}
  Action tick(uint32_t now,bool echo){
    Action none={-1,-1};
    if(phase==0){
      if(started&&(uint32_t)(now-cycleStart)<60000UL)return none;
      started=true;cycleStart=now;
      if(echo){finish(now,0);return none;}phase=1;phaseAt=now;return {(int8_t)channel,1};
    }
    if(phase==1){if((uint32_t)(now-phaseAt)>=10){phase=2;phaseAt=now;return {(int8_t)channel,0};}return none;}
    if((uint32_t)(now-phaseAt)>30000UL){finish(now,0);return none;}
    if(phase==2&&echo){riseAt=now;phase=3;}
    else if(phase==3&&!echo){uint32_t duration=now-riseAt;uint32_t distance=duration*343UL/2000UL;finish(now,duration<=25000&&distance>=20&&distance<=4000?(uint16_t)distance:0);}
    return none;
  }
private:
  uint8_t phase=0;bool started=false;uint32_t cycleStart=0,phaseAt=0,riseAt=0;
  void finish(uint32_t now,uint16_t distance){mm[channel]=distance;sampled[channel]=now;if(distance)validMask|=1<<channel;else validMask&=~(1<<channel);channel=(channel+1)%4;phase=0;}
};
class DebouncedInput {
public:
  bool pressed=false;
  bool sample(bool active,uint32_t now){
    if(!active)releasedSeen=true;
    if(!initialized){candidate=active;changed=now;initialized=true;return false;}
    if(candidate!=active){candidate=active;changed=now;}
    if(candidate!=pressed&&(uint32_t)(now-changed)>=40){pressed=candidate;return pressed&&releasedSeen;}
    return false;
  }
  void disconnected(){pressed=false;candidate=false;initialized=false;releasedSeen=false;}
private:
  bool initialized=false,candidate=false,releasedSeen=false;uint32_t changed=0;
};
}

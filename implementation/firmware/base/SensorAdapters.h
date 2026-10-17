#pragma once
#include <Arduino.h>
#include <Wire.h>
#include <util/atomic.h>
#include "SensorCore.h"
namespace rollingtable {
static volatile uint32_t leftEdges=0,rightEdges=0;
static void leftEdge(){leftEdges++;}static void rightEdge(){rightEdges++;}
class SensorAdapters {
public:
  SonarCore ranges;uint32_t followEvents=0,sendEvents=0;bool pcfOK=false,mpuOK=false;
  uint8_t pcfBits=255;int16_t gyroZRaw=0;uint16_t batteryADC=0;
  // Candidate addresses/pins; verify actual breakouts and voltage domains before wiring.
  static constexpr uint8_t PCF=0x20,MPU=0x68;
  void begin(){
    for(uint8_t i=0;i<4;i++){pinMode(trig[i],OUTPUT);digitalWrite(trig[i],LOW);pinMode(echo[i],INPUT);}
    pinMode(2,INPUT_PULLUP);pinMode(3,INPUT_PULLUP);attachInterrupt(digitalPinToInterrupt(2),leftEdge,RISING);attachInterrupt(digitalPinToInterrupt(3),rightEdge,RISING);
    Wire.setWireTimeout(2500,true);uint8_t who=0;
    mpuOK=readReg(MPU,0x75,&who,1)&&who==0x68&&writeReg(MPU,0x6B,0)&&writeReg(MPU,0x1A,3)&&writeReg(MPU,0x1B,0);
    Wire.beginTransmission(PCF);Wire.write(255);pcfOK=Wire.endTransmission()==0;
  }
  void poll(){
    uint32_t us=micros();SonarCore::Action action=ranges.tick(us,digitalRead(echo[ranges.channel])==HIGH);
    if(action.channel>=0)digitalWrite(trig[(uint8_t)action.channel],action.level?HIGH:LOW);
    uint32_t now=millis();if(ranges.busy()||(uint32_t)(now-auxAt)<50)return;auxAt=now;
    // I2C/ADC work occurs between acoustic measurements, not during an echo pulse.
    Wire.clearWireTimeoutFlag();pcfOK=Wire.requestFrom(PCF,(uint8_t)1)==1;
    if(pcfOK){pcfBits=Wire.read();if(follow.sample(!(pcfBits&1),now))followEvents++;if(send.sample(!(pcfBits&2),now))sendEvents++;}
    else{pcfBits=255;follow.disconnected();send.disconnected();}
    if(mpuOK){uint8_t raw[2];mpuOK=readReg(MPU,0x47,raw,2);if(mpuOK)gyroZRaw=(int16_t)((uint16_t)raw[0]<<8|raw[1]);}
    if(Wire.getWireTimeoutFlag()){pcfOK=mpuOK=false;follow.disconnected();send.disconnected();}
    batteryADC=analogRead(A3);
  }
  uint8_t freshMask(uint32_t nowUs)const{uint8_t mask=0;for(uint8_t i=0;i<4;i++)if(ranges.fresh(i,nowUs))mask|=1<<i;return mask;}
  void edges(uint32_t& left,uint32_t& right)const{ATOMIC_BLOCK(ATOMIC_RESTORESTATE){left=leftEdges;right=rightEdges;}}
private:
  const uint8_t trig[4]={4,8,12,A1},echo[4]={7,11,A0,A2};
  DebouncedInput follow,send;uint32_t auxAt=0;
  static bool writeReg(uint8_t addr,uint8_t reg,uint8_t value){Wire.beginTransmission(addr);Wire.write(reg);Wire.write(value);return Wire.endTransmission()==0;}
  static bool readReg(uint8_t addr,uint8_t reg,uint8_t* out,uint8_t count){Wire.beginTransmission(addr);Wire.write(reg);if(Wire.endTransmission(false)!=0||Wire.requestFrom(addr,count)!=count)return false;for(uint8_t i=0;i<count;i++)out[i]=Wire.read();return true;}
};
}

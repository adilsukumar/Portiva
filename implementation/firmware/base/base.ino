#include <Wire.h>
#include "ControlCore.h"
#include "SensorAdapters.h"
using namespace rollingtable;
// Compile-time lock: this version is a bench prototype, not commissioned motion firmware.
constexpr bool ALLOW_MOTION=false;
constexpr uint8_t LP=5,LN=6,RP=9,RN=10,ENABLE=13;
ControlCore controller;SensorAdapters sensors;char line[80];uint8_t used=0;uint32_t statusAt=0;
void outputs(){
  // Final enable also needs the documented independent physical NC stop chain.
  bool enable=ALLOW_MOTION&&controller.armed&&controller.safetyClear;
  digitalWrite(ENABLE,enable?HIGH:LOW);
  analogWrite(LP,enable&&controller.left>0?controller.left:0);analogWrite(LN,enable&&controller.left<0?-controller.left:0);
  analogWrite(RP,enable&&controller.right>0?controller.right:0);analogWrite(RN,enable&&controller.right<0?-controller.right:0);
}
void setup(){pinMode(ENABLE,OUTPUT);digitalWrite(ENABLE,LOW);const uint8_t pins[]={LP,LN,RP,RN};for(uint8_t p:pins){pinMode(p,OUTPUT);analogWrite(p,0);}
  Serial.begin(115200);Wire.begin();sensors.begin();controller.commissioned=ALLOW_MOTION;controller.safety(false);}
void loop(){
  uint32_t now=millis();controller.tick(now);
  sensors.poll();uint8_t bytes=0;
  while(Serial.available()&&bytes++<32){
    char c=Serial.read();if(c=='\n'){controller.receive(line,used,now);used=0;}
    else if(c!='\r'){if(used<sizeof(line)-1)line[used++]=c;else{used=0;controller.stop("serial-overflow");}}
  }
  // Sensors, loaded stopping calibration and hard stop latch are not yet qualified.
  // Keep safety(false); never replace this with an unconditional true on the real trolley.
  controller.safety(false);outputs();
  if(now-statusAt>=250&&!sensors.ranges.busy()){
    statusAt=now;uint32_t left,right;sensors.edges(left,right);char body[160],packet[168];
    // Last field is literal0: motion disabled. ADC/gyro are raw, uncalibrated readings.
    snprintf(body,sizeof(body),"S,%lu,%u,%u,%u,%u,%u,%lu,%lu,%u,%u,%lu,%lu,%u,%d,%u,0",
      (unsigned long)now,sensors.freshMask(micros()),sensors.ranges.mm[0],sensors.ranges.mm[1],sensors.ranges.mm[2],sensors.ranges.mm[3],
      (unsigned long)left,(unsigned long)right,sensors.pcfOK,sensors.pcfBits,(unsigned long)sensors.followEvents,(unsigned long)sensors.sendEvents,sensors.mpuOK,sensors.gyroZRaw,sensors.batteryADC);
    snprintf(packet,sizeof(packet),"%s*%04X",body,crc16(body,strlen(body)));Serial.println(packet);
  }
}

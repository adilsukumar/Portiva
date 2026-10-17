#include "../firmware/base/ControlCore.h"
#include <cassert>
#include <cstdio>
#include <cstring>
#include <string>
using namespace rollingtable;
int checks=0;
void check(bool pass){assert(pass);checks++;}
std::string packet(unsigned seq,int left,int right,unsigned ttl=200){char s[80];sprintf(s,"D,%u,%u,%d,%d",seq,ttl,left,right);char h[8];sprintf(h,"*%04X",crc16(s,strlen(s)));return std::string(s)+h;}
bool send(ControlCore& c,const std::string& s,unsigned now){return c.receive(s.data(),s.size(),now);}
int main(){
  ControlCore c;check(!c.deliberateArm(0));c.commissioned=true;c.safety(true);check(c.deliberateArm(0));
  check(send(c,packet(1,40,40),10));check(c.left==40);c.tick(209);check(c.left==40);c.tick(210);check(c.left==0&&!c.armed);
  check(!send(c,packet(2,40,40),211));check(c.left==0);check(c.deliberateArm(212));
  check(send(c,packet(3,40,40),220));check(!send(c,packet(3,40,40),230));check(!c.armed);
  c.deliberateArm(240);check(send(c,packet(4,40,40),245));c.safety(false);check(c.left==0&&!c.armed);
  c.safety(true);check(!send(c,packet(5,40,40),246));check(c.deliberateArm(250));
  check(!send(c,packet(6,81,40),251));check(c.left==0);
  c.deliberateArm(300);std::string bad=packet(7,40,40);bad[3]='9';check(!send(c,bad,301));
  c.deliberateArm(400);check(send(c,packet(8,40,40),401));check(!send(c,packet(9,-40,40),402));check(c.left==0);
  c.deliberateArm(500);check(!send(c,packet(10,40,40,251),501));
  c.deliberateArm(600);check(send(c,packet(11,40,40),601));check(send(c,packet(12,0,0),602));check(!c.armed);
  c.resetSession();c.deliberateArm(0xfffffff0u);check(send(c,packet(0xffffffffu,40,40),0xfffffff8u));
  check(send(c,packet(0,40,40),5u));c.tick(205u);check(c.left==0&&!c.armed);
  for(unsigned i=0;i<2000;i++){ControlCore q;q.commissioned=true;q.safety(true);q.deliberateArm(100);std::string fuzz=packet(1,40,40);fuzz[i%fuzz.size()]^=1+(i%127);check(!send(q,fuzz,101));check(q.left==0);}
  printf("%d C++ packet/watchdog/fault checks passed; no motor hardware test.\n",checks);
}

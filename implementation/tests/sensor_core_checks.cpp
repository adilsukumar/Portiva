#include "../firmware/base/SensorCore.h"
#include <cassert>
#include <cstdio>
using namespace rollingtable;
int checks=0;void check(bool b){assert(b);checks++;}
int main(){
  SonarCore s;check(!s.fresh(0,0));auto a=s.tick(0,false);check(a.channel==0&&a.level==1);
  check(s.tick(9,false).channel==-1);check(s.tick(10,false).level==0);
  s.tick(100,true);s.tick(1100,false);check(s.mm[0]==171&&s.fresh(0,1100));
  check(s.tick(59999,false).channel==-1);check(s.tick(60000,false).channel==1);s.tick(60010,false);s.tick(90100,false);check(!s.fresh(1,90100));
  check(s.tick(120000,true).channel==-1);check(s.channel==3&&!s.fresh(2,120000));
  check(!s.fresh(0,352000));
  SonarCore wrap;wrap.tick(0xfffffff0UL,false);wrap.tick(0xfffffffaUL,false);wrap.tick(10,true);wrap.tick(1010,false);check(wrap.mm[0]==171&&wrap.fresh(0,1020));
  DebouncedInput b;check(!b.sample(false,0));check(!b.sample(true,5));check(!b.sample(false,10));check(!b.sample(true,15));check(!b.sample(true,54));check(b.sample(true,55));check(!b.sample(true,100));
  b.disconnected();check(!b.pressed);check(!b.sample(false,110));check(!b.sample(true,111));check(b.sample(true,151));
  DebouncedInput held;check(!held.sample(true,0));check(!held.sample(true,50));check(!held.sample(true,100));check(!held.sample(false,110));check(!held.sample(false,160));check(!held.sample(true,170));check(held.sample(true,210));
  printf("%d sonar timing/timeout/rollover/button assertions passed; not acoustic or electrical validation.\n",checks);
}

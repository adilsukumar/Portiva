#include "../firmware/base/TelemetryCore.h"
#include <string>
#include <cstdio>
#include <cassert>
using namespace rollingtable;
std::string frame(std::string s){char checksum[8];snprintf(checksum,sizeof(checksum),"*%04X",crc16(s.data(),s.size()));return s+checksum;}
bool valid(std::string s){return validTelemetry(s.data(),s.size());}
int main(){int checks=0;auto check=[&](bool b){assert(b);checks++;};
  std::string body="S,1000,15,500,600,700,800,100,200,1,255,4,5,1,-120,512,0";std::string good=frame(body);check(valid(good));
  check(valid(frame("S,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0")));
  check(valid(frame("S,1,0,0,0,0,0,0,0,0,0,0,0,1,-32768,0,0")));
  for(size_t i=0;i<good.size();i++){auto corrupt=good;corrupt[i]^=1;check(!valid(corrupt));}
  check(!valid(frame("S,1000,16,500,600,700,800,100,200,1,255,4,5,1,-120,512,0")));
  check(!valid(frame("S,1000,15,500,600,700,800,100,200,1,255,4,5,1,32768,512,0")));
  check(!valid(frame("S,1000,15,500,600,700,800,100,200,1,255,4,5,1,-120,512,1")));
  check(!valid(frame(body+",0")));check(!valid(frame("S,1,15,500,600,700,800,100,200,1,255,4,5,1,-120,1024,0")));
  printf("%d telemetry framing/checksum/bounds assertions passed; not physical UART validation.\n",checks);
}

#include <WiFi.h>
#include <WebServer.h>
#include "esp_camera.h"
#include "secrets.h"
#include "../base/ControlCore.h"
#include "../base/TelemetryCore.h"
WebServer server(80);uint32_t sequence=0;
char telemetry[168]={0},incoming[168];uint16_t incomingSize=0;uint32_t telemetryAt=0;bool haveTelemetry=false,overflow=false;
void pollBase(){
  uint16_t count=0;while(Serial.available()&&count++<256){char c=Serial.read();
    if(c=='\n'){
      if(!overflow&&rollingtable::validTelemetry(incoming,incomingSize)){memcpy(telemetry,incoming,incomingSize);telemetry[incomingSize]=0;telemetryAt=millis();haveTelemetry=true;}
      incomingSize=0;overflow=false;
    }else if(c!='\r'){if(incomingSize<sizeof(incoming)-1)incoming[incomingSize++]=c;else overflow=true;}
  }
}
bool authorized(){return strlen(ROBOT_PAIRING_TOKEN)>=32 && server.hasHeader("X-Robot-Token")&&server.header("X-Robot-Token")==ROBOT_PAIRING_TOKEN;}
void stopBase(){char body[50],packet[65];snprintf(body,sizeof(body),"D,%lu,200,0,0",(unsigned long)++sequence);
  snprintf(packet,sizeof(packet),"%s*%04X\n",body,rollingtable::crc16(body,strlen(body)));Serial.print(packet);}
void setup(){
  Serial.begin(115200);
  if(strlen(ROBOT_WIFI_PASSWORD)<12||strlen(ROBOT_PAIRING_TOKEN)<32){while(true)delay(1000);}
  camera_config_t c={};c.ledc_channel=LEDC_CHANNEL_0;c.ledc_timer=LEDC_TIMER_0;
  // Ai-Thinker pin map ONLY; verify the actual board before flashing.
  c.pin_d0=5;c.pin_d1=18;c.pin_d2=19;c.pin_d3=21;c.pin_d4=36;c.pin_d5=39;c.pin_d6=34;c.pin_d7=35;
  c.pin_xclk=0;c.pin_pclk=22;c.pin_vsync=25;c.pin_href=23;c.pin_sccb_sda=26;c.pin_sccb_scl=27;c.pin_pwdn=32;c.pin_reset=-1;
  c.xclk_freq_hz=20000000;c.pixel_format=PIXFORMAT_JPEG;c.frame_size=FRAMESIZE_VGA;c.jpeg_quality=12;c.fb_count=1;
  if(!psramFound()||esp_camera_init(&c)!=ESP_OK){while(true){stopBase();delay(250);}}
  WiFi.mode(WIFI_AP);WiFi.softAP(ROBOT_WIFI_SSID,ROBOT_WIFI_PASSWORD);
  const char* headers[]={"X-Robot-Token"};server.collectHeaders(headers,1);
  server.on("/health",HTTP_GET,[]{
    if(!authorized()){server.send(401,"text/plain","pairing required");return;}
    sensor_t* sensor=esp_camera_sensor_get();
    char response[260];snprintf(response,sizeof(response),"{\"firmware\":\"Portiva-camera-bench-v1\",\"camera_pid\":%u,\"psram_bytes\":%lu,\"free_heap_bytes\":%lu,\"uptime_ms\":%lu,\"motion_enabled\":false}",sensor?(unsigned)sensor->id.PID:0,(unsigned long)ESP.getPsramSize(),(unsigned long)ESP.getFreeHeap(),(unsigned long)millis());
    server.sendHeader("Cache-Control","no-store");server.send(200,"application/json",response);
  });
  server.on("/capture",HTTP_GET,[]{
    if(!authorized()){server.send(401,"text/plain","pairing required");return;}
    camera_fb_t* frame=esp_camera_fb_get();if(!frame){stopBase();server.send(503,"text/plain","camera unavailable");return;}
    server.sendHeader("Cache-Control","no-store");server.setContentLength(frame->len);server.send(200,"image/jpeg","");
    WiFiClient client=server.client();client.write(frame->buf,frame->len);esp_camera_fb_return(frame);
  });
  server.on("/stop",HTTP_POST,[]{if(!authorized()){server.send(401,"text/plain","pairing required");return;}stopBase();server.send(200,"text/plain","stopped");});
  server.on("/telemetry",HTTP_GET,[]{
    if(!authorized()){server.send(401,"text/plain","pairing required");return;}
    if(!haveTelemetry||(uint32_t)(millis()-telemetryAt)>600){server.send(503,"text/plain","base telemetry stale or absent");return;}
    char response[240];snprintf(response,sizeof(response),"{\"age_ms\":%lu,\"packet\":\"%s\",\"motion_enabled\":false}",(unsigned long)(millis()-telemetryAt),telemetry);
    server.sendHeader("Cache-Control","no-store");server.send(200,"application/json",response);
  });
  // Deliberately no steering endpoint in this uncommissioned bench firmware.
  server.begin();stopBase();
}
void loop(){pollBase();server.handleClient();}

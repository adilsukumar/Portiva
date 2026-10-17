import in.rollingtable.prototype.BaseTelemetry;
public class TelemetryChecks {
  static int checks;static void check(boolean b){if(!b)throw new AssertionError("telemetry "+checks);checks++;}
  static String packet(String body){int crc=0xffff;for(char c:body.toCharArray()){crc^=c<<8;for(int j=0;j<8;j++)crc=(crc&0x8000)!=0?((crc<<1)^0x1021)&0xffff:(crc<<1)&0xffff;}return body+String.format("*%04X",crc);}
  static BaseTelemetry t(long time,long follow,long send){return BaseTelemetry.decode(packet("S,"+time+",15,500,600,700,800,100,200,1,255,"+follow+","+send+",1,-120,512,0"));}
  public static void main(String[] args){
    BaseTelemetry a=t(1000,4,5);check(a.fields[13]==-120);check(a.fields[2]==500);BaseTelemetry.ButtonEvents events=new BaseTelemetry.ButtonEvents();
    check(BaseTelemetry.decode(packet("S,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0")).fields[0]==0);
    check(BaseTelemetry.decode(packet("S,1,0,0,0,0,0,0,0,0,0,0,0,1,-32768,0,0")).fields[13]==-32768);
    check(events.accept(a)==0);check(events.accept(t(1250,5,5))==1);check(events.accept(t(1250,5,5))==0);check(events.accept(t(1500,5,6))==2);
    check(events.accept(t(1750,7,6))==-1);check(events.accept(t(2000,7,6))==0);check(events.accept(t(250,0,0))==-1);check(events.accept(t(500,0,0))==0);
    check(events.accept(null)==-1);check(events.accept(t(1000,9,9))==0);check(events.accept(t(1250,10,10))==-1);
    String good=packet("S,1000,15,500,600,700,800,100,200,1,255,4,5,1,-120,512,0");
    for(int i=0;i<good.length();i++){String corrupt=good.substring(0,i)+(char)(good.charAt(i)^1)+good.substring(i+1);boolean bad=false;try{BaseTelemetry.decode(corrupt);}catch(IllegalArgumentException e){bad=true;}check(bad);}
    boolean bad=false;try{BaseTelemetry.decode(packet("S,1000,15,500,600,700,800,100,200,1,255,4,5,1,-120,512,1"));}catch(IllegalArgumentException e){bad=true;}check(bad);
    System.out.println(checks+" Java telemetry/button event assertions passed; no actuation path.");
  }
}

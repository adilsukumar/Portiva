package in.rollingtable.prototype;

/** Strict read-only decoder for the base bench packet. CRC is corruption detection, not authentication. */
public final class BaseTelemetry {
  public final long[] fields;
  private BaseTelemetry(long[] fields){this.fields=fields;}
  public static BaseTelemetry decode(String packet){
    if(packet==null||packet.length()>159||packet.length()<38)throw new IllegalArgumentException("packet size");
    int star=packet.length()-5;if(packet.charAt(star)!='*')throw new IllegalArgumentException("checksum framing");
    int crc=0xffff;for(int i=0;i<star;i++){char c=packet.charAt(i);if(c>127)throw new IllegalArgumentException("ASCII required");crc^=c<<8;for(int j=0;j<8;j++)crc=(crc&0x8000)!=0?((crc<<1)^0x1021)&0xffff:(crc<<1)&0xffff;}
    String hex=packet.substring(star+1);if(!hex.matches("[0-9A-F]{4}")||Integer.parseInt(hex,16)!=crc)throw new IllegalArgumentException("bad checksum");
    String[] values=packet.substring(0,star).split(",",-1);if(values.length!=17||!values[0].equals("S"))throw new IllegalArgumentException("field count");
    long[] max={0xffffffffL,15,4000,4000,4000,4000,0xffffffffL,0xffffffffL,1,255,0xffffffffL,0xffffffffL,1,32767,1023,0};long[] fields=new long[16];
    for(int i=0;i<16;i++){String value=values[i+1];if(!value.matches(i==13?"-?[0-9]{1,10}":"[0-9]{1,10}"))throw new IllegalArgumentException("numeric field");long n=Long.parseLong(value);if(n>max[i]||n<(i==13?-32768:0))throw new IllegalArgumentException("field bound");fields[i]=n;}
    return new BaseTelemetry(fields);
  }
  public String summary(){return "Base range mask "+fields[1]+"; raw mm F/L/R/B "+fields[2]+"/"+fields[3]+"/"+fields[4]+"/"+fields[5]+"; PCF "+fields[8]+"; FOLLOW/SEND counts "+fields[10]+"/"+fields[11]+"; IMU "+fields[12];}
  public static final class ButtonEvents {
    private boolean initialized;private long baseTime,follow,send;
    // Result is a BENCH event code only: 0 none,1 follow,2 send,-1 ambiguous/disconnected/reboot.
    public int accept(BaseTelemetry t){
      if(t==null||t.fields[8]!=1){initialized=false;return -1;}
      long now=t.fields[0],f=t.fields[10],s=t.fields[11];
      if(!initialized){initialized=true;baseTime=now;follow=f;send=s;return 0;}
      long elapsed=(now-baseTime)&0xffffffffL,df=(f-follow)&0xffffffffL,ds=(s-send)&0xffffffffL;
      baseTime=now;follow=f;send=s;
      if(elapsed>=0x80000000L||df>1||ds>1||(df==1&&ds==1))return -1;
      return df==1?1:ds==1?2:0;
    }
  }
}

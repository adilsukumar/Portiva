"""Create local camera credentials. No upload, flash or motor action."""
from pathlib import Path
import argparse,json,secrets

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--force',action='store_true',help='Replace existing LOCAL bench credentials');a=p.parse_args()
    root=Path(__file__).resolve().parent;target=root/'firmware/camera/secrets.h';private=root/'config/camera-private.json'
    if (target.exists() or private.exists()) and not a.force:raise SystemExit('Credentials already exist. They were preserved. Use --force only to deliberately replace them.')
    password=secrets.token_urlsafe(18);token=secrets.token_hex(32);ssid='Portiva-'+secrets.token_hex(3)
    header='#pragma once\n// PRIVATE LOCAL FILE. Never commit or share.\n'+''.join('static const char* '+key+'='+json.dumps(value)+';\n' for key,value in [('ROBOT_WIFI_SSID',ssid),('ROBOT_WIFI_PASSWORD',password),('ROBOT_PAIRING_TOKEN',token)])
    target.write_text(header,encoding='utf-8');private.write_text(json.dumps(dict(ssid=ssid,password=password,token=token,url='http://192.168.4.1'),indent=2)+'\n',encoding='utf-8')
    print('Created local credentials in '+str(private)+'. Values were not printed. Keep this file private.')
if __name__=='__main__':main()

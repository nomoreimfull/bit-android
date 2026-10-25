"""Host checks: original timer parity, geometry integrity, exact sound extraction, PCM, APK."""
import sys,pathlib,struct,json,hashlib,wave,zipfile,subprocess,os,math
R=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(R));import build
os.chdir(R)
meta=json.loads((R/'analysis/recovered.json').read_text())
for rid,name in [(105,'no'),(106,'yes')]:
 wav=(R/f'analysis/resource-{rid}.wav').read_bytes();pos=12
 while pos+8<=len(wav):
  tag=wav[pos:pos+4];n=struct.unpack_from('<I',wav,pos+4)[0]
  if tag==b'data':assert (R/f'app/src/main/assets/{name}.mp3').read_bytes()==wav[pos+8:pos+8+n]
  pos+=8+n+(n%2)
 mp3=(R/f'app/src/main/assets/{name}.mp3').read_bytes();pos=0;frames=0
 while pos<len(mp3):
  h=int.from_bytes(mp3[pos:pos+4],'big');assert h>>21==0x7ff and ((h>>19)&3)==0 and ((h>>17)&3)==1
  rate=[11025,12000,8000][(h>>10)&3];br=[0,8,16,24,32,40,48,56,64,80,96,112,128,144,160][(h>>12)&15]
  assert rate==11025 and br in (16,24)
  pos+=72000*br//rate+((h>>9)&1);frames+=1
 assert pos==len(mp3) and frames==(24 if name=='yes' else 12)
 with wave.open(str(R/f'app/src/main/assets/{name}.wav')) as f:
  assert (f.getnchannels(),f.getsampwidth(),f.getframerate())==(1,2,11025)
  assert f.getnframes()==(13824 if name=='yes' else 6912)
for i,expected in [(1,60),(2,108),(3,24),(4,360)]:
 data=(R/f'app/src/main/assets/mesh{i}.bin').read_bytes();assert len(data)==expected*24
 vals=struct.unpack('<'+'f'*(len(data)//4),data)
 assert all(math.isfinite(v) for v in vals)
 # every captured original vertex, after the deliberate double->float conversion
 original=json.loads((R/'analysis/meshes.json').read_text())[str(i)]
 assert data==struct.pack('<'+'f'*(len(data)//4),*[x for v in original for x in v])
fixture=R/'build/timer-fixture.txt';fixture.parent.mkdir(exist_ok=True)
fixture.write_text('\n'.join(map(str,json.loads((R/'analysis/timer-probe.json').read_text())['0x40ee18']['phases'])))
libs,std=build.compiler();classes=R/'build/checks';classes.mkdir(exist_ok=True)
build.run('java','-Xmx768m','-cp',os.pathsep.join(map(str,libs)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',std,'-d',classes,R/'app/src/main/java/org/sksoft/bit/Model.kt',R/'tests/ModelChecks.kt')
build.run('java','-cp',os.pathsep.join(map(str,[classes,std])),'org.sksoft.bit.ModelChecksKt',fixture)
apk=R/'build/Bit-debug.apk'
if apk.exists():
 with zipfile.ZipFile(apk) as z:
  assert z.testzip() is None
  with open(apk,'rb') as raw:
   for info in z.infolist():
    if info.compress_type!=zipfile.ZIP_STORED:continue
    raw.seek(info.header_offset+26);nl,el=struct.unpack('<HH',raw.read(4));assert (info.header_offset+30+nl+el)%4==0,info.filename
  for f in (R/'app/src/main/assets').iterdir():assert z.read('assets/'+f.name)==f.read_bytes(),f'Outdated APK asset: {f.name}'
 build.run('apksigner','verify',apk)
print('PASS: recovered meshes, byte-exact original MP3 streams, PCM WAV metadata and APK integrity')

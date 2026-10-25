#!/usr/bin/env python3
"""Framework-only Kotlin APK build, for standard Android SDK or Termux tools.
BIT_KOTLIN_HOME: Kotlin distribution directory (with lib/), or auto-detect cached compiler.
ANDROID_JAR: SDK platform android.jar; default ~/android-sdk/platforms/android-36/android.jar.
No extraction dependencies are required to build: recovered assets are checked in.
"""
from pathlib import Path
import os, shutil, subprocess as sp, zipfile, struct, hashlib
ROOT=Path(__file__).resolve().parent
B=ROOT/'build';SRC=ROOT/'app/src/main'
JAR=Path(os.environ.get('ANDROID_JAR',str(Path.home()/'android-sdk/platforms/android-36/android.jar')))
def run(*a):sp.run([str(x) for x in a],check=True)
def tool(*names):
 for n in names:
  if shutil.which(n):return shutil.which(n)
 raise SystemExit('Missing tool: '+', '.join(names))
def compiler():
 kh=os.environ.get('BIT_KOTLIN_HOME')
 if kh:
  libs=list((Path(kh)/'lib').glob('*.jar'));stdlib=Path(kh)/'lib/kotlin-stdlib.jar'
 else:
  cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
  libs=[]
  for group,artifact,version in [('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.3.10'),('org.jetbrains.kotlin','kotlin-stdlib','2.3.10'),('org.jetbrains.kotlin','kotlin-script-runtime','2.3.10'),('org.jetbrains.kotlin','kotlin-daemon-embeddable','2.3.10'),('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),('org.jetbrains','annotations','13.0'),('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0')]:
   paths=list((cache/group/artifact/version).glob('*/*.jar'))
   if not paths:
    paths=sorted((cache/group/artifact).glob('*/*/*.jar'))[-1:]
   libs.extend(paths)
  stdlib=next(p for p in libs if p.name=='kotlin-stdlib-2.3.10.jar')
 return libs,stdlib

def main():
 os.chdir(ROOT);B.mkdir(exist_ok=True)
 for name in ['classes','generated','dex']:
  d=B/name
  if d.exists():shutil.rmtree(d)
  d.mkdir()
 aapt=tool('aapt2-aosp','aapt2')
 # AGP 9 takes namespace from Gradle; standalone AAPT2 needs it in its input manifest.
 manifest=B/'AndroidManifest.xml'
 text=(SRC/'AndroidManifest.xml').read_text()
 text=text.replace('<manifest ', '<manifest package="org.sksoft.bit" ',1)
 manifest.write_text(text)
 run(aapt,'compile','--no-crunch','--dir',SRC/'res','-o',B/'res.zip')
 run(aapt,'link','--debug-mode','-I',JAR,'--manifest',manifest,'--java',B/'generated','--min-sdk-version','26','--target-sdk-version','36','-A',SRC/'assets','-0','mp3','-0','wav','-o',B/'resources.apk',B/'res.zip')
 run('javac','-source','8','-target','8','-Xlint:-options','-classpath',JAR,'-d',B/'classes',*sorted((B/'generated').rglob('*.java')))
 libs,stdlib=compiler()
 run('java','-Xmx1024m','-cp',os.pathsep.join(map(str,libs)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','1.8','-classpath',os.pathsep.join(map(str,[JAR,stdlib,B/'classes'])),'-d',B/'classes',*sorted(SRC.rglob('*.kt')))
 args=B/'d8.args';args.write_text('\n'.join(map(str,sorted((B/'classes').rglob('*.class'))+[stdlib])))
 run(tool('d8'),'--lib',JAR,'--min-api','26','--output',B/'dex','@'+str(args))
 entries={}
 with zipfile.ZipFile(B/'resources.apk') as z:
  for n in z.namelist():entries[n]=z.read(n)
 for d in (B/'dex').glob('*.dex'):entries[d.name]=d.read_bytes()
 with zipfile.ZipFile(B/'aligned.apk','w') as z:
  for n,data in entries.items():
   i=zipfile.ZipInfo(n);offset=z.fp.tell()+30+len(n.encode());pad=(-offset-4)%4;i.extra=struct.pack('<HH',0xCAFE,pad)+bytes(pad);i.compress_type=zipfile.ZIP_STORED;z.writestr(i,data)
 key=B/'debug.keystore'
 if not key.exists():run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Bit Debug,O=Local,C=US','-keyalg','RSA','-keysize','2048','-validity','10000')
 apk=B/'Bit-debug.apk'
 run(tool('apksigner'),'sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,B/'aligned.apk')
 run('apksigner','verify','--verbose',apk)
 with zipfile.ZipFile(apk) as z:
  assert z.testzip() is None
  assert {'classes.dex','assets/yes.mp3','assets/no.mp3','assets/yes.wav','assets/no.wav','assets/mesh4.bin','AndroidManifest.xml'}<=set(z.namelist())
 digest=hashlib.sha256(apk.read_bytes()).hexdigest();(B/'Bit-debug.apk.sha256').write_text(digest+'  Bit-debug.apk\n')
 print('APK:',apk,'\nSHA256:',digest)
if __name__=='__main__':main()

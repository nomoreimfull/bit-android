"""Static PE extraction, then CPU-only geometry emulation. No Windows code or APIs run on host.
Requires: pip install --target analysis/python pefile unicorn
"""
import sys, pathlib, struct, json, hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'analysis/python'))
import pefile
from unicorn import Uc,UC_ARCH_X86,UC_MODE_32,UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_ESP, UC_X86_REG_EIP, UC_X86_REG_EAX,UC_X86_REG_FPCW
exe=next((ROOT/'.codexchat-attachments').rglob('*.exe'));b=exe.read_bytes();pe=pefile.PE(data=b)
out=ROOT/'app/src/main/assets';out.mkdir(parents=True,exist_ok=True)
meta={'sha256':hashlib.sha256(b).hexdigest(),'resources':[]}
def walk(es,path=[]):
 for e in es:
  key=str(e.name) if e.name else e.id
  if hasattr(e,'directory'):walk(e.directory.entries,path+[key])
  else:
   d=e.data.struct; raw=pe.get_data(d.OffsetToData,d.Size)
   if raw[:4]==b'RIFF':
    rid=path[1];(ROOT/f'analysis/resource-{rid}.wav').write_bytes(raw)
    pos=12;chunks={}
    while pos+8<=len(raw):
     name=raw[pos:pos+4];n=struct.unpack_from('<I',raw,pos+4)[0];chunks[name]=raw[pos+8:pos+8+n];pos+=8+n+(n%2)
    info=chunks.get(b'LIST',b'');ip=4;credits={}
    while ip+8<=len(info):
     ik=info[ip:ip+4].decode();il=struct.unpack_from('<I',info,ip+4)[0]
     credits[ik]=info[ip+8:ip+8+il].rstrip(b'\0').decode(errors='replace');ip+=8+il+(il%2)
    if credits:meta['audio_metadata']=credits
    fmt=chunks[b'fmt '];tag,ch,rate,avg,align,bits=struct.unpack_from('<HHIIHH',fmt)
    (out/('no.mp3' if rid==105 else 'yes.mp3')).write_bytes(chunks[b'data'])
    meta['resources'].append(dict(id=rid,tag=tag,channels=ch,rate=rate,bits=bits,fmt=fmt.hex(),bytes=len(raw)))
walk(pe.DIRECTORY_ENTRY_RESOURCE.entries)
# Execute ONLY the display-list construction routine, intercept EVERY external call.
u=Uc(UC_ARCH_X86,UC_MODE_32);base=pe.OPTIONAL_HEADER.ImageBase
u.mem_map(base,0x30000);u.mem_write(base,pe.get_memory_mapped_image());u.mem_map(0x100000,0x100000);u.mem_map(0x700000,0x10000)
sizes={'glNormal3dv':4,'glBegin':4,'glVertex3d':24,'glNormal3d':24,'glScaled':24,'glPopMatrix':0,'glPushMatrix':0,'glEndList':0,'glNewList':8,'glGenLists':4,'glEnd':0,'glVertex3dv':4,'glEnable':4,'glLightfv':12,'glFrustum':48,'glLoadIdentity':0,'glMatrixMode':4,'glTranslatef':12,'glViewport':16,'glDrawBuffer':4}
imports={}
for dll in pe.DIRECTORY_ENTRY_IMPORT:
 for imp in dll.imports:
  addr=0x700000+len(imports)*16;name=imp.name.decode();imports[addr]=name
  u.mem_write(imp.address,struct.pack('<I',addr));u.mem_write(addr,b'\xc3')
state={'list':None,'next':1,'normal':[0,0,1],'scale':[1,1,1],'stack':[],'verts':[],'meshes':{}}
def hook(uc,addr,size,user):
 if addr not in imports:return
 name=imports[addr]
 if name not in sizes:raise RuntimeError('Unexpected external call '+name)
 sp=uc.reg_read(UC_X86_REG_ESP);args=bytes(uc.mem_read(sp+4,sizes[name]));ints=struct.unpack('<'+'I'*(len(args)//4),args)
 if name=='glGenLists':uc.reg_write(UC_X86_REG_EAX,state['next']);state['next']+=1
 elif name=='glNewList':state['list']=ints[0];state['meshes'][str(ints[0])]=[];state['scale']=[1,1,1];state['stack']=[]
 elif name=='glPushMatrix':state['stack'].append(state['scale'][:])
 elif name=='glPopMatrix':state['scale']=state['stack'].pop()
 elif name=='glScaled':state['scale']=[a*c for a,c in zip(state['scale'],struct.unpack('<ddd',args))]
 elif name=='glBegin':state['mode']=ints[0];state['verts']=[]
 elif name in ('glNormal3d','glNormal3dv'):
  state['normal']=list(struct.unpack('<ddd',args if name=='glNormal3d' else uc.mem_read(ints[0],24)))
 elif name in ('glVertex3d','glVertex3dv'):
  xyz=struct.unpack('<ddd',args if name=='glVertex3d' else uc.mem_read(ints[0],24));state['verts'].append([*[v*s for v,s in zip(xyz,state['scale'])],*[n/s for n,s in zip(state['normal'],state['scale'])]])
 elif name=='glEnd':
  vs=state['verts'];assert state['mode'] in (4,9)
  if state['mode']==4:
   assert len(vs)%3==0
   state['meshes'][str(state['list'])].extend(vs)
  else:
   for i in range(1,len(vs)-1):state['meshes'][str(state['list'])].extend([vs[0],vs[i],vs[i+1]])
 ret=struct.unpack('<I',uc.mem_read(sp,4))[0];uc.reg_write(UC_X86_REG_ESP,sp+4+sizes[name]);uc.reg_write(UC_X86_REG_EIP,ret)
u.hook_add(UC_HOOK_CODE,hook)
sp=0x1ff000;u.mem_write(sp,struct.pack('<II',0x710000,sp+32));u.mem_write(sp+32,struct.pack('<iiii',0,0,200,200));u.reg_write(UC_X86_REG_ESP,sp);u.reg_write(UC_X86_REG_FPCW,0x37f)
u.emu_start(0x4024e0,0x710000,count=1000000)
for key,verts in state['meshes'].items():
 vals=[v for vert in verts for v in vert];(out/f'mesh{key}.bin').write_bytes(struct.pack('<'+'f'*len(vals),*vals))
meta['mesh_vertices']={k:len(v) for k,v in state['meshes'].items()};(ROOT/'analysis/recovered.json').write_text(json.dumps(meta,indent=2));(ROOT/'analysis/meshes.json').write_text(json.dumps(state['meshes']))
print(json.dumps(meta,indent=2))

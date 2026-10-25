"""Compare recovered timer evolution and PRNG against isolated x86 routines.
Run with the same PYTHONPATH/LIBUNICORN_PATH as recover.py.
"""
import runpy,struct,pathlib,json
ns=runpy.run_path(str(pathlib.Path(__file__).with_name('recover.py')))
u=ns['u'];ns['sizes']['InvalidateRect']=12
from unicorn.x86_const import UC_X86_REG_ESP,UC_X86_REG_EAX
sp=0x1ff000
results={}
for flag,phase in [(0x40ee18,0x40ee30),(0x40ee19,0x40ee34)]:
 u.mem_write(0x40ee18,b'\0\0');u.mem_write(0x40ee20,bytes(24));u.mem_write(flag,b'\1')
 phases=[]
 for tick in range(1,64):
  u.mem_write(sp,struct.pack('<IIIII',0x710000,1,0x113,1,0));u.reg_write(UC_X86_REG_ESP,sp)
  u.emu_start(0x4012c0,0x710000,count=10000)
  val=struct.unpack('<f',u.mem_read(phase,4))[0];phases.append(val)
  if not u.mem_read(flag,1)[0]:break
 assert tick==61,(tick,phases)
 assert phases[15:18]==[88.0,93.5,95.5],phases[15:18]
 angles=struct.unpack('<ffff',u.mem_read(0x40ee20,16))
 assert angles==(76.25,7.625,15.25,22.875),angles
 results[hex(flag)]={'ticks':tick,'seconds_at_14ms':tick*.014,'phases':phases,'angles':angles}
u.mem_write(0x40c514,struct.pack('<I',1));values=[]
for _ in range(5):
 u.mem_write(sp,struct.pack('<I',0x710000));u.reg_write(UC_X86_REG_ESP,sp);u.emu_start(0x403afa,0x710000,count=100);values.append(u.reg_read(UC_X86_REG_EAX))
assert values==[41,18467,6334,26500,19169],values
results['rand_seed_1']=values
(ns['ROOT']/'analysis/timer-probe.json').write_text(json.dumps(results,indent=2))
print('PASS: actual x86 timer (both responses), 61 ticks, rotations, MSVC PRNG')

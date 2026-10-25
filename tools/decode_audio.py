"""Losslessly decode recovered MPEG2.5 Layer III streams to widely supported PCM WAV.
Usage: python3 tools/decode_audio.py /path/to/libmpg123.so
Original MP3 data remains in assets as provenance; runtime plays PCM.
"""
import ctypes as C,sys,wave,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
lib=C.CDLL(sys.argv[1] if len(sys.argv)>1 else 'libmpg123.so')
lib.mpg123_init()
lib.mpg123_new.argtypes=[C.c_char_p,C.POINTER(C.c_int)];lib.mpg123_new.restype=C.c_void_p
lib.mpg123_open.argtypes=[C.c_void_p,C.c_char_p]
lib.mpg123_getformat.argtypes=[C.c_void_p,C.POINTER(C.c_long),C.POINTER(C.c_int),C.POINTER(C.c_int)]
lib.mpg123_read.argtypes=[C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)]
lib.mpg123_delete.argtypes=[C.c_void_p]
results={}
for name in ['yes','no']:
 error=C.c_int();h=lib.mpg123_new(None,C.byref(error));assert h
 assert lib.mpg123_open(h,str(root/f'app/src/main/assets/{name}.mp3').encode())==0
 rate=C.c_long();channels=C.c_int();encoding=C.c_int()
 assert lib.mpg123_getformat(h,C.byref(rate),C.byref(channels),C.byref(encoding))==0
 assert encoding.value==0xd0,hex(encoding.value) # MPG123_ENC_SIGNED_16
 pcm=bytearray();buf=C.create_string_buffer(8192);done=C.c_size_t()
 while True:
  ret=lib.mpg123_read(h,buf,len(buf),C.byref(done));pcm.extend(buf.raw[:done.value])
  if ret==-12:break
  assert ret in (0,-11),ret
 lib.mpg123_delete(h)
 with wave.open(str(root/f'app/src/main/assets/{name}.wav'),'wb') as f:
  f.setparams((channels.value,2,rate.value,0,'NONE','not compressed'));f.writeframes(pcm)
 results[name]={'rate':rate.value,'channels':channels.value,'samples':len(pcm)//2,'seconds':len(pcm)/2/rate.value}
(root/'analysis/audio-decoded.json').write_text(json.dumps(results,indent=2));print(results)
lib.mpg123_exit()

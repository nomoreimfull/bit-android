package org.sksoft.bit

import android.content.Context
import android.opengl.GLSurfaceView
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Binary meshes contain the intercepted original display-list vertices AND original normals. */
class BitRenderer(context: Context) : GLSurfaceView.Renderer {
    private val meshes = (1..4).map { id ->
        val bytes = context.assets.open("mesh$id.bin").use { it.readBytes() }
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).put(bytes).also { it.position(0) }.asFloatBuffer()
    }
    @Volatile var ticks = 0L
    @Volatile var response = 0 // 0 neutral, 1 yes, 2 no
    @Volatile var responseStart = 0L
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        gl.glClearColor(0f, 0f, 0f, 0f)
        gl.glEnable(GL10.GL_DEPTH_TEST)
        gl.glEnable(GL10.GL_CULL_FACE)
        gl.glEnable(GL10.GL_LIGHTING); gl.glEnable(GL10.GL_LIGHT0)
        gl.glEnable(GL10.GL_COLOR_MATERIAL)
        gl.glMatrixMode(GL10.GL_MODELVIEW); gl.glLoadIdentity()
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_AMBIENT, floatArrayOf(.1f,.1f,.1f,1f),0)
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_SPECULAR, floatArrayOf(.7f,.7f,.7f,1f),0)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY); gl.glEnableClientState(GL10.GL_NORMAL_ARRAY)
    }
    override fun onSurfaceChanged(gl: GL10, w: Int, h: Int) {
        gl.glViewport(0,0,w,h); gl.glMatrixMode(GL10.GL_PROJECTION); gl.glLoadIdentity()
        gl.glFrustumf(-1f,1f,-1f,1f,1.5f,20f)
        gl.glMatrixMode(GL10.GL_MODELVIEW)
    }
    private fun draw(gl: GL10, id: Int, scale: Float) {
        if (kotlin.math.abs(scale) <= 0.0001f) return
        val b = meshes[id-1]
        gl.glPushMatrix(); gl.glScalef(scale,scale,scale)
        b.position(0); gl.glVertexPointer(3,GL10.GL_FLOAT,24,b)
        b.position(3); gl.glNormalPointer(GL10.GL_FLOAT,24,b)
        gl.glDrawArrays(GL10.GL_TRIANGLES,0,b.capacity()/6)
        gl.glPopMatrix()
    }
    override fun onDrawFrame(gl: GL10) {
        val t=ticks; val r=response
        val blend=if (r==0) 0f else Motion.responseBlend(t-responseStart)
        val yes=if (r==1) blend else 0f; val no=if (r==2) blend else 0f
        val color=if(r==1) Motion.yes else Motion.no
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
        gl.glLoadIdentity(); gl.glTranslatef(0f,0f,-3f)
        gl.glColor4f(Motion.neutral[0]*(1-blend)+color[0]*blend,Motion.neutral[1]*(1-blend)+color[1]*blend,Motion.neutral[2]*(1-blend)+color[2]*blend,1f)
        gl.glRotatef((t*.125%360).toFloat(),1f,0f,0f)
        gl.glRotatef((t*.25%360).toFloat(),0f,1f,0f)
        gl.glRotatef((t*.375%360).toFloat(),0f,0f,1f)
        gl.glScalef(.9f,.9f,.9f)
        val s=Motion.neutralScales(t,yes,no)
        draw(gl,1,s[0]); draw(gl,2,s[1]); draw(gl,3,yes*1.7f); draw(gl,4,no*1.7f)
    }
}

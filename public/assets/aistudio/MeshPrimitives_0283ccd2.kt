package com.example.engine3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MeshData(val vertices: FloatArray) {
    val vertexCount: Int = vertices.size / 6
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }

    fun draw(posAttr: Int, normAttr: Int, mode: Int = GLES30.GL_TRIANGLES) {
        buffer.position(0)
        GLES30.glEnableVertexAttribArray(posAttr)
        GLES30.glVertexAttribPointer(posAttr, 3, GLES30.GL_FLOAT, false, 24, buffer)
        buffer.position(3)
        GLES30.glEnableVertexAttribArray(normAttr)
        GLES30.glVertexAttribPointer(normAttr, 3, GLES30.GL_FLOAT, false, 24, buffer)
        GLES30.glDrawArrays(mode, 0, vertexCount)
    }
}

object MeshPrimitives {
    val cube: MeshData by lazy { buildCube() }
    val cylinder: MeshData by lazy { buildCylinder(16) }
    val octahedron: MeshData by lazy { buildOctahedron() }
    val ring: MeshData by lazy { buildRing(32, 0.72f, 1f) }
    val disc: MeshData by lazy { buildRing(24, 0f, 1f) }
    val groundQuad: MeshData by lazy { buildGroundQuad() }
    // Additional meshes. All centered at origin and sized around one world unit.
    val sphere: MeshData by lazy { buildSphere(12, 8) }
    val capsule: MeshData by lazy { buildCapsule(12, 5) }
    val beveledBox: MeshData by lazy { buildBeveledBox() }
    val cone: MeshData by lazy { buildCone(12) }
    val blade: MeshData by lazy { buildBlade() }
    val torus: MeshData by lazy { buildTorus(24, 6) }

    private fun tri(v: MutableList<Float>, a: FloatArray, b: FloatArray, c: FloatArray) {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.000001f)
        nx /= len; ny /= len; nz /= len
        for (p in arrayOf(a,b,c)) { v.add(p[0]); v.add(p[1]); v.add(p[2]); v.add(nx); v.add(ny); v.add(nz) }
    }
    private fun p(x: Float, y: Float, z: Float) = floatArrayOf(x,y,z)
    private fun quad(v: MutableList<Float>, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
        tri(v,a,b,c); tri(v,a,c,d)
    }
    private fun buildCube(): MeshData {
        val v = mutableListOf<Float>(); val h = .5f
        quad(v,p(-h,-h,h),p(h,-h,h),p(h,h,h),p(-h,h,h))
        quad(v,p(h,-h,-h),p(-h,-h,-h),p(-h,h,-h),p(h,h,-h))
        quad(v,p(-h,-h,-h),p(-h,-h,h),p(-h,h,h),p(-h,h,-h))
        quad(v,p(h,-h,h),p(h,-h,-h),p(h,h,-h),p(h,h,h))
        quad(v,p(-h,h,h),p(h,h,h),p(h,h,-h),p(-h,h,-h))
        quad(v,p(-h,-h,-h),p(h,-h,-h),p(h,-h,h),p(-h,-h,h))
        return MeshData(v.toFloatArray())
    }
    private fun buildCylinder(n: Int): MeshData {
        val v = mutableListOf<Float>()
        for(i in 0 until n) {
            val a = 2.0 * PI * i / n; val b = 2.0 * PI * (i+1) / n
            val x = (.5*cos(a)).toFloat(); val z = (.5*sin(a)).toFloat()
            val xx = (.5*cos(b)).toFloat(); val zz = (.5*sin(b)).toFloat()
            quad(v,p(x,-.5f,z),p(xx,-.5f,zz),p(xx,.5f,zz),p(x,.5f,z))
            tri(v,p(0f,.5f,0f),p(x,.5f,z),p(xx,.5f,zz))
            tri(v,p(0f,-.5f,0f),p(xx,-.5f,zz),p(x,-.5f,z))
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildOctahedron(): MeshData {
        val v = mutableListOf<Float>(); val r = .55f
        for(i in 0..3) {
            val a = 2.0*PI*i/4; val b = 2.0*PI*(i+1)/4
            val aa=p((r*cos(a)).toFloat(),0f,(r*sin(a)).toFloat())
            val bb=p((r*cos(b)).toFloat(),0f,(r*sin(b)).toFloat())
            tri(v,p(0f,r,0f),aa,bb); tri(v,p(0f,-r,0f),bb,aa)
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildRing(n: Int, inner: Float, outer: Float): MeshData {
        val v=mutableListOf<Float>()
        for(i in 0 until n) {
            val a=2.0*PI*i/n; val b=2.0*PI*(i+1)/n
            quad(v,p((inner*cos(a)).toFloat(),0f,(inner*sin(a)).toFloat()),
                p((outer*cos(a)).toFloat(),0f,(outer*sin(a)).toFloat()),
                p((outer*cos(b)).toFloat(),0f,(outer*sin(b)).toFloat()),
                p((inner*cos(b)).toFloat(),0f,(inner*sin(b)).toFloat()))
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildGroundQuad(): MeshData {
        val v=mutableListOf<Float>()
        quad(v,p(-.5f,0f,.5f),p(.5f,0f,.5f),p(.5f,0f,-.5f),p(-.5f,0f,-.5f))
        return MeshData(v.toFloatArray())
    }
    private fun buildSphere(n: Int, rings: Int): MeshData {
        val v=mutableListOf<Float>()
        fun point(i:Int,j:Int):FloatArray {
            val lat=PI*j/rings; val lon=2.0*PI*i/n
            return p((.5*sin(lat)*cos(lon)).toFloat(),(.5*cos(lat)).toFloat(),(.5*sin(lat)*sin(lon)).toFloat())
        }
        for(j in 0 until rings) for(i in 0 until n) {
            val a=point(i,j); val b=point(i+1,j); val c=point(i+1,j+1); val d=point(i,j+1)
            if(j>0) tri(v,a,b,d)
            if(j<rings-1) tri(v,b,c,d)
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildCapsule(n:Int, hemiRings:Int):MeshData {
        val v=mutableListOf<Float>()
        val rings=hemiRings*2+1
        fun point(i:Int,j:Int):FloatArray {
            val lat=PI*j/rings; val lon=2.0*PI*i/n
            val y=(.38*cos(lat)).toFloat() + if(j<rings/2) .12f else -.12f
            return p((.38*sin(lat)*cos(lon)).toFloat(),y,(.38*sin(lat)*sin(lon)).toFloat())
        }
        for(j in 0 until rings) for(i in 0 until n) {
            val a=point(i,j); val b=point(i+1,j); val c=point(i+1,j+1); val d=point(i,j+1)
            if(j>0) tri(v,a,b,d)
            if(j<rings-1) tri(v,b,c,d)
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildBeveledBox():MeshData {
        // Chamfered corners: octagonal horizontal cross-section, flat top/bottom.
        val v=mutableListOf<Float>(); val h=.5f; val k=.36f
        val x=floatArrayOf(-k,k,h,h,k,-k,-h,-h)
        val z=floatArrayOf(h,h,k,-k,-h,-h,-k,k)
        for(i in 0..7) {
            val j=(i+1)%8
            quad(v,p(x[i],-h,z[i]),p(x[j],-h,z[j]),p(x[j],h,z[j]),p(x[i],h,z[i]))
            tri(v,p(0f,h,0f),p(x[i],h,z[i]),p(x[j],h,z[j]))
            tri(v,p(0f,-h,0f),p(x[j],-h,z[j]),p(x[i],-h,z[i]))
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildCone(n:Int):MeshData {
        val v=mutableListOf<Float>()
        for(i in 0 until n) {
            val a=2.0*PI*i/n; val b=2.0*PI*(i+1)/n
            val aa=p((.5*cos(a)).toFloat(),-.5f,(.5*sin(a)).toFloat())
            val bb=p((.5*cos(b)).toFloat(),-.5f,(.5*sin(b)).toFloat())
            tri(v,aa,bb,p(0f,.5f,0f)); tri(v,p(0f,-.5f,0f),bb,aa)
        }
        return MeshData(v.toFloatArray())
    }
    private fun buildBlade():MeshData {
        // Diamond-shaped energy blade, pointed forward along +Z.
        val v=mutableListOf<Float>()
        val tip=p(0f,0f,.5f); val base=p(0f,0f,-.5f)
        val l=p(-.5f,0f,-.16f); val r=p(.5f,0f,-.16f)
        val top=p(0f,.12f,-.12f); val bottom=p(0f,-.12f,-.12f)
        for(a in arrayOf(l,r)) { tri(v,tip,a,top); tri(v,base,top,a); tri(v,tip,bottom,a); tri(v,base,a,bottom) }
        return MeshData(v.toFloatArray())
    }
    private fun buildTorus(n:Int,m:Int):MeshData {
        val v=mutableListOf<Float>()
        fun point(i:Int,j:Int):FloatArray {
            val a=2.0*PI*i/n; val b=2.0*PI*j/m
            val r=.40+.10*cos(b)
            return p((r*cos(a)).toFloat(),(.10*sin(b)).toFloat(),(r*sin(a)).toFloat())
        }
        for(i in 0 until n) for(j in 0 until m)
            quad(v,point(i,j),point(i+1,j),point(i+1,j+1),point(i,j+1))
        return MeshData(v.toFloatArray())
    }
}

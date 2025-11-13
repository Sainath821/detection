package com.example.edgevision.gl

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages OpenGL shader compilation and linking
 */
class ShaderProgram(private val context: Context) {

    companion object {
        private const val TAG = "ShaderProgram"
    }

    var programId: Int = 0
        private set

    private var vertexShaderId: Int = 0
    private var fragmentShaderId: Int = 0

    /**
     * Load shader source from assets
     */
    private fun loadShaderFromAssets(fileName: String): String {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line).append('\n')
        }

        reader.close()
        return stringBuilder.toString()
    }

    /**
     * Compile shader from source
     */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type $type")
            return 0
        }

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        // Check compilation status
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Error compiling shader: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }

        val shaderType = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
        Log.d(TAG, "Shader compiled successfully: $shaderType")
        return shader
    }

    /**
     * Load and compile shaders from assets
     */
    fun loadShaders(vertexShaderPath: String, fragmentShaderPath: String): Boolean {
        try {
            // Load shader sources
            val vertexSource = loadShaderFromAssets(vertexShaderPath)
            val fragmentSource = loadShaderFromAssets(fragmentShaderPath)

            // Compile shaders
            vertexShaderId = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            if (vertexShaderId == 0) return false

            fragmentShaderId = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            if (fragmentShaderId == 0) {
                GLES20.glDeleteShader(vertexShaderId)
                return false
            }

            Log.i(TAG, "Shaders loaded and compiled successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error loading shaders", e)
            return false
        }
    }

    /**
     * Link shader program
     */
    fun linkProgram(): Boolean {
        programId = GLES20.glCreateProgram()
        if (programId == 0) {
            Log.e(TAG, "Error creating program")
            return false
        }

        GLES20.glAttachShader(programId, vertexShaderId)
        GLES20.glAttachShader(programId, fragmentShaderId)
        GLES20.glLinkProgram(programId)

        // Check link status
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            Log.e(TAG, "Error linking program: $log")
            GLES20.glDeleteProgram(programId)
            programId = 0
            return false
        }

        Log.i(TAG, "Shader program linked successfully")
        return true
    }

    /**
     * Use this shader program
     */
    fun use() {
        GLES20.glUseProgram(programId)
    }

    /**
     * Get attribute location
     */
    fun getAttribLocation(name: String): Int {
        return GLES20.glGetAttribLocation(programId, name)
    }

    /**
     * Get uniform location
     */
    fun getUniformLocation(name: String): Int {
        return GLES20.glGetUniformLocation(programId, name)
    }

    /**
     * Clean up resources
     */
    fun release() {
        if (vertexShaderId != 0) {
            GLES20.glDeleteShader(vertexShaderId)
            vertexShaderId = 0
        }
        if (fragmentShaderId != 0) {
            GLES20.glDeleteShader(fragmentShaderId)
            fragmentShaderId = 0
        }
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        Log.d(TAG, "ShaderProgram resources released")
    }
}

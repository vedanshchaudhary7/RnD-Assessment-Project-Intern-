package com.example.edgedetectionviewer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "CameraGLSurfaceView";
    private final MainActivity activity;
    private SurfaceTexture surfaceTexture;
    private int textureId;
    private boolean frameAvailable = false;
    private boolean applyEdges = false;

    // Transformation matrix for the texture
    private final float[] transformMatrix = new float[16];
    private int programHandle;
    private int positionHandle;
    private int texCoordHandle;
    private int transformHandle;
    private int textureHandle;

    // Vertex and texture coordinates
    private static final float[] VERTICES = {
            -1f, -1f,  // bottom-left
            1f, -1f,   // bottom-right
            -1f, 1f,   // top-left
            1f, 1f     // top-right
    };
    private static final float[] TEX_COORDS = {
            0f, 1f,    // bottom-left
            1f, 1f,    // bottom-right
            0f, 0f,    // top-left
            1f, 0f     // top-right
    };

    public CameraGLSurfaceView(Context context) {
        super(context);
        this.activity = (MainActivity) context;
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Create the texture for the camera feed
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(this);

        // Set up shaders and program
        String vertexShaderCode =
                "attribute vec4 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "uniform mat4 uTransform;\n" +
                "void main() {\n" +
                "    gl_Position = aPosition;\n" +
                "    vTexCoord = (uTransform * vec4(aTexCoord, 0.0, 1.0)).xy;\n" +
                "}";
        String fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTexCoord;\n" +
                "uniform samplerExternalOES uTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                "}";
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        programHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(programHandle, vertexShader);
        GLES20.glAttachShader(programHandle, fragmentShader);
        GLES20.glLinkProgram(programHandle);

        positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition");
        texCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTexCoord");
        transformHandle = GLES20.glGetUniformLocation(programHandle, "uTransform");
        textureHandle = GLES20.glGetUniformLocation(programHandle, "uTexture");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            if (!frameAvailable) return;
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(transformMatrix);
            frameAvailable = false;
        }

        // Process the frame via JNI
        int[] outputTextures = new int[1];
        GLES20.glGenTextures(1, outputTextures, 0);
        int outputTextureId = outputTextures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputTextureId);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        activity.processFrame(textureId, outputTextureId, applyEdges);

        // Clear the screen
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Use the shader program
        GLES20.glUseProgram(programHandle);

        // Set up vertex and texture coordinates
        java.nio.FloatBuffer vertexBuffer = java.nio.ByteBuffer.allocateDirect(VERTICES.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(VERTICES).position(0);
        java.nio.FloatBuffer texCoordBuffer = java.nio.ByteBuffer.allocateDirect(TEX_COORDS.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        texCoordBuffer.put(TEX_COORDS).position(0);

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);
        GLES20.glEnableVertexAttribArray(texCoordHandle);

        // Bind the output texture (processed by OpenCV)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outputTextureId);
        GLES20.glUniform1i(textureHandle, 0);

        // Set the transform matrix
        java.nio.FloatBuffer transformBuffer = java.nio.ByteBuffer.allocateDirect(transformMatrix.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        transformBuffer.put(transformMatrix).position(0);
        GLES20.glUniformMatrix4fv(transformHandle, 1, false, transformBuffer);

        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Clean up
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
        GLES20.glDeleteTextures(1, new int[]{outputTextureId}, 0);

        // Update FPS
        activity.updateFPS();
        requestRender();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            frameAvailable = true;
        }
        requestRender();
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public void setApplyEdges(boolean applyEdges) {
        this.applyEdges = applyEdges;
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
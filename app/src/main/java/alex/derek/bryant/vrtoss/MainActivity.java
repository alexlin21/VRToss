package alex.derek.bryant.vrtoss;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import static alex.derek.bryant.vrtoss.Square.COORDS_PER_VERTEX;

public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {

    private static final float CAMERA_Z = 0.01f;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    private Square mSquare;
    private String TAG = "MAIN_ACTIVITY";

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];

    private FloatBuffer floorVertices;
    private FloatBuffer floorColors;
    private FloatBuffer floorNormals;
    private int floorPositionParam;
    private int floorNormalParam;
    private int floorColorParam;
    private int floorModelParam;
    private int floorModelViewParam;
    private int floorModelViewProjectionParam;
    private int floorLightPosParam;
    private int floorProgram;
    private float[] modelView;
    private float[] modelFloor;
    private float[] modelViewProjection;
    private float floorDepth = 20f;

    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] headRotation;

    //    private VrPanoramaView panoWidgetView;
//    private VrPanoramaView.Options panoOptions = new VrPanoramaView.Options();
//    private ImageLoaderTask backgroundImageLoaderTask;
//    private boolean loadImageSuccessful;
//    private Uri fileUri;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        camera = new float[16];
        view = new float[16];
        modelFloor = new float[16];
        modelView = new float[16];
        modelViewProjection = new float[16];
        headRotation = new float[4];
        headView = new float[16];
        // Associate a GvrView.StereoRenderer with gvrView.
        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);
        // Associate the gvrView with this activity.
        setGvrView(gvrView);



//        // This code is for using the panoroma widget. I don't think we will be able to overlay
//        // things on it
//
//        panoWidgetView = (VrPanoramaView) findViewById(R.id.living_room_pano);
//        panoWidgetView.setEventListener(new ActivityEventListener());
//
//        // Initial launch of the app or an Activity recreation due to rotation.
//        handleIntent(getIntent());

    }





    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        headTransform.getHeadView(headView, 0);

        // Update the 3d audio engine with the most recent head rotation.
        headTransform.getQuaternion(headRotation, 0);
    }

    @Override
    public void onDrawEye(Eye eye) {
        // Set the background clear color to gray.
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 0.5f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float[] scratch = new float[16];

        // Draw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, eye.getPerspective(Z_NEAR, Z_FAR), 0, mViewMatrix, 0);

        // Draw square
        mSquare.draw(mMVPMatrix);

        // Combine the rotation matrix with the projection and camera view
        // Note that the mMVPMatrix factor *must be first* in order
        // for the matrix multiplication product to be correct.
        Matrix.multiplyMM(scratch, 0, mMVPMatrix, 0, mRotationMatrix, 0);


        // Set modelView for the floor, so we draw floor in the correct location
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

        drawFloor();

    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        // Adjust the viewport based on geometry changes,
        // such as screen rotation
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        // Set the background frame color

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        mSquare = new Square();

        createWorld();
    }

    public void createWorld() {
        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(EnvironmentData.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        floorVertices = bbFloorVertices.asFloatBuffer();
        floorVertices.put(EnvironmentData.FLOOR_COORDS);
        floorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(EnvironmentData.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        floorNormals = bbFloorNormals.asFloatBuffer();
        floorNormals.put(EnvironmentData.FLOOR_NORMALS);
        floorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(EnvironmentData.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        floorColors = bbFloorColors.asFloatBuffer();
        floorColors.put(EnvironmentData.FLOOR_COLORS);
        floorColors.position(0);

        int vertexShader = LoadGLShaderFromResource(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = LoadGLShaderFromResource(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);

        floorProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(floorProgram, vertexShader);
        GLES20.glAttachShader(floorProgram, gridShader);
        GLES20.glLinkProgram(floorProgram);
        GLES20.glUseProgram(floorProgram);

        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
        floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

        Matrix.setIdentityM(modelFloor, 0);
        Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

    }

    public void drawFloor() {
        GLES20.glUseProgram(floorProgram);

        // Set ModelView, MVP, position, normals, and color.
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false, modelViewProjection, 0);
        GLES20.glVertexAttribPointer(
                floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, floorVertices);
        GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0, floorNormals);
        GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

        GLES20.glEnableVertexAttribArray(floorPositionParam);
        GLES20.glEnableVertexAttribArray(floorNormalParam);
        GLES20.glEnableVertexAttribArray(floorColorParam);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 24);

        GLES20.glDisableVertexAttribArray(floorPositionParam);
        GLES20.glDisableVertexAttribArray(floorNormalParam);
        GLES20.glDisableVertexAttribArray(floorColorParam);
    }

    @Override
    public void onRendererShutdown() {

    }

    /**
     * Utility method for compiling a OpenGL shader.
     *
     * <p><strong>Note:</strong> When developing shaders, use the checkGlError()
     * method to debug shader coding errors.</p>
     *
     * @param type - Vertex or fragment shader type.
     * @param shaderCode - String containing the shader code.
     * @return - Returns an id for the shader.
     */
    public static int loadShader(int type, String shaderCode) {
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }

    public int LoadGLShaderFromResource(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }




    // The massive comment below is for the panorma view...

//
//
//    /*
//     * Called when the Activity is already running and it's given a new intent.
//     */
//    @Override
//    protected void onNewIntent(Intent intent) {
//        Log.i(TAG, this.hashCode() + ".onNewIntent()");
//        // Save the intent. This allows the getIntent() call in onCreate() to use this new Intent during
//        // future invocations.
//        setIntent(intent);
//        // Load the new image.
//        handleIntent(intent);
//    }
//
//    private void handleIntent(Intent intent) {
//        // Determine if the Intent contains a file to load.
//        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
//            Log.i(TAG, "ACTION_VIEW Intent recieved");
//
//            fileUri = intent.getData();
//            if (fileUri == null) {
//                Log.w(TAG, "No data uri specified. Use \"-d /path/filename\".");
//            } else {
//                Log.i(TAG, "Using file " + fileUri.toString());
//            }
//
//            panoOptions.inputType = intent.getIntExtra("inputType", VrPanoramaView.Options.TYPE_STEREO_OVER_UNDER);
//            Log.i(TAG, "Options.inputType = " + panoOptions.inputType);
//        } else {
//            Log.i(TAG, "Intent is not ACTION_VIEW. Using default pano image.");
//            fileUri = null;
//            panoOptions.inputType = VrPanoramaView.Options.TYPE_STEREO_OVER_UNDER;
//        }
//
//        // Load the bitmap in a background thread to avoid blocking the UI thread. This operation can
//        // take 100s of milliseconds.
//        if (backgroundImageLoaderTask != null) {
//            // Cancel any task from a previous intent sent to this activity.
//            backgroundImageLoaderTask.cancel(true);
//        }
//        backgroundImageLoaderTask = new ImageLoaderTask();
//        backgroundImageLoaderTask.execute(Pair.create(fileUri, panoOptions));
//    }
//
//    /**
//     * Helper class to manage threading.
//     */
//    class ImageLoaderTask extends AsyncTask<Pair<Uri, VrPanoramaView.Options>, Void, Boolean> {
//
//        /**
//         * Reads the bitmap from disk in the background and waits until it's loaded by pano widget.
//         */
//        @Override
//        protected Boolean doInBackground(Pair<Uri, VrPanoramaView.Options>... fileInformation) {
//            VrPanoramaView.Options panoOptions = null;  // It's safe to use null VrPanoramaView.Options.
//            InputStream istr = null;
//            if (fileInformation == null || fileInformation.length < 1
//                    || fileInformation[0] == null || fileInformation[0].first == null) {
//                AssetManager assetManager = getAssets();
//                try {
//                    istr = assetManager.open("living_room.jpg");
//                    panoOptions = new VrPanoramaView.Options();
//                    panoOptions.inputType = VrPanoramaView.Options.TYPE_STEREO_OVER_UNDER;
//                } catch (IOException e) {
//                    Log.e(TAG, "Could not decode default bitmap: " + e);
//                    return false;
//                }
//            } else {
//                try {
//                    istr = new FileInputStream(new File(fileInformation[0].first.getPath()));
//                    panoOptions = fileInformation[0].second;
//                } catch (IOException e) {
//                    Log.e(TAG, "Could not load file: " + e);
//                    return false;
//                }
//            }
//
//            panoWidgetView.loadImageFromBitmap(BitmapFactory.decodeStream(istr), panoOptions);
//            try {
//                istr.close();
//            } catch (IOException e) {
//                Log.e(TAG, "Could not close input stream: " + e);
//            }
//
//            return true;
//        }
//    }
//
//    private class ActivityEventListener extends VrPanoramaEventListener {
//        @Override
//        public void onLoadSuccess() {
//            loadImageSuccessful = true;
//        }
//
//        /**
//         * Called by pano widget on the UI thread on any asynchronous error.
//         */
//        @Override
//        public void onLoadError(String errorMessage) {
//            loadImageSuccessful = false;
//            Toast.makeText(
//                    MainActivity.this, "Error loading pano: " + errorMessage, Toast.LENGTH_LONG)
//                    .show();
//            Log.e(TAG, "Error loading pano: " + errorMessage);
//        }
//    }

}

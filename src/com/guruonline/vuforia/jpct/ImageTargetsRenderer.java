/*==============================================================================
Copyright (c) 2010-2013 QUALCOMM Austria Research Center GmbH.
All Rights Reserved.

@file
    ImageTargetsRenderer.java

@brief
    Sample for ImageTargets

==============================================================================*/

package com.guruonline.vuforia.jpct;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.qualcomm.QCAR.QCAR;
/**
 * jpct-ae include
 */
import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Logger;
import com.threed.jpct.Matrix;
import com.threed.jpct.Object3D;
import com.threed.jpct.Primitives;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;

/** The renderer class for the ImageTargets sample. */
public class ImageTargetsRenderer implements GLSurfaceView.Renderer {
	public boolean mIsActive = false;

	/** Reference to main activity **/
	public ImageTargets mActivity;

	/**
	 * jpct-ae properties
	 */
	// Used to handle pause and resume...

	private GLSurfaceView mGLView;

	private FrameBuffer fb = null;
	private World world = null;
	private RGBColor back = new RGBColor(50, 50, 100);
	private Camera cam = null;
	private float touchTurn = 0;
	private float touchTurnUp = 0;

	private float xpos = -1;
	private float ypos = -1;

	private Object3D cube = null;
	private int fps = 0;

	private Light sun = null;

	private float[] modelViewMat;

	private float fov;

	private float fovy;

	/**
	 * image target renderer for jpct-ae First, we will make jPCT-AE and
	 * Vuforia’s native code share the same GLsurface. For that, first open
	 * ImageTargetsRenderer.java under the ImageTargets sample app (package
	 * com.qualcomm.QCARSamples.ImageTargets). This is the OpenGL renderer, and
	 * thus it’s where our jPCT code should be injected in. We will start by
	 * taking JPCT-AE Hello World sample app into this renderer. First, create a
	 * constructor for ImageTargetsRenderer. This is for the Activity reference
	 * to be passed on to our renderer, instead of explicitly setting the
	 * attribute, as Qualcomm’s demo does. We will also init our scene here.
	 * 
	 * @param activity
	 */
	public ImageTargetsRenderer(ImageTargets activity) {
		Config.viewportOffsetAffectsRenderTarget = true;
		Config.viewportOffsetY = 0.12f;
		Point size = new Point();
		activity.getWindowManager().getDefaultDisplay().getSize(size);

		this.mActivity = activity;
		world = new World();
		world.setAmbientLight(20, 20, 20);

		sun = new Light(world);
		sun.setIntensity(250, 250, 250);

		// Create a texture out of the icon...:-)
		// Texture texture = new Texture(BitmapHelper.rescale(
		// BitmapHelper.convert(mActivity.getResources().getDrawable(
		// R.drawable.ic_launcher)), 64, 64));
		// TextureManager.getInstance().addTexture("texture", texture);

		cube = Primitives.getCube(10);
		cube.calcTextureWrapSpherical();
		// cube.setTexture("texture");
		cube.strip();
		cube.build();

		world.addObject(cube);

		cam = world.getCamera();
		/**
		 * And we must remove the lines of code configuring the camera from our
		 * ImageRenderer constructor:
		 */
		// cam.moveCamera(Camera.CAMERA_MOVEOUT, 50);
		// cam.lookAt(cube.getTransformedCenter());

		SimpleVector sv = new SimpleVector();
		sv.set(cube.getTransformedCenter());
		sv.y -= 100;
		sv.z -= 100;
		sun.setPosition(sv);
		MemoryHelper.compact();

	}

	/** Native function for initializing the renderer. */
	public native void initRendering();

	/** Native function to update the renderer. */
	public native void updateRendering(int width, int height);

	/** Called when the surface is created or recreated. */
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		DebugLog.LOGD("GLRenderer::onSurfaceCreated");

		// Call native function to initialize rendering:
		initRendering();

		// Call QCAR function to (re)initialize rendering after first use
		// or after OpenGL ES context was lost (e.g. after onPause/onResume):
		QCAR.onSurfaceCreated();
	}

	/** Called when the surface changed size. */
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		/**
		 * As you can see, I just copied and pasted the code in the Hello World
		 * demo and changed the icon reference. I also put the camera object as
		 * a field instead of a local variable. We will remove camera
		 * initialization later, since we will be handling camera dynamically in
		 * accordance to the marker's orientation. But for now, go to
		 * ImageTargets.java, then change the initialization of
		 * ImageTargetsRenderer to include the activity in the constructor, and
		 * remove the line setting this parameter immediately after. Then go
		 * back to ImageTargetsRenderer, into the onSurfaceChanged method. This
		 * method is called whenever our surface changes size. We should put
		 * jpct-ae framebuffer initialization code here, right from the Hello
		 * World demo.
		 */
		if (fb != null) {
			fb.dispose();
		}
		fb = new FrameBuffer(width, height);
		DebugLog.LOGD("GLRenderer::onSurfaceChanged");

		// Call native function to update rendering when render surface
		// parameters have changed:
		updateRendering(width, height);

		// Call QCAR function to handle render surface size changes:
		QCAR.onSurfaceChanged(width, height);
	}

	/** The native render function. */
	public native void renderFrame();

	/** Called to draw the current frame. */
	public void onDrawFrame(GL10 gl) {
		if (!mIsActive)
			return;

		// Update render view (projection matrix and viewport) if needed:
		mActivity.updateRenderView();

		// Call our native function to render content
		renderFrame();

		updateCamera();
		/**
		 * Okay, we have initialized our scene and framebuffer, but we have yet
		 * to tell jPCT to render it. This is done in the onDrawFrame() method.
		 * From the HelloWorld sample, paste the following code directly after
		 * the renderFrame() native call :
		 */
		world.renderScene(fb);
		world.draw(fb);
		fb.display();
	}

	public void updateModelviewMatrix(float mat[]) {
		modelViewMat = mat;
	}

	/**
	 * Applying the matrix to the camera Now back to the Java code. We know have
	 * access to the marker's Model View Matrix returned by the AR engine. We
	 * just need to apply it to the camera, and we're set! (well, almost). To do
	 * this, we will convert the array of float we defined earlier into
	 * jPCT-AE's own matrix class. We first create a Matrix object, then assign
	 * the raw float values using the setDump() method. After that, applying the
	 * matrix to the camera is just a matter of calling the setBack() method on
	 * the Camera object. I condensed all this within an updateCamera() method,
	 * which I call right after the renderFrame() call in the onDrawFrame()
	 * method:
	 */
	public void updateCamera() {
		if (modelViewMat != null) {
			Matrix m = new Matrix();

			cam.setFOV(fov);
			cam.setYFOV(fovy);
			/*
			 * // In the java files where we configured the camera // in the
			 * function where we received the inverted Matrix (if you followed
			 * the example the function is updateModelviewMatrix) //
			 * http://www.jpct.net/forum2/index.php?topic=3598.0
			 */
			//Camera position
//			landscape mode
//			float cam_x = invTranspMV.data[12];
//			float cam_y = invTranspMV.data[13];
//			float cam_z = invTranspMV.data[14];
//
//			//Camera orientation axis (camera viewing direction, camera right direction and camera up direction)
//			float cam_right_x = invTranspMV.data[0];
//			float cam_right_y = invTranspMV.data[1];
//			float cam_right_z = invTranspMV.data[2];
//
//			float cam_up_x = -invTranspMV.data[4];
//			float cam_up_y = -invTranspMV.data[5];
//			float cam_up_z = -invTranspMV.data[6];
//
//			float cam_dir_x = invTranspMV.data[8];
//			float cam_dir_y = invTranspMV.data[9];
//			float cam_dir_z = invTranspMV.data[10];
			
			float cam_right_x = modelViewMat[0];
			float cam_right_y = modelViewMat[1];
			float cam_right_z = modelViewMat[2];
			float cam_up_x = -modelViewMat[4];
			float cam_up_y = -modelViewMat[5];
			float cam_up_z = -modelViewMat[6];
			float cam_dir_x = modelViewMat[8];
			float cam_dir_y = modelViewMat[9];
			float cam_dir_z = modelViewMat[10];

			float cam_x = modelViewMat[12];
			float cam_y = modelViewMat[13];
			float cam_z = modelViewMat[14];

			cam.setOrientation(
					new SimpleVector(cam_dir_x, cam_dir_y, cam_dir_z),
					new SimpleVector(-cam_right_x, -cam_right_y, -cam_right_z));
			cam.setPosition(cam_x, cam_y, cam_z);
		}
	}

	public void setFov(float V) {
		// Log.v("setFov", "V = " + V);
		fov = V;
	}

	public void setFovy(float V) {
		// Log.v("setFovy", "V = " + V);
		fovy = V;
	}

}

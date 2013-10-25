package com.roryhool.mediacodecdemo;

import com.roryhool.mediacodecdemo.R;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class DecodeToSurfaceActivity extends Activity implements Callback {

   public static final String EXTRA_FILE_PATH = "EXTRA_FILE_PATH";

   SurfaceDecoder mDecoder;

   SurfaceView    mSurfaceView;

   String                     mFilePath;

   @Override
   protected void onCreate( Bundle savedInstanceState ) {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_decode );

      mSurfaceView = (SurfaceView) findViewById( R.id.decoderSurface );
      
      mFilePath = getIntent().getStringExtra( EXTRA_FILE_PATH );

      getActionBar().hide();
   }

   @Override
   protected void onResume() {
      super.onResume();

      mSurfaceView.getHolder().addCallback( this );
   }

   @Override
   public void surfaceChanged( SurfaceHolder holder, int format, int width, int height ) {
      if ( mDecoder == null ) {
         mDecoder = new SurfaceDecoder( mSurfaceView, mFilePath );
         float videoWidth = mDecoder.getVideoWidth();
         float videoHeight = mDecoder.getVideoHeight();

         if ( videoWidth != -1 && videoHeight != -1 ) {
            resizeSurfaceViewForVideo( videoWidth, videoHeight );
         }
         mDecoder.start();
      }
   }

   @Override
   public void surfaceCreated( SurfaceHolder holder ) {
      // TODO Auto-generated method stub

   }

   @Override
   public void surfaceDestroyed( SurfaceHolder holder ) {
      // TODO Auto-generated method stub

   }

   private void resizeSurfaceViewForVideo( float videoWidth, float videoHeight ) {

      float surfaceWidth = mSurfaceView.getWidth();
      float surfaceHeight = mSurfaceView.getHeight();

      float ratio_width = surfaceWidth / videoWidth;
      float ratio_height = surfaceHeight / videoHeight;
      float aspectratio = videoWidth / videoHeight;

      RelativeLayout.LayoutParams layoutParams = (LayoutParams) mSurfaceView.getLayoutParams();

      if ( ratio_width > ratio_height ) {
         layoutParams.width = (int) ( surfaceHeight * aspectratio );
         layoutParams.height = (int) surfaceHeight;
      } else {
         layoutParams.width = (int) surfaceWidth;
         layoutParams.height = (int) ( surfaceWidth / aspectratio );
      }

      Log.d( "This", String.format( "SAJM - setting layout size %d, %d from video size %f, %f", layoutParams.width, layoutParams.height, videoWidth, videoHeight ) );

      mSurfaceView.setLayoutParams( layoutParams );
   }

}

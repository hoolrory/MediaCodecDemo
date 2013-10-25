package com.roryhool.mediacodecdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.roryhool.mediacodecdemo.SurfaceEncoder.EncoderListener;

public class EncodeActivity extends Activity {

   SurfaceView mSurfaceView;

   String      mFilePath;

   Button      mButton;

   ProgressBar mProgress;

   @Override
   protected void onCreate( Bundle savedInstanceState ) {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_encode );

      mButton = (Button) findViewById( R.id.watchVideoButton );

      mProgress = (ProgressBar) findViewById( R.id.createProgress );

      mFilePath = Environment.getExternalStorageDirectory().getPath() + "/test.mp4";
   }

   public void onClickCreateVideo( View view ) {

      mProgress.setVisibility( View.VISIBLE );
      mButton.setEnabled( false );

      SurfaceEncoder encoder = new SurfaceEncoder();
      encoder.setOutputPath( mFilePath );
      encoder.setEncoderSource( new SimpleSource( this ) );
      encoder.addEncoderListener( mEncoderListener );
      encoder.start();
   }

   public void onClickWatchVideo( View view ) {
      Intent intent = new Intent( this, DecodeToSurfaceActivity.class );
      intent.putExtra( DecodeToSurfaceActivity.EXTRA_FILE_PATH, mFilePath );
      startActivity( intent );
   }

   EncoderListener mEncoderListener = new EncoderListener() {

      @Override
      public void encoderSucceeded() {
         Log.d( "this", "SAJM - got succeeded" );
         mProgress.setVisibility( View.INVISIBLE );
         mButton.setEnabled( true );
      }

      @Override
      public void encoderFailed() {
         
      }
   };

}

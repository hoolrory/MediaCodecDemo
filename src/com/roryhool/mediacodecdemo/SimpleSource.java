package com.roryhool.mediacodecdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.roryhool.mediacodecdemo.SurfaceEncoder.EncoderSource;

public class SimpleSource implements EncoderSource {

   Context mContext;

   Paint mPaint;

   int   mDuration = 5 * 1000;

   int   mWidth    = 720;
   int   mHeight   = 480;

   public SimpleSource( Context context ) {

      mContext = context;

      mPaint = new Paint();
      mPaint.setAntiAlias( true );
      mPaint.setDither( true );
      mPaint.setTextSize( context.getResources().getDimension( R.dimen.simple_source_font_size ) );
   }

   boolean done = false;

   @Override
   public boolean renderFrame( Canvas canvas, long time, long interval ) {

      mPaint.setColor( mContext.getResources().getColor( R.color.msu_green ) );
      canvas.drawRect( 0, 0, canvas.getWidth(), canvas.getHeight(), mPaint );

      mPaint.setColor( mContext.getResources().getColor( R.color.msu_white ) );
      canvas.drawText( String.format( "%.2f", (float) time / 1000f ), 100, 100, mPaint );

      return time + interval <= mDuration;
   }

   @Override
   public int getWidth() {
      return mWidth;
   }

   @Override
   public int getHeight() {
      return mHeight;
   }

}

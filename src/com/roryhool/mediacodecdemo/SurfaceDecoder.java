package com.roryhool.mediacodecdemo;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

public class SurfaceDecoder {

   private static final String TAG = "Decoder";

   SurfaceView                 mSurfaceView;

   private DecoderThread       mThread = null;

   float                       mVideoWidth  = -1;
   float                       mVideoHeight = -1;

   public SurfaceDecoder( SurfaceView surfaceView, String filePath ) {
      mSurfaceView = surfaceView;
      calculateMediaStats( filePath );
      mThread = new DecoderThread( surfaceView.getHolder().getSurface(), filePath );
   }

   private void calculateMediaStats( String filePath ) {

      Log.d( TAG, "Got string " + filePath );
      MediaMetadataRetriever r = new MediaMetadataRetriever();

      if ( r != null ) {
         try {
            r.setDataSource( filePath );
         } catch ( RuntimeException exception ) {
            r.release();
            return;
         }

         String widthString = r.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH );
         String heightString = r.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT );

         r.release();

         mVideoWidth = Float.parseFloat( widthString );
         mVideoHeight = Float.parseFloat( heightString );
      }
   }

   public float getVideoWidth() {
      return mVideoWidth;
   }

   public float getVideoHeight() {
      return mVideoHeight;
   }

   public void start() {
      mThread.start();
   }

   private class DecoderThread extends Thread {

      Surface mSurface;
      
      String mFilePath;

      public DecoderThread( Surface surface, String filePath ) {
         mSurface = surface;
         mFilePath = filePath;

         Log.d( "this", String.format( "got output path %s", mFilePath ) );
      }

      @Override
      public void run() {

         MediaExtractor extractor = new MediaExtractor();
         try {
            extractor.setDataSource( mFilePath );
         } catch ( IOException e ) {
            e.printStackTrace();
         }

         int videoIndex = 0;
         
         for ( int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++ ) {
            MediaFormat format = extractor.getTrackFormat( trackIndex );

            String mime = format.getString( MediaFormat.KEY_MIME );
            if ( mime != null ) {
               if ( mime.equals( "video/avc" ) ) {
                  extractor.selectTrack( trackIndex );
                  videoIndex = trackIndex;
                  break;
               }
            }
         }

         MediaCodec decoder = MediaCodec.createDecoderByType( "video/avc" );
         decoder.configure( extractor.getTrackFormat( videoIndex ), mSurface, null, 0 );
         decoder.start();

         BufferInfo info = new BufferInfo();
         boolean isEOS = false;
         long startMs = System.currentTimeMillis();

         ByteBuffer[] inputBuffers = decoder.getInputBuffers();
         ByteBuffer[] outputBuffers = decoder.getOutputBuffers();

         Log.d( TAG, String.format( "Got ibuffers %d and o buffers %d", inputBuffers.length, outputBuffers.length ) );
         while ( !Thread.interrupted() ) {
            if ( !isEOS ) {
               int inIndex = decoder.dequeueInputBuffer( 10000 );
               Log.d( TAG, String.format( "Got index %d", inIndex ) );
               if ( inIndex >= 0 ) {
                  ByteBuffer buffer = inputBuffers[inIndex];
                  int sampleSize = extractor.readSampleData( buffer, 0 );
                  if ( sampleSize < 0 ) {
                     Log.d( TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM" );
                     decoder.queueInputBuffer( inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM );
                     isEOS = true;
                  } else {
                     Log.d( TAG, "InputBuffer ADVANCING" );
                     decoder.queueInputBuffer( inIndex, 0, sampleSize, extractor.getSampleTime(), 0 );
                     extractor.advance();
                  }
               }
            }

            int outIndex = decoder.dequeueOutputBuffer( info, 10000  );
            switch ( outIndex ) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
               Log.d( TAG, "INFO_OUTPUT_BUFFERS_CHANGED" );
               outputBuffers = decoder.getOutputBuffers();
               break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
               Log.d( TAG, "New format " + decoder.getOutputFormat() );
               break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
               Log.d( TAG, "dequeueOutputBuffer timed out!" );
               break;
            default:
               ByteBuffer buffer = outputBuffers[outIndex];
               Log.v( TAG, "We can't use this buffer but render it due to the API limit, " + buffer );

               // We use a very simple clock to keep the video FPS, or the video
               // playback will be too fast
               while ( info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs ) {
                  try {
                     sleep( 10 );
                  } catch ( InterruptedException e ) {
                     e.printStackTrace();
                     break;
                  }
               }
               decoder.releaseOutputBuffer( outIndex, true );
               break;
            }

            if ( ( info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM ) != 0 ) {
               Log.d( TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM" );
               break;
            }
         }

         decoder.stop();
         decoder.release();
         extractor.release();
      }
   }

   public void renderFrame( Canvas canvas ) {
      mSurfaceView.buildDrawingCache();
      Bitmap bitmap = mSurfaceView.getDrawingCache();
      canvas.drawBitmap( bitmap, null, new Rect( 0, 0, canvas.getWidth(), canvas.getHeight() ), null );
      // mSurfaceView.draw( canvas );
   }
}

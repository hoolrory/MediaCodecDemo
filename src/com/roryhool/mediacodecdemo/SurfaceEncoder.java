package com.roryhool.mediacodecdemo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.Surface.OutOfResourcesException;

public class SurfaceEncoder {

   public static final int ENCODER_STATUS    = 100;
   public static final int ENCODER_SUCCEEDED = 101;
   public static final int ENCODER_FAILED    = 102;

   public interface EncoderSource {
      public boolean renderFrame( Canvas canvas, long time, long interval );
      public int getWidth();
      public int getHeight();
   }
   
   public interface EncoderListener {
      public void encoderSucceeded();
      public void encoderFailed();
   }

   private static final String TAG = "Encoder";

   private static final String MIME_TYPE = "video/avc";
   private static final int      FRAME_RATE      = 25;
   private static final int      IFRAME_INTERVAL = 1;

   String                        mOutputPath;

   int mWidth;
   int mHeight;

   int mBitRate = 2000000;

   private MediaCodec            mEncoder;
   private Surface               mSurface;
   private MediaMuxer            mMuxer;
   private int                   mTrackIndex;
   private boolean               mMuxerStarted;

   private MediaCodec.BufferInfo mBufferInfo;

   EncoderSource mSource;

   EncoderThread                 mThread;

   ArrayList<EncoderListener>    mListeners      = new ArrayList<EncoderListener>();

   EncoderHandler                mHandler;

   public SurfaceEncoder() {
      mHandler = new EncoderHandler( this );
      mThread = new EncoderThread();
   }

   public void setOutputPath( String outputPath ) {
      mOutputPath = outputPath;
   }

   public void setEncoderSource( EncoderSource source ) {
      mSource = source;
   }

   public ArrayList<EncoderListener> getListeners() {
      return mListeners;
   }
   
   public void addEncoderListener( EncoderListener listener ) {
      mListeners.add( listener );
   }

   public void start() {
      mThread.start();
   }
   
   private static class EncoderHandler extends Handler {

      SurfaceEncoder mEncoder;

      public EncoderHandler( SurfaceEncoder encoder ) {
         mEncoder = encoder;
      }
      
      @Override
      public void handleMessage( Message msg ) {
         if ( msg.what == SurfaceEncoder.ENCODER_STATUS ) {
            if( msg.arg1 == SurfaceEncoder.ENCODER_SUCCEEDED ) {
               for ( EncoderListener listener : mEncoder.getListeners() ) {
                  listener.encoderSucceeded();
               }
            } else if ( msg.arg1 == SurfaceEncoder.ENCODER_FAILED ) {
               for ( EncoderListener listener : mEncoder.getListeners() ) {
                  listener.encoderFailed();
               }
            }
         } 
      }
    };

   private class EncoderThread extends Thread {

      @Override
      public void run() {

         if ( mSource == null ) {
            throw new NullPointerException( "Need to set an encoder source on the surfaceEncoder" );
         }

         boolean succeeded = true;
         try {
            prepareEncoder();
            
            boolean framesRemaining = true;
            
            int frameCount = 0;

            while ( framesRemaining ) {
               drainEncoder( false );
               framesRemaining = renderFromSource( computePresentationTimeMs( frameCount ) );
               frameCount++;
            }

            drainEncoder( true );
         } catch ( Exception e ) {
            succeeded = false;
            e.printStackTrace();
         } finally {
            releaseEncoder();
         }

         int status = -1;

         if ( succeeded ) {
            status = ENCODER_SUCCEEDED;
         } else {
            status = ENCODER_SUCCEEDED;
         }

         Message.obtain( mHandler, ENCODER_STATUS, status, -1 ).sendToTarget();
      }

      private void prepareEncoder() {
         mBufferInfo = new MediaCodec.BufferInfo();

         MediaFormat format = MediaFormat.createVideoFormat( MIME_TYPE, mSource.getWidth(), mSource.getHeight() );

         Log.d( TAG, String.format( "KAJM - getting size %d, %d", mSource.getWidth(), mSource.getHeight() ) );
         format.setInteger( MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface );
         format.setInteger( MediaFormat.KEY_BIT_RATE, mBitRate );
         format.setInteger( MediaFormat.KEY_FRAME_RATE, FRAME_RATE );
         format.setInteger( MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL );
         format.setInteger( "stride", mSource.getWidth() );
         format.setInteger( "slice-height", mSource.getHeight() );

         mEncoder = MediaCodec.createEncoderByType( MIME_TYPE );
         mEncoder.configure( format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE );
         mSurface = mEncoder.createInputSurface();
         mEncoder.start();

         try {
            mMuxer = new MediaMuxer( mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 );
         } catch ( IOException ioe ) {
            throw new RuntimeException( "MediaMuxer creation failed", ioe );
         }

         mTrackIndex = -1;
         mMuxerStarted = false;
      }

      private boolean renderFromSource( long time ) {
         Canvas canvas = null;
         try {
            canvas = mSurface.lockCanvas( null );
         } catch ( IllegalArgumentException e ) {
            e.printStackTrace();
            return false;
         } catch ( OutOfResourcesException e ) {
            e.printStackTrace();
            return false;
         }

         boolean framesRemaining = mSource.renderFrame( canvas, time, 1000 / FRAME_RATE );

         mSurface.unlockCanvasAndPost( canvas );

         return framesRemaining;
      }

      private void releaseEncoder() {

         if ( mEncoder != null ) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
         }
         if ( mSurface != null ) {
            mSurface.release();
            mSurface = null;
         }
         if ( mMuxer != null ) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
         }
      }

      private void drainEncoder( boolean endOfStream ) {
         final int TIMEOUT_USEC = 10000;

         if ( endOfStream ) {
            mEncoder.signalEndOfInputStream();
         }

         ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
         while ( true ) {
            int encoderStatus = mEncoder.dequeueOutputBuffer( mBufferInfo, TIMEOUT_USEC );
            if ( encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER ) {
               // no output available yet
               if ( !endOfStream ) {
                  break; // out of while
               } else {
                  Log.d( TAG, "no output available, spinning to await EOS" );
               }
            } else if ( encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ) {
               // not expected for an encoder
               encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if ( encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {
               // should happen before receiving buffers, and should only happen once
               if ( mMuxerStarted ) {
                  throw new RuntimeException( "format changed twice" );
               }
               MediaFormat newFormat = mEncoder.getOutputFormat();
               Log.d( TAG, "encoder output format changed: " + newFormat );

               // now that we have the Magic Goodies, start the muxer
               mTrackIndex = mMuxer.addTrack( newFormat );
               mMuxer.start();
               mMuxerStarted = true;
            } else if ( encoderStatus < 0 ) {
               Log.w( TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus );
               // let's ignore it
            } else {
               ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
               if ( encodedData == null ) {
                  throw new RuntimeException( "encoderOutputBuffer " + encoderStatus + " was null" );
               }

               if ( ( mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG ) != 0 ) {
                  // The codec config data was pulled out and fed to the muxer when we got
                  // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
                  Log.d( TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG" );
                  mBufferInfo.size = 0;
               }

               if ( mBufferInfo.size != 0 ) {
                  if ( !mMuxerStarted ) {
                     throw new RuntimeException( "muxer hasn't started" );
                  }

                  // adjust the ByteBuffer values to match BufferInfo (not needed?)
                  encodedData.position( mBufferInfo.offset );
                  encodedData.limit( mBufferInfo.offset + mBufferInfo.size );

                  mMuxer.writeSampleData( mTrackIndex, encodedData, mBufferInfo );
                  Log.d( TAG, "sent " + mBufferInfo.size + " bytes to muxer" );
               }

               mEncoder.releaseOutputBuffer( encoderStatus, false );

               if ( ( mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM ) != 0 ) {
                  if ( !endOfStream ) {
                     Log.w( TAG, "reached end of stream unexpectedly" );
                  } else {
                     Log.d( TAG, "end of stream reached" );
                  }
                  break; // out of while
               }
            }
         }
      }

      private long computePresentationTimeMs( int frameIndex ) {
         return frameIndex * 1000 / FRAME_RATE;
      }
   }
}

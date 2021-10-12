package com.example.mediaframes;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import com.mojang.blaze3d.systems.RenderSystem;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.librealsense.frame;
import org.bytedeco.opencv.opencv_core.Mat;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL43;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.vector.Matrix4f;

import static org.lwjgl.opengl.GL42.*;

public class MediaPlayer implements Runnable {

    private static final String VIDEO_SOURCE = "C:\\Users\\Stuart\\Downloads\\Picard.mp4";

    private long startTime;

    private FFmpegFrameGrabber frameGrabber;
    private Frame capturedFrame = null;
    private int videoFrameCount = 0;
    private int audioFrameCount = 0;
    private float oneVideoFrameTime;
    private int currentFrameCount = 0;
    SourceDataLine soundLine;
    private int textureIndex = 0;
    private int[] glPixelBufferId = new int[2];
    private int[] glTextureId = new int[2];
    private ByteBuffer[] pixelData = new ByteBuffer[2];
    private static final int QUEUE_SIZE = 1000;

    long t1, t2, t3, t4, t5, t6, t7;

    private ArrayBlockingQueue<Frame> videoFrames = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private ArrayBlockingQueue<Frame> audioFrames = new ArrayBlockingQueue<>(QUEUE_SIZE);

    private boolean isPlaying;

    public MediaPlayer() {
        this(VIDEO_SOURCE);
    }

    public MediaPlayer(String filename) {
        try {
            System.out.println("Media Player starting!");

            frameGrabber = new FFmpegFrameGrabber(filename);
            frameGrabber.setVideoOption("threads", "1");
            frameGrabber.setOption("hwaccel", "cuda");
            frameGrabber.setOption("hwaccel_output_format", "cuda");
            //frameGrabber.setOption("preset", "veryfast");
            frameGrabber.setOption("tune", "zerolatency");
            frameGrabber.start();
            oneVideoFrameTime = 1000f * 1000f * 1000f / (float)frameGrabber.getVideoFrameRate();

            final AudioFormat audioFormat = new AudioFormat(frameGrabber.getSampleRate(), 16, frameGrabber.getAudioChannels(), true, true);
            final DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            soundLine = (SourceDataLine) AudioSystem.getLine(info);
            soundLine.open(audioFormat);
            soundLine.start();

            setupPixelBuffers(frameGrabber.getImageWidth(), frameGrabber.getImageHeight(), 3);
            setupTextures(frameGrabber.getImageWidth(), frameGrabber.getImageHeight(), 3);
            //RenderSystem.recordRenderCall(this::setupTexture);

            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getHeight() {
        return frameGrabber == null ? 0 : frameGrabber.getImageHeight();
    }

    public int getWidth() {
        return frameGrabber == null ? 0 : frameGrabber.getImageWidth();
    }

    public int getImageChannels() {
        return frameGrabber == null ? 0 : frameGrabber.getBitsPerPixel() / 8;
    }

    public void play() {
        this.isPlaying = true;
    }

    @Override
    public void run() {
        try {
            
            int l = 2;
            boolean rendered = false;
            while(l > 0) {
                l--;

                t1 = System.nanoTime();
                t2 = t1;
                t3 = t1;
                t4 = t1;
                t5 = t1;
                t6 = t1;
                t7 = t1;

                if (
                    !isPlaying
                    ||
                    audioFrames.size() < 20
                    ||
                    videoFrames.size() < 20
                ) {
                    capturedFrame = frameGrabber.grab();

                    t2 = System.nanoTime();
                    t3 = t2;
                    t4 = t2;

                    if (capturedFrame == null) {
                        System.out.format("Done! Found %d / %d frames (audio / images)%n", audioFrameCount, videoFrameCount);
                        System.out.format("start %d end %d duration %d%n", startTime, System.nanoTime(), startTime - System.nanoTime());
                        System.out.format("Length (frames) %d Duration %d%n", frameGrabber.getLengthInVideoFrames(), frameGrabber.getLengthInVideoFrames() * oneVideoFrameTime);
                        frameGrabber.stop();
                        // quit the while loop
                        break;
                    }
    
                    if (capturedFrame.samples != null) {
                        audioFrameCount++;
    
                        audioFrames.put(capturedFrame.clone());
    
                        //System.out.format("Found an audio frame %d%n", audioFrameCount);

                        t3 = System.nanoTime();

                    }

    
                    if (capturedFrame.image != null) {
    
                        videoFrameCount++;
                        // Handle the video
                    
                        videoFrames.put(capturedFrame.clone());

                        // RenderSystem.recordRenderCall(() -> 
                        //     renderTexture(image.get())
                        // );
    
                        //System.out.format("Found a video frame %d%n", videoFrameCount);

                        t4 = System.nanoTime();
                    }

                }

                t5 = System.nanoTime();
                t6 = t5;
                t7 = t5;

                // only start playing after we have some audio and video frames in the buffers
                if (videoFrameCount > 3 && audioFrames.size() > 3) {
                    isPlaying = true;
                    if (startTime == 0) {
                        startTime = System.nanoTime();
                    }
                }

                if (
                    // currentTime >= nextAudioFrameTime
                    // && 
                    !audioFrames.isEmpty()
                    && isPlaying
                ) {
                    Frame audioFrame = audioFrames.peek();
                    if (audioFrame.timestamp * 1000 <= System.nanoTime() - startTime) {
                        // Handle the audio                    

                        final ShortBuffer channelSamplesShortBuffer = (ShortBuffer)audioFrames.poll().samples[0];
                        channelSamplesShortBuffer.rewind();
                        final ByteBuffer outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2);
                        outBuffer.asShortBuffer().put(channelSamplesShortBuffer);
                        soundLine.write(outBuffer.array(), 0, outBuffer.capacity());
                        //System.out.format("De-queing an audio frame. Qsize = %s%n", audioFrames.size()); 
                        t6 = System.nanoTime();    
                    }
                }


                if (
                    isPlaying
                    && !videoFrames.isEmpty()
                ) {
                    Frame videoFrame = videoFrames.peek();
                    if (videoFrame.timestamp * 1000 <= System.nanoTime() - startTime) {
                        videoFrame = videoFrames.poll();
                        updateTexture((ByteBuffer)videoFrame.image[0]);
                        //System.out.format("De-queing a video frame. Qsize = %s%n", videoFrames.size());
                        currentFrameCount++;
                        t6 = System.nanoTime();

                        renderTexture();
                        rendered = true;
                        t7 = System.nanoTime();
                    }
                }


                System.out.printf("F:%4d  V:%4d  A:%4d  Grab:%,12d   QA:%,12d   QV:%,12d   UpA:%,12d   UpV:%,12d    Render:%,12d   Total:%,12d  Elapsed:%,12d%n", 
                    videoFrameCount, currentFrameCount, 0,
                    t2 - t1, t3 - t2, t4 - t3, t5 - t4, t6 - t5, t7 - t6, t7 - t1, System.nanoTime() - startTime
                );

            }
            if (!rendered) {
                renderTexture();
            }


        } catch (Exception e) {
            try {
                frameGrabber.stop();
            } catch (Exception e2) {
                // Do nothing
            }
            e.printStackTrace();
        }
    }

    private void setupPixelBuffers(int imageWidth, int imageHeight, int channelCount) {
        // create 2 pixel buffer objects, need to delete them when program exits.
        // glBufferData() with NULL pointer reserves only memory space.
        int dataSize = imageWidth * imageHeight * channelCount;
        glGenBuffers(glPixelBufferId);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, glPixelBufferId[0]);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, dataSize, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, glPixelBufferId[1]);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, dataSize, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    private void setupTextures(int imageWidth, int imageHeight, int channelCount) {
        RenderSystem.assertThread(RenderSystem::isOnRenderThread);
        
        int dataSize = imageWidth * imageHeight * channelCount;

        for (int i = 0; i < 2; i++) {
            this.pixelData[i] = ByteBuffer.allocateDirect(dataSize);
            this.glTextureId[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, glTextureId[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, imageWidth, imageHeight, 0, GL_BGR, GL_UNSIGNED_BYTE, (ByteBuffer)null);    
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }

    private void updateTexture(ByteBuffer image) {
        textureIndex = textureIndex ^ 1;
        RenderSystem.bindTexture(glTextureId[textureIndex]);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, glPixelBufferId[textureIndex]);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frameGrabber.getImageWidth(), frameGrabber.getImageHeight(), GL_BGR, GL_UNSIGNED_BYTE, 0);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, image.limit(), GL_STREAM_DRAW);
        pixelData[textureIndex].rewind();
        image.rewind();
        pixelData[textureIndex] = GL15C.glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY, image.limit(), pixelData[textureIndex]);
        pixelData[textureIndex].put(image);
        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    private void renderTexture() {
        RenderSystem.assertThread(RenderSystem::isOnRenderThread);

        float scale = Math.min(
            (float)Minecraft.getInstance().getMainWindow().getWidth() / frameGrabber.getImageWidth() / 2f,
            (float)Minecraft.getInstance().getMainWindow().getHeight() / frameGrabber.getImageHeight() / 2f
        );
        RenderSystem.pushMatrix();

        // RenderSystem.matrixMode(GL_PROJECTION);
        // RenderSystem.loadIdentity();
        // RenderSystem.ortho(0, Minecraft.getInstance().getMainWindow().getWidth(), Minecraft.getInstance().getMainWindow().getHeight(), 0, 1, -1);
        // RenderSystem.matrixMode(GL_MODELVIEW);
//        matrices.scale((float) scale, (float) scale, 1f);

        RenderSystem.bindTexture(glTextureId[textureIndex]);

        float x1 = 0;
        float x2 = frameGrabber.getImageWidth() * 2f;
        float y1 = 0;
        float y2 = frameGrabber.getImageHeight() * 2f;
        float blitOffset = 0;
        float minU = 0f;
        float maxU = 2f;
        float minV = 0f;
        float maxV = 2f;

        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.mul(scale);

        BufferBuilder bufferbuilder = Tessellator.getInstance().getBuffer();
        bufferbuilder.begin(GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
        bufferbuilder.pos(matrix, x1, y2, blitOffset).tex(minU, maxV).endVertex();
        bufferbuilder.pos(matrix, x2, y1, blitOffset).tex(maxU, minV).endVertex();
        bufferbuilder.pos(matrix, x1, y1, blitOffset).tex(minU, minV).endVertex();
        bufferbuilder.finishDrawing();
        RenderSystem.disableAlphaTest();
        WorldVertexBufferUploader.draw(bufferbuilder);
        RenderSystem.popMatrix();

    }

}

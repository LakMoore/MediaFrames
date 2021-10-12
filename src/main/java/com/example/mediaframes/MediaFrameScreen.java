package com.example.mediaframes;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class MediaFrameScreen extends Screen {

    MediaPlayer mediaPlayer = null;
    boolean ready = false;
    boolean started = false;
    long lastTime;

    protected MediaFrameScreen(ITextComponent titleIn, String filename) {
        super(titleIn);
        mediaPlayer = new MediaPlayer(filename);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        //super.render(matrixStack, mouseX, mouseY, partialTicks);
        mediaPlayer.run();
    }

    public void closeScreen() {
        mediaPlayer.finish();
        super.closeScreen();
    }
    
}

package com.discordsrv.heads;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static com.discordsrv.heads.Heads.services;

public class SkinStorage {

    private final File directory;

    public SkinStorage(File directory) {
        this.directory = directory;
        if (new File("/.dockerenv").exists()) {
            // don't bother caching textures unless running in container
            this.directory.mkdirs();
        }
    }

    public BufferedImage getTexture(String textureId) throws IOException {
        File file = getFile(textureId);
        if (file.exists()) return ImageIO.read(file);
        BufferedImage texture = services.getTexture(textureId);
        if (directory.exists()) ImageIO.write(texture, "png", file);
        return texture;
    }

    public File getFile(String textureId) {
        return new File(directory, textureId + ".png");
    }

}

package com.discordsrv.heads;

import com.discordsrv.heads.services.CraftHeadService;
import com.discordsrv.heads.services.MojangService;
import com.discordsrv.heads.services.Services;
import com.discordsrv.heads.services.profiles.Profile;
import com.discordsrv.heads.services.profiles.SkinData;
import com.discordsrv.heads.services.textures.AvatarType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Heads {

    public static boolean DEBUG = false;
    public static int DEFAULT_HEAD_SIZE = 64;

    public static Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Profile.class, new Profile(null, null, null))
            .registerTypeAdapter(SkinData.class, new SkinData(null, null, null, -1))
            .create();

    public static Services<?> services = new Services<>(
            new MojangService(),
            new CraftHeadService()
    );
    public static SkinStorage skinStorage = new SkinStorage(new File("/storage"));

    public static void main(String[] args) {
        Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add("static");
            config.requestLogger.http((ctx, executionTimeMs) -> {
                if (ctx.status().equals(HttpStatus.FOUND)) return; // don't log redirects
                System.out.println(Math.round(executionTimeMs) + "ms \t" + ctx.ip() + " [" + ctx.userAgent() + "] > " + ctx.status().getMessage() + " " + ctx.fullUrl());
            });
            config.router.apiBuilder(() -> {
                get(ctx -> ctx.redirect("https://github.com/DiscordSRV/Heads"));
                get("head.png", ctx -> {
                    // old DiscordSRV heads proxy request
                    // https://heads.discordsrv.com/head.png?texture={texture}&uuid={uuid}&name={username}&overlay
                    AvatarType type = ctx.queryParam("overlay") != null ? AvatarType.OVERLAY : AvatarType.HEAD;
                    String texture = ctx.queryParam("texture") != null && !ctx.queryParam("texture").isBlank() ? ctx.queryParam("texture") : null;
                    UUID uuid = ctx.queryParam("uuid") != null && !ctx.queryParam("uuid").isBlank() ? UUID.fromString(ctx.queryParam("uuid")) : null;
                    String username = ctx.queryParam("username") != null && !ctx.queryParam("username").isBlank() ? ctx.queryParam("username") : null;
                    String target = Stream.of(texture, uuid, username).filter(Objects::nonNull).findFirst().orElseThrow(BadRequestResponse::new).toString();
                    ctx.redirect(target + "/" + type.name().toLowerCase());
                });
                path("{target}", () -> {
                    get(ctx -> ctx.redirect(ctx.pathParam("target") + "/overlay"));
                    path("head", () -> {
                        get(ctx -> handle(ctx, AvatarType.HEAD));
                        get("{size}", ctx -> handle(ctx, AvatarType.HEAD, Integer.parseInt(ctx.pathParam("size"))));
                    });
                    path("overlay", () -> {
                        get(ctx -> handle(ctx, AvatarType.OVERLAY));
                        get("{size}", ctx -> handle(ctx, AvatarType.OVERLAY, Integer.parseInt(ctx.pathParam("size"))));
                    });
                    path("helm", () -> {
                        get(ctx -> handle(ctx, AvatarType.HELM));
                        get("{size}", ctx -> handle(ctx, AvatarType.HELM, Integer.parseInt(ctx.pathParam("size"))));
                    });
                });
            });
        }).start(7070);
    }

    public static void handle(Context ctx, AvatarType avatarType) {
        handle(ctx, avatarType, DEFAULT_HEAD_SIZE);
    }
    public static void handle(Context ctx, AvatarType avatarType, Integer scaledSize) {
        String target = ctx.pathParam("target");
        try {
            String textureId;
            Profile profile = null;
            if (target.length() <= 16) {
                // username
                profile = services.resolve(target);
            } else if (target.length() == 32) {
                // non-dashed uuid
                profile = services.resolve(uuidString(target));
            } else if (target.length() == 36) {
                // dashed uuid
                profile = services.resolve(UUID.fromString(target));
            }
            if (profile != null) {
                textureId = profile.skinData().textureId();
            } else {
                // texture id
                textureId = target;
            }

//            System.out.println("Handling " + avatarType + " for " + (profile == null ? "texture " + texture : "profile " + profile.username()));
            BufferedImage texture = skinStorage.getTexture(textureId);
            TextureIO headIO = new TextureIO(texture);
            if (avatarType.hasHelmet()) headIO.applyHelmet(avatarType == AvatarType.HELM);
            if (scaledSize != null && headIO.getHead().getWidth() != scaledSize) headIO.scale(scaledSize);

            ctx.contentType("image/png");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(headIO.getHead(), "png", outputStream);
            ctx.result(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static UUID uuidString(String uuid) {
        if (uuid.length() == 32) {
            uuid = uuid.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        }
        return UUID.fromString(uuid);
    }

}

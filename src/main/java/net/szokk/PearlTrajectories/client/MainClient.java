package net.szokk.PearlTrajectories;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class MainClient implements ClientModInitializer {
    private static final Map<EnderPearlEntity, List<Vec3d>> PEARL_TRAILS = new HashMap<>();
    private static final Map<EnderPearlEntity, Long> PEARL_REMOVAL_TIME = new HashMap<>();
    private static final Map<EnderPearlEntity, Vec3d> PEARL_LAST_POS = new HashMap<>();
    private static final Map<Vec3d, Long> LANDING_EFFECTS = new HashMap<>();

    private static final long LANDING_EFFECT_DURATION = 3000L;
    private static final long TRAIL_PERSIST_TIME = 2000L;
    private static final int CYLINDER_SEGMENTS = 16;
    private static final float CYLINDER_RADIUS = 0.05F;
    private static final double MIN_DISTANCE_THRESHOLD = 0.02D;

    public void onInitializeClient() {
        System.out.println("[Pearl Trajectories] Initializing :v");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null)
                return;

            Vec3d playerPos = client.player.getPos();
            Box searchBox = new Box(
                    playerPos.subtract(200.0D, 200.0D, 200.0D),
                    playerPos.add(200.0D, 200.0D, 200.0D));
            List<EnderPearlEntity> pearls = client.world.getEntitiesByClass(
                    EnderPearlEntity.class, searchBox, entity -> true);
            long currentTime = System.currentTimeMillis();

            for (EnderPearlEntity pearl : PEARL_TRAILS.keySet()) {
                if ((pearl.isRemoved() || !pearls.contains(pearl))
                        && !PEARL_REMOVAL_TIME.containsKey(pearl)) {
                    PEARL_REMOVAL_TIME.put(pearl, currentTime);

                    if (!isOwnedByPlayer(pearl, client)) {
                        Vec3d landingPos = PEARL_LAST_POS.get(pearl);
                        if (landingPos != null) {
                            LANDING_EFFECTS.put(landingPos, currentTime);


                            for (int i = 0; i < 30; i++) {
                                client.world.addParticle(
                                        ParticleTypes.PORTAL,
                                        landingPos.x + (Math.random() - 0.5) * 2,
                                        landingPos.y + Math.random() * 2,
                                        landingPos.z + (Math.random() - 0.5) * 2,
                                        (Math.random() - 0.5) * 0.5,
                                        Math.random() * 0.5,
                                        (Math.random() - 0.5) * 0.5
                                );
                            }
                        }
                    }
                }
            }

            PEARL_TRAILS.keySet().removeIf(pearl -> {
                boolean shouldRemove = PEARL_REMOVAL_TIME.containsKey(pearl)
                        && currentTime - PEARL_REMOVAL_TIME.get(pearl) > TRAIL_PERSIST_TIME;
                if (shouldRemove) {
                    PEARL_LAST_POS.remove(pearl);
                }
                return shouldRemove;
            });

            LANDING_EFFECTS.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > LANDING_EFFECT_DURATION);

            for (EnderPearlEntity pearl : pearls) {
                if (!pearl.isRemoved()) {
                    if (isOwnedByPlayer(pearl, client)) {
                        continue;
                    }

                    Vec3d currentPos = pearl.getPos();
                    Vec3d lastPos = PEARL_LAST_POS.get(pearl);

                    if (lastPos != null) {
                        double distance = lastPos.distanceTo(currentPos);

                        if (distance > MIN_DISTANCE_THRESHOLD) {
                            List<Vec3d> trail = PEARL_TRAILS.computeIfAbsent(pearl, k -> new ArrayList<>());
                            trail.add(currentPos);

                            if (trail.size() > 150)
                                trail.remove(0);

                            PEARL_LAST_POS.put(pearl, currentPos);
                        }
                    } else {
                        PEARL_LAST_POS.put(pearl, currentPos);
                    }
                }
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(this::renderTrajectories);
    }


    private boolean isOwnedByPlayer(EnderPearlEntity pearl, MinecraftClient client) {
        if (client.player == null) return false;

        var owner = pearl.getOwner();
        return owner != null && owner.getUuid().equals(client.player.getUuid());
    }

    private void renderTrajectories(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null)
            return;

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        VertexConsumer vertexConsumer = consumers.getBuffer(RenderLayer.getDebugQuads());

        matrices.push();

        if (!PEARL_TRAILS.isEmpty()) {
            for (Map.Entry<EnderPearlEntity, List<Vec3d>> entry : PEARL_TRAILS.entrySet()) {
                if (entry.getValue().size() >= 2) {
                    renderCylindricalTrail(entry.getValue(), cameraPos, matrices, vertexConsumer);
                }
            }
        }


        if (!LANDING_EFFECTS.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<Vec3d, Long> entry : LANDING_EFFECTS.entrySet()) {
                Vec3d landingPos = entry.getKey();
                long spawnTime = entry.getValue();
                float age = (currentTime - spawnTime) / 1000.0F;

                renderLandingEffect(landingPos, age, cameraPos, matrices, vertexConsumer);
            }
        }

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderCylindricalTrail(List<Vec3d> trail, Vec3d cameraPos,
                                        MatrixStack matrices, VertexConsumer vertexConsumer) {
        if (trail.size() < 2)
            return;

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d start = trail.get(i).subtract(cameraPos);
            Vec3d end = trail.get(i + 1).subtract(cameraPos);
            float progress = (float) i / (float)(trail.size() - 1);

            float r = lerp(0.5F, 0.3F, 0.0F, progress);
            float g = lerp(0.2F, 0.6F, 0.9F, progress);
            float b = lerp(0.9F, 0.8F, 1.0F, progress);

            int red = (int)(r * 255);
            int green = (int)(g * 255);
            int blue = (int)(b * 255);
            int alpha = (int)(220 * (1.0F - progress * 0.3F));

            renderCylinderSegment(start, end, matrices, vertexConsumer,
                    red/3, green/3, blue/2, alpha/4, CYLINDER_RADIUS * 2.0F);

            renderCylinderSegment(start, end, matrices, vertexConsumer,
                    red, green, blue, alpha, CYLINDER_RADIUS);

            renderCylinderSegment(start, end, matrices, vertexConsumer,
                    255, 255, 255, alpha/2, CYLINDER_RADIUS * 0.3F);
        }
    }

    private void renderLandingEffect(Vec3d landingPos, float age, Vec3d cameraPos,
                                     MatrixStack matrices, VertexConsumer vertexConsumer) {
        Vec3d relativePos = landingPos.subtract(cameraPos);

        float fadeProgress = age / 3.0F;
        float alpha = 1.0F - fadeProgress;
        if (alpha <= 0) return;

        int baseAlpha = (int)(alpha * 255);

        renderVerticalBeam(relativePos, landingPos.y, cameraPos, matrices, vertexConsumer, baseAlpha);
        renderLightningBolt(relativePos, cameraPos, matrices, vertexConsumer, baseAlpha, age);
    }

    private void renderVerticalBeam(Vec3d relativePos, double startY, Vec3d cameraPos,
                                    MatrixStack matrices, VertexConsumer vertexConsumer, int baseAlpha) {
        Vec3d beamStart = relativePos;
        Vec3d beamEnd = new Vec3d(relativePos.x, 255 - cameraPos.y, relativePos.z);

        float beamRadius = 0.1F;
        int segments = 8;

        for (int layer = 0; layer < 3; layer++) {
            float layerRadius = beamRadius * (1.0F + layer * 0.8F);
            int layerAlpha = baseAlpha / (layer + 1);

            int red = 100 + layer * 30;
            int green = 50 + layer * 50;
            int blue = 255;

            renderVerticalCylinder(beamStart, beamEnd, matrices, vertexConsumer,
                    red, green, blue, layerAlpha, layerRadius, segments);
        }
    }

    private void renderVerticalCylinder(Vec3d start, Vec3d end, MatrixStack matrices,
                                        VertexConsumer vertexConsumer, int red, int green,
                                        int blue, int alpha, float radius, int segments) {
        Vec3d[] bottomCircle = new Vec3d[segments];
        Vec3d[] topCircle = new Vec3d[segments];
        Vector3f[] normals = new Vector3f[segments];

        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / (double) segments;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            bottomCircle[i] = start.add(offsetX, 0, offsetZ);
            topCircle[i] = end.add(offsetX, 0, offsetZ);

            normals[i] = new Vector3f((float)offsetX, 0, (float)offsetZ).normalize();
        }

        for (int i = 0; i < segments; i++) {
            int nextI = (i + 1) % segments;

            addVertexWithNormal(vertexConsumer, matrices, bottomCircle[i], normals[i], red, green, blue, alpha);
            addVertexWithNormal(vertexConsumer, matrices, bottomCircle[nextI], normals[nextI], red, green, blue, alpha);
            addVertexWithNormal(vertexConsumer, matrices, topCircle[nextI], normals[nextI], red, green, blue, alpha);
            addVertexWithNormal(vertexConsumer, matrices, topCircle[i], normals[i], red, green, blue, alpha);
        }
    }

    private void renderLightningBolt(Vec3d relativePos, Vec3d cameraPos, MatrixStack matrices,
                                     VertexConsumer vertexConsumer, int baseAlpha, float age) {
        Random rand = new Random((long)(age * 1000));

        Vec3d skyStart = new Vec3d(
                relativePos.x + (rand.nextDouble() - 0.5) * 2,
                255 - cameraPos.y,
                relativePos.z + (rand.nextDouble() - 0.5) * 2
        );

        Vec3d current = skyStart;
        Vec3d target = relativePos;

        int segments = 20;

        for (int i = 0; i < segments; i++) {
            float t = (float)(i + 1) / segments;
            Vec3d nextTarget = skyStart.lerp(target, t);

            Vec3d offset = new Vec3d(
                    (rand.nextDouble() - 0.5) * 0.5,
                    0,
                    (rand.nextDouble() - 0.5) * 0.5
            );
            Vec3d next = nextTarget.add(offset);

            float flash = (float)Math.sin(age * 10) * 0.5F + 0.5F;
            int brightness = (int)(200 + 55 * flash);

            renderLightningSegment(current, next, matrices, vertexConsumer,
                    brightness, brightness, 255, baseAlpha);

            current = next;
        }

        current = skyStart;
        for (int i = 0; i < segments; i++) {
            float t = (float)(i + 1) / segments;
            Vec3d next = skyStart.lerp(target, t).add(
                    (rand.nextDouble() - 0.5) * 0.3,
                    0,
                    (rand.nextDouble() - 0.5) * 0.3
            );

            renderLightningSegment(current, next, matrices, vertexConsumer,
                    255, 255, 255, baseAlpha);
            current = next;
        }
    }

    private void renderLightningSegment(Vec3d start, Vec3d end, MatrixStack matrices,
                                        VertexConsumer vertexConsumer, int red, int green,
                                        int blue, int alpha) {
        Vec3d direction = end.subtract(start);
        Vec3d perpendicular = new Vec3d(-direction.z, 0, direction.x).normalize();
        float width = 0.04F;

        Vec3d offset = perpendicular.multiply(width);
        Vector3f normal = new Vector3f(0, 1, 0);

        addVertexWithNormal(vertexConsumer, matrices, start.add(offset), normal, red, green, blue, alpha);
        addVertexWithNormal(vertexConsumer, matrices, start.subtract(offset), normal, red, green, blue, alpha);
        addVertexWithNormal(vertexConsumer, matrices, end.subtract(offset), normal, red, green, blue, alpha);
        addVertexWithNormal(vertexConsumer, matrices, end.add(offset), normal, red, green, blue, alpha);
    }

    private void renderCylinderSegment(Vec3d start, Vec3d end, MatrixStack matrices,
                                       VertexConsumer vertexConsumer, int red, int green,
                                       int blue, int alpha, float radius) {
        Vec3d direction = end.subtract(start);
        if (direction.length() < 0.001D)
            return;

        Vec3d normalizedDirection = direction.normalize();
        Vec3d perpendicular1 = findPerpendicularVector(normalizedDirection);
        Vec3d perpendicular2 = normalizedDirection.crossProduct(perpendicular1).normalize();

        Vec3d[] startCircle = new Vec3d[CYLINDER_SEGMENTS];
        Vec3d[] endCircle = new Vec3d[CYLINDER_SEGMENTS];
        Vector3f[] normals = new Vector3f[CYLINDER_SEGMENTS];

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / (double) CYLINDER_SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            Vec3d offset = perpendicular1.multiply(cos * radius)
                    .add(perpendicular2.multiply(sin * radius));

            startCircle[i] = start.add(offset);
            endCircle[i] = end.add(offset);

            normals[i] = new Vector3f(
                    (float)(cos * perpendicular1.x + sin * perpendicular2.x),
                    (float)(cos * perpendicular1.y + sin * perpendicular2.y),
                    (float)(cos * perpendicular1.z + sin * perpendicular2.z)
            ).normalize();
        }

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            int nextI = (i + 1) % CYLINDER_SEGMENTS;

            addVertexWithNormal(vertexConsumer, matrices, startCircle[i], normals[i], red, green, blue, alpha);
            addVertexWithNormal(vertexConsumer, matrices, startCircle[nextI], normals[nextI], red, green, blue, alpha);
            addVertexWithNormal(vertexConsumer, matrices, endCircle[nextI], normals[nextI], red, green, blue, alpha);
            addVertexWithNormal(vertexConsumer, matrices, endCircle[i], normals[i], red, green, blue, alpha);
        }
    }

    private float lerp(float start, float mid, float end, float t) {
        if (t < 0.5F) {
            return start + (mid - start) * (t * 2.0F);
        } else {
            return mid + (end - mid) * ((t - 0.5F) * 2.0F);
        }
    }

    private Vec3d findPerpendicularVector(Vec3d vector) {
        if (Math.abs(vector.x) < 0.9D)
            return new Vec3d(1.0D, 0.0D, 0.0D).crossProduct(vector).normalize();
        return new Vec3d(0.0D, 1.0D, 0.0D).crossProduct(vector).normalize();
    }

    private void addVertexWithNormal(VertexConsumer vertexConsumer, MatrixStack matrices,
                                     Vec3d pos, Vector3f normal, int red, int green, int blue, int alpha) {
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(),
                        (float)pos.x, (float)pos.y, (float)pos.z)
                .color(red, green, blue, alpha)
                .normal(matrices.peek(), normal.x(), normal.y(), normal.z());
    }
}

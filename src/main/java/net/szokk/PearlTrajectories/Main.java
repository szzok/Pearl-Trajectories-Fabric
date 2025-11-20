//package net.szokk.PearlTrajectories;
//
//import com.mojang.blaze3d.systems.RenderSystem;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import net.fabricmc.api.ClientModInitializer;
//import net.fabricmc.api.EnvType;
//import net.fabricmc.api.Environment;
//import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
//import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//import net.minecraft.client.MinecraftClient;
//import net.minecraft.client.render.RenderLayer;
//import net.minecraft.client.render.VertexConsumer;
//import net.minecraft.client.render.VertexConsumerProvider;
//import net.minecraft.client.util.math.MatrixStack;
//import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
//import net.minecraft.util.math.Box;
//import net.minecraft.util.math.Vec3d;
//
//@Environment(EnvType.CLIENT)
//public class Main implements ClientModInitializer {
//    private static final Map<EnderPearlEntity, List<Vec3d>> PEARL_TRAILS = new HashMap<>();
//
//    private static final Map<EnderPearlEntity, Long> PEARL_REMOVAL_TIME = new HashMap<>();
//
//    private static final long TRAIL_PERSIST_TIME = 1000L;
//
//    private static final int CYLINDER_SEGMENTS = 8;
//
//    private static final float CYLINDER_RADIUS = 0.06F;
//
//    public void onInitializeClient() {
//        System.out.println("[Trajectories] Initializing");
//        ClientTickEvents.END_CLIENT_TICK.register(client -> {
//            if (client.world == null)
//                return;
//            if (client.player == null)
//                return;
//            Vec3d playerPos = client.player.getPos();
//            Box searchBox = new Box(
//                    playerPos.subtract(200.0D, 200.0D, 200.0D),
//                    playerPos.add(200.0D, 200.0D, 200.0D));
//            List<EnderPearlEntity> pearls = client.world.getEntitiesByClass(
//                    EnderPearlEntity.class, searchBox, entity -> true);
//            long currentTime = System.currentTimeMillis();
//
//            for (EnderPearlEntity pearl : PEARL_TRAILS.keySet()) {
//                if ((pearl.isRemoved() || !pearls.contains(pearl))
//                        && !PEARL_REMOVAL_TIME.containsKey(pearl))
//                    PEARL_REMOVAL_TIME.put(pearl, Long.valueOf(currentTime));
//            }
//
//            PEARL_TRAILS.keySet().removeIf(pearl ->
//                    PEARL_REMOVAL_TIME.containsKey(pearl)
//                            && currentTime - PEARL_REMOVAL_TIME.get(pearl) > TRAIL_PERSIST_TIME);
//
//            for (EnderPearlEntity pearl : pearls) {
//                if (!pearl.isRemoved()) {
//                    List<Vec3d> trail = PEARL_TRAILS.computeIfAbsent(pearl, k -> new ArrayList<>());
//                    Vec3d currentPos = pearl.getPos();
//                    if (trail.isEmpty()
//                            || trail.get(trail.size() - 1).distanceTo(currentPos) > 0.01D) {
//                        trail.add(currentPos);
//                        if (trail.size() > 100)
//                            trail.remove(0);
//                    }
//                }
//            }
//        });
//        WorldRenderEvents.AFTER_ENTITIES.register(this::renderTrajectories);
//    }
//
//    private void renderTrajectories(WorldRenderContext context) {
//        MinecraftClient client = MinecraftClient.getInstance();
//        if (client.world == null || PEARL_TRAILS.isEmpty())
//            return;
//        Vec3d cameraPos = context.camera().getPos();
//        MatrixStack matrices = context.matrixStack();
//        VertexConsumerProvider consumers = context.consumers();
//
//        RenderSystem.enableBlend();
//        RenderSystem.defaultBlendFunc();
//        RenderSystem.disableDepthTest();
//
//        VertexConsumer vertexConsumer = consumers.getBuffer(RenderLayer.getDebugLineStrip(1.0));
//
//        matrices.push();
//        for (Map.Entry<EnderPearlEntity, List<Vec3d>> entry : PEARL_TRAILS.entrySet())
//            renderCylindricalTrail(entry.getValue(), cameraPos, matrices, vertexConsumer);
//        matrices.pop();
//
//        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
//            immediate.draw();
//        }
//
//        RenderSystem.enableDepthTest();
//        RenderSystem.disableBlend();
//    }
//
//    private void renderCylindricalTrail(List<Vec3d> trail, Vec3d cameraPos,
//                                        MatrixStack matrices, VertexConsumer vertexConsumer) {
//        if (trail.size() < 2)
//            return;
//        for (int i = 0; i < trail.size() - 1; i++) {
//            Vec3d start = trail.get(i).subtract(cameraPos);
//            Vec3d end = trail.get(i + 1).subtract(cameraPos);
//            float progress = (float) i / trail.size();
//
//            int red = (int)(255.0F * (0.3F + 0.7F * progress));
//            int green = (int)(100.0F * progress);
//            int blue = (int)(255.0F * (1.0F - progress * 0.3F));
//
//            renderCylinderSegment(start, end, matrices, vertexConsumer, red, green, blue);
//        }
//    }
//
//    private void renderCylinderSegment(Vec3d start, Vec3d end, MatrixStack matrices,
//                                       VertexConsumer vertexConsumer, int red, int green, int blue) {
//        Vec3d direction = end.subtract(start);
//        if (direction.length() < 0.001D)
//            return;
//        Vec3d normalizedDirection = direction.normalize();
//        Vec3d perpendicular1 = findPerpendicularVector(normalizedDirection);
//        Vec3d perpendicular2 = normalizedDirection.crossProduct(perpendicular1).normalize();
//
//        Vec3d[] startCircle = new Vec3d[CYLINDER_SEGMENTS];
//        Vec3d[] endCircle = new Vec3d[CYLINDER_SEGMENTS];
//
//        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
//            double angle = 2 * Math.PI * i / (double) CYLINDER_SEGMENTS;
//            double cos = Math.cos(angle);
//            double sin = Math.sin(angle);
//            Vec3d offset = perpendicular1.multiply(cos * CYLINDER_RADIUS)
//                    .add(perpendicular2.multiply(sin * CYLINDER_RADIUS));
//            startCircle[i] = start.add(offset);
//            endCircle[i] = end.add(offset);
//        }
//
//        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
//            int nextI = (i + 1) % CYLINDER_SEGMENTS;
//            addVertex(vertexConsumer, matrices, startCircle[i], red, green, blue);
//            addVertex(vertexConsumer, matrices, endCircle[i], red, green, blue);
//            addVertex(vertexConsumer, matrices, endCircle[nextI], red, green, blue);
//            addVertex(vertexConsumer, matrices, startCircle[nextI], red, green, blue);
//        }
//    }
//
//    private Vec3d findPerpendicularVector(Vec3d vector) {
//        if (Math.abs(vector.x) < 0.9D)
//            return new Vec3d(1.0D, 0.0D, 0.0D).crossProduct(vector).normalize();
//        return new Vec3d(0.0D, 1.0D, 0.0D).crossProduct(vector).normalize();
//    }
//
//    private void addVertex(VertexConsumer vertexConsumer, MatrixStack matrices,
//                           Vec3d pos, int red, int green, int blue) {
//        vertexConsumer.vertex(matrices.peek().getPositionMatrix(),
//                        (float)pos.x, (float)pos.y, (float)pos.z)
//                .color(red, green, blue, 255)
//                .normal(matrices.peek(), 0.0F, 1.0F, 0.0F);
//    }
//}

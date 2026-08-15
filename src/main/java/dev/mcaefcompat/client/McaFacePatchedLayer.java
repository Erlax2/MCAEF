package dev.mcaefcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;

import forge.net.mca.MCAClient;
import forge.net.mca.client.model.CommonVillagerModel;
import forge.net.mca.client.render.layer.FaceLayer;
import forge.net.mca.client.resources.EyeTextureLayers;
import forge.net.mca.client.resources.EyeTextureLayers.Layer;
import forge.net.mca.client.resources.EyeTextureLayers.Side;
import forge.net.mca.entity.VillagerLike;
import forge.net.mca.entity.ai.Traits;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.lang.reflect.Method;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class McaFacePatchedLayer extends PatchedLayer {

    private final SkinnedMesh mesh;

    private static Method getSkinMethod;
    private static Method isBlinkingMethod;
    private static Method getBlinkSkinMethod;
    private static Method getOrGenerateEyeLayerMethod;
    private static Method getEyeColorMethod;

    static {
        try {
            getSkinMethod =
                    FaceLayer.class.getMethod(
                            "getSkin",
                            LivingEntity.class
                    );

            isBlinkingMethod =
                    FaceLayer.class.getDeclaredMethod(
                            "isBlinking",
                            LivingEntity.class
                    );

            getBlinkSkinMethod =
                    FaceLayer.class.getDeclaredMethod(
                            "getBlinkSkin"
                    );

            getOrGenerateEyeLayerMethod =
                    FaceLayer.class.getDeclaredMethod(
                            "getOrGenerateEyeLayer",
                            ResourceLocation.class,
                            EyeTextureLayers.Layer.class,
                            EyeTextureLayers.Side.class
                    );

            getEyeColorMethod =
                    FaceLayer.class.getDeclaredMethod(
                            "getEyeColor",
                            LivingEntity.class,
                            float.class,
                            boolean.class
                    );

            isBlinkingMethod.setAccessible(true);
            getBlinkSkinMethod.setAccessible(true);
            getOrGenerateEyeLayerMethod.setAccessible(true);
            getEyeColorMethod.setAccessible(true);

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public McaFacePatchedLayer(SkinnedMesh mesh) {
        this.mesh = mesh;
    }

    @Override
    protected void renderLayer(
            LivingEntityPatch entityPatch,
            LivingEntity entity,
            RenderLayer originalLayer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            OpenMatrix4f[] poses,
            float bob,
            float netHeadYaw,
            float ageInTicks,
            float partialTicks
    ) {

        if (!(originalLayer instanceof FaceLayer faceLayer)) {
            return;
        }

        if (!MCAClient.useVillagerRenderer(entity.getUUID())) {
            return;
        }

        McaMeshVisibility.restore(mesh);

        ResourceLocation baseFace =
                invokeGetSkin(faceLayer, entity);

        if (baseFace == null) {
            return;
        }

        Armature armature =
                entityPatch.getArmature();

        if (invokeIsBlinking(faceLayer, entity)) {
            ResourceLocation blink =
                    invokeGetBlinkSkin(faceLayer);

            if (blink != null && faceLayer.canUse(blink)) {
                drawTinted(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        blink,
                        0xFFFFFFFF
                );
            }

            return;
        }

        if (!faceLayer.canUse(baseFace)) {
            return;
        }

        // sclera
        ResourceLocation sclera =
                invokeGetEyeLayer(
                        faceLayer,
                        baseFace,
                        Layer.SCLERA,
                        Side.FULL
                );

        if (sclera != null) {
            drawTinted(
                    poseStack,
                    buffer,
                    packedLight,
                    armature,
                    poses,
                    sclera,
                    0xFFFFFFFF
            );
        }

        // details
        ResourceLocation details =
                invokeGetEyeLayer(
                        faceLayer,
                        baseFace,
                        Layer.DETAILS,
                        Side.FULL
                );

        if (details != null) {
            drawTinted(
                    poseStack,
                    buffer,
                    packedLight,
                    armature,
                    poses,
                    details,
                    0xFF808080
            );
        }

        VillagerLike villager =
                CommonVillagerModel.getVillager(entity);

        if (villager != null
                && villager.getTraits().hasTrait(Traits.HETEROCHROMIA)) {

            ResourceLocation irisLeft =
                    invokeGetEyeLayer(
                            faceLayer,
                            baseFace,
                            Layer.IRIS,
                            Side.LEFT
                    );

            if (irisLeft != null) {
                drawTinted(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        irisLeft,
                        invokeGetEyeColor(
                                faceLayer,
                                entity,
                                partialTicks,
                                true
                        )
                );
            }

            ResourceLocation irisRight =
                    invokeGetEyeLayer(
                            faceLayer,
                            baseFace,
                            Layer.IRIS,
                            Side.RIGHT
                    );

            if (irisRight != null) {
                drawTinted(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        irisRight,
                        invokeGetEyeColor(
                                faceLayer,
                                entity,
                                partialTicks,
                                false
                        )
                );
            }

        } else {
            ResourceLocation iris =
                    invokeGetEyeLayer(
                            faceLayer,
                            baseFace,
                            Layer.IRIS,
                            Side.FULL
                    );

            if (iris != null) {
                drawTinted(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        iris,
                        invokeGetEyeColor(
                                faceLayer,
                                entity,
                                partialTicks,
                                false
                        )
                );
            }
        }
    }

    private void drawTinted(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Armature armature,
            OpenMatrix4f[] poses,
            ResourceLocation texture,
            int argb
    ) {

        float a =
                ((argb >>> 24) & 0xFF) / 255.0F;
        float r =
                ((argb >>> 16) & 0xFF) / 255.0F;
        float g =
                ((argb >>> 8) & 0xFF) / 255.0F;
        float b =
                (argb & 0xFF) / 255.0F;

        if (a <= 0.0F) {
            a = 1.0F;
        }

        mesh.draw(
                poseStack,
                buffer,
                RenderType.entityTranslucent(texture),
                packedLight,
                r,
                g,
                b,
                a,
                OverlayTexture.NO_OVERLAY,
                armature,
                poses
        );
    }

    private static ResourceLocation invokeGetSkin(
            FaceLayer layer,
            LivingEntity entity
    ) {
        try {
            return (ResourceLocation)
                    getSkinMethod.invoke(layer, entity);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean invokeIsBlinking(
            FaceLayer layer,
            LivingEntity entity
    ) {
        try {
            return (boolean)
                    isBlinkingMethod.invoke(layer, entity);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static ResourceLocation invokeGetBlinkSkin(
            FaceLayer layer
    ) {
        try {
            return (ResourceLocation)
                    getBlinkSkinMethod.invoke(layer);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static ResourceLocation invokeGetEyeLayer(
            FaceLayer layer,
            ResourceLocation baseFace,
            Layer eyeLayer,
            Side side
    ) {
        try {
            return (ResourceLocation)
                    getOrGenerateEyeLayerMethod.invoke(
                            layer,
                            baseFace,
                            eyeLayer,
                            side
                    );
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static int invokeGetEyeColor(
            FaceLayer layer,
            LivingEntity entity,
            float partialTicks,
            boolean leftEyeVariant
    ) {
        try {
            return (int)
                    getEyeColorMethod.invoke(
                            layer,
                            entity,
                            partialTicks,
                            leftEyeVariant
                    );
        } catch (ReflectiveOperationException exception) {
            return 0xFFFFFFFF;
        }
    }
}

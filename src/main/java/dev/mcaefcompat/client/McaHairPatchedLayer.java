package dev.mcaefcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;

import forge.net.mca.MCA;
import forge.net.mca.MCAClient;
import forge.net.mca.client.model.CommonVillagerModel;
import forge.net.mca.client.render.layer.HairLayer;
import forge.net.mca.entity.VillagerLike;
import forge.net.mca.resources.data.skin.LayeredHair;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.lang.reflect.Method;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class McaHairPatchedLayer extends PatchedLayer {

    private final SkinnedMesh mesh;

    private static Method getTextureMethod;
    private static Method getOverlayTextureMethod;
    private static Method getOverlayMethod;

    static {
        try {
            getTextureMethod =
                    HairLayer.class.getDeclaredMethod(
                            "getTexture",
                            String.class
                    );

            getOverlayTextureMethod =
                    HairLayer.class.getDeclaredMethod(
                            "getOverlayTexture",
                            String.class
                    );

            getOverlayMethod =
                    HairLayer.class.getDeclaredMethod(
                            "getOverlay",
                            LivingEntity.class
                    );

            getTextureMethod.setAccessible(true);
            getOverlayTextureMethod.setAccessible(true);
            getOverlayMethod.setAccessible(true);

        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public McaHairPatchedLayer(SkinnedMesh mesh) {
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

        if (!(originalLayer instanceof HairLayer hairLayer)) {
            return;
        }

        if (!MCAClient.useVillagerRenderer(entity.getUUID())) {
            return;
        }

        McaMeshVisibility.restore(mesh);

        Armature armature =
                entityPatch.getArmature();

        float[] color =
                hairLayer.getColor(entity, partialTicks);

        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;

        if (color != null && color.length >= 3) {
            r = color[0];
            g = color[1];
            b = color[2];
        }

        VillagerLike villager =
                CommonVillagerModel.getVillager(entity);

        boolean renderedLayeredHair = false;

        /*
         * MCA puede construir el pelo usando varias categorías.
         *
         * Replicamos el mismo orden que HairLayer.renderFinal().
         */
        for (Object categoryObject :
                LayeredHair.Category.RENDER_ORDER) {

            LayeredHair.Category category =
                    (LayeredHair.Category) categoryObject;

            String hair =
                    villager.getLayeredHair(category);

            if (MCA.isBlankString(hair)) {
                continue;
            }

            renderedLayeredHair = true;

            ResourceLocation texture =
                    invokeTexture(hairLayer, hair);

            if (texture != null
                    && hairLayer.canUse(texture)) {

                draw(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        texture,
                        r, g, b
                );
            }

            ResourceLocation overlay =
                    invokeOverlayTexture(
                            hairLayer,
                            hair
                    );

            if (overlay != null
                    && hairLayer.canUse(overlay)) {

                draw(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        overlay,
                        1.0F,
                        1.0F,
                        1.0F
                );
            }
        }

        /*
         * Sistema antiguo / pelo no-layered.
         */
        if (!renderedLayeredHair) {

            ResourceLocation texture =
                    hairLayer.getSkin(entity);

            if (texture != null
                    && hairLayer.canUse(texture)) {

                draw(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        texture,
                        r, g, b
                );
            }

            ResourceLocation overlay =
                    invokeOverlay(
                            hairLayer,
                            entity
                    );

            if (overlay != null
                    && !overlay.equals(texture)
                    && hairLayer.canUse(overlay)) {

                draw(
                        poseStack,
                        buffer,
                        packedLight,
                        armature,
                        poses,
                        overlay,
                        1.0F,
                        1.0F,
                        1.0F
                );
            }
        }
    }

    private void draw(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Armature armature,
            OpenMatrix4f[] poses,
            ResourceLocation texture,
            float r,
            float g,
            float b
    ) {

        mesh.draw(
                poseStack,
                buffer,
                RenderType.entityCutoutNoCull(texture),
                packedLight,
                r,
                g,
                b,
                1.0F,
                OverlayTexture.NO_OVERLAY,
                armature,
                poses
        );
    }

    private static ResourceLocation invokeTexture(
            HairLayer layer,
            String hair
    ) {

        if (getTextureMethod == null) {
            return null;
        }

        try {
            return (ResourceLocation)
                    getTextureMethod.invoke(
                            layer,
                            hair
                    );

        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static ResourceLocation invokeOverlayTexture(
            HairLayer layer,
            String hair
    ) {

        if (getOverlayTextureMethod == null) {
            return null;
        }

        try {
            return (ResourceLocation)
                    getOverlayTextureMethod.invoke(
                            layer,
                            hair
                    );

        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static ResourceLocation invokeOverlay(
            HairLayer layer,
            LivingEntity entity
    ) {

        if (getOverlayMethod == null) {
            return null;
        }

        try {
            return (ResourceLocation)
                    getOverlayMethod.invoke(
                            layer,
                            entity
                    );

        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}

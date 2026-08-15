package dev.mcaefcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;

import forge.net.mca.MCAClient;
import forge.net.mca.client.render.layer.SkinLayer;

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

@SuppressWarnings({"rawtypes", "unchecked"})
public final class McaSkinPatchedLayer extends PatchedLayer {

    private final SkinnedMesh mesh;

    public McaSkinPatchedLayer(SkinnedMesh mesh) {
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
        if (!(originalLayer instanceof SkinLayer skinLayer)) {
            return;
        }

        // Solo dibujar esta capa cuando MCA está usando realmente
        // el modelo VILLAGER del jugador.
        if (!MCAClient.useVillagerRenderer(entity.getUUID())) {
            return;
        }

        McaMeshVisibility.restore(mesh);

        ResourceLocation texture = skinLayer.getSkin(entity);

        if (texture == null || !skinLayer.canUse(texture)) {
            return;
        }

        float[] color = skinLayer.getColor(entity, partialTicks);

        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;

        if (color != null && color.length >= 3) {
            r = color[0];
            g = color[1];
            b = color[2];
        }

        Armature armature = entityPatch.getArmature();

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
}

package dev.mcaefcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class NoopMcaPatchedLayer extends PatchedLayer {

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
        // Intencionalmente vacío.
        //
        // Sustituye temporalmente el RenderOriginalModelLayer("Root")
        // que Epic Fight genera para las layers desconocidas de MCA.
    }
}


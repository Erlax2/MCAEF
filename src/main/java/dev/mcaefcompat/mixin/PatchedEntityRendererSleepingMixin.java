package dev.mcaefcompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import forge.net.mca.entity.VillagerEntityMCA;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/*
 * MCAEF sleeping compatibility.
 *
 * Epic Fight ya posee soporte nativo para players durmiendo.
 * AbstractClientPlayerPatch.getModelMatrix() sustituye el
 * body yaw normal por el facing de la cama.
 *
 * EFMCA selecciona LivingMotions.SLEEP pero no reproduce
 * esa transformación especial.
 *
 * Este mixin aplica únicamente el delta necesario para que
 * la matriz normal del villager:
 *
 *     Y(180 - bodyYaw)
 *
 * termine siendo equivalente a la del player de Epic Fight:
 *
 *     Y(180 - bedYaw)
 *
 * BIPED_SLEEPING sigue siendo responsable de la pose.
 */
@Mixin(
        value = PatchedEntityRenderer.class,
        remap = false
)
public abstract class PatchedEntityRendererSleepingMixin {

    @Inject(
            method = "mulPoseStack",
            at = @At("TAIL"),
            remap = false
    )
    private void mcaefcompat$applySleepingBedRotation(
            PoseStack poseStack,
            Armature armature,
            LivingEntity entity,
            LivingEntityPatch entityPatch,
            float partialTicks,
            CallbackInfo ci
    ) {

        if (!(entity instanceof VillagerEntityMCA)) {
            return;
        }

        if (!entity.hasPose(Pose.SLEEPING)) {
            return;
        }

        /*
         * Epic Fight obtiene el BlockState correspondiente
         * a la cama y usa HORIZONTAL_FACING.
         *
         * Reproducimos ese comportamiento en vez de utilizar
         * una traslación vanilla adicional.
         */
        BlockPos sleepingPos =
                entity.getSleepingPos()
                        .orElse(null);

        if (sleepingPos == null) {
            return;
        }

        BlockState bedState =
                entity.level()
                        .getBlockState(sleepingPos);

        if (!bedState.isBed(
                entity.level(),
                sleepingPos,
                entity
        )) {
            return;
        }

        if (!bedState.hasProperty(
                BlockStateProperties.HORIZONTAL_FACING
        )) {
            return;
        }

        Direction direction =
                bedState.getValue(
                        BlockStateProperties.HORIZONTAL_FACING
                );

        /*
         * Valores utilizados por
         * AbstractClientPlayerPatch.getModelMatrix().
         *
         * WEST queda en 0 grados.
         */
        /*
         * Mapping EXACTO utilizado por
         * AbstractClientPlayerPatch de Epic Fight:
         *
         * EAST  ->  +90
         * WEST  ->  -90
         * SOUTH -> +180
         * NORTH ->    0
         */
        float bedYaw =
                switch (direction) {
                    case EAST  -> 90.0F;
                    case WEST  -> -90.0F;
                    case SOUTH -> 180.0F;
                    case NORTH -> 0.0F;
                    default    -> 0.0F;
                };

        /*
         * LivingEntityPatch.getModelMatrix() ya introdujo
         * -bodyYaw y PatchedEntityRenderer introdujo +180°.
         *
         * Estado actual:
         *
         *     180 - bodyYaw
         *
         * Queremos:
         *
         *     180 - bedYaw
         *
         * Por tanto:
         *
         *     correction = bodyYaw - bedYaw
         */
        float bodyYaw =
                Mth.rotLerp(
                        partialTicks,
                        entity.yBodyRotO,
                        entity.yBodyRot
                );

        float correction =
                bodyYaw - bedYaw;

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        correction
                )
        );
    }
}

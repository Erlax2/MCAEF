package dev.mcaefcompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.gameasset.Animations;

/*
 * EFMCA detecta correctamente que un VillagerEntityMCA
 * está durmiendo y selecciona LivingMotions.SLEEP.
 *
 * Sin embargo, MCAVillagerEntityPatch.initAnimator()
 * no registra ninguna animación para SLEEP.
 *
 * MCAEF completa únicamente ese mapping faltante.
 */
@Pseudo
@Mixin(
        targets = "net.forixaim.mcea.entity_patch.MCAVillagerEntityPatch",
        remap = false
)
public abstract class MCAVillagerEntityPatchMixin {

    @Unique
    private static boolean mcaefcompat$sleepMappingLogged;

    @Inject(
            method =
                    "initAnimator(Lyesman/epicfight/api/animation/Animator;)V",
            at = @At("TAIL"),
            remap = false
    )
    private void mcaefcompat$registerSleepingAnimation(
            Animator animator,
            CallbackInfo ci
    ) {

        animator.addLivingAnimation(
                LivingMotions.SLEEP,
                Animations.BIPED_SLEEPING
        );

        if (!mcaefcompat$sleepMappingLogged) {

            mcaefcompat$sleepMappingLogged = true;

            System.out.println(
                    "[MCAEFCompat] 0.0.24 "
                            + "EFMCA SLEEP -> BIPED_SLEEPING registered"
            );
        }
    }
}

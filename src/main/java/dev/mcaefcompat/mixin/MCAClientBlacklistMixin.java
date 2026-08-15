package dev.mcaefcompat.mixin;

import forge.net.mca.MCAClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/*
 * MCAEF 0.0.25
 *
 * MCA incluye Epic Fight por defecto en:
 *
 *     playerRendererBlacklist:
 *         epicfight = all
 *
 * Eso impide utilizar el renderer/player genetics de MCA
 * cuando Epic Fight está instalado.
 *
 * MCAEF ya proporciona la compatibilidad necesaria, por lo
 * que ignoramos EXCLUSIVAMENTE la entrada "epicfight" al
 * evaluar:
 *
 * - isPlayerRendererAllowed()
 * - isVillagerRendererAllowed()
 *
 * No modificamos Config.playerRendererBlacklist.
 * No escribimos mca.json.
 * No ignoramos otros mods de la blacklist.
 * No tocamos renderArms().
 */
@Mixin(
        value = MCAClient.class,
        remap = false
)
public abstract class MCAClientBlacklistMixin {

    @Unique
    private static boolean
            mcaefcompat$loggedPlayerBlacklistBypass;

    @Unique
    private static boolean
            mcaefcompat$loggedVillagerBlacklistBypass;


    /*
     * Predicate usado por:
     *
     * MCAClient.isPlayerRendererAllowed()
     *
     * Normalmente devuelve:
     *
     *     MCA.doesModExist(entry.getKey())
     *
     * Para Epic Fight hacemos que esta entrada no participe
     * en el cálculo de all/block_player.
     */
    @Inject(
            method =
                    "lambda$isPlayerRendererAllowed$6(Ljava/util/Map$Entry;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mcaefcompat$allowEpicFightPlayerRenderer(
            Map.Entry<String, String> entry,
            CallbackInfoReturnable<Boolean> cir
    ) {

        if (entry == null
                || !"epicfight".equals(entry.getKey())) {
            return;
        }

        /*
         * false = esta entrada no pasa el filter()
         * de mods incompatibles instalados.
         */
        cir.setReturnValue(false);

        if (!mcaefcompat$loggedPlayerBlacklistBypass) {

            mcaefcompat$loggedPlayerBlacklistBypass = true;

            System.out.println(
                    "[MCAEFCompat] 1.0.0 "
                            + "ignored MCA epicfight blacklist "
                            + "for player renderer"
            );
        }
    }


    /*
     * Misma operación para el renderer de villagers.
     *
     * Conservamos intactos:
     *
     * - forceVillagerPlayerModel
     * - otras entradas de blacklist
     * - block_villager de otros mods
     */
    @Inject(
            method =
                    "lambda$isVillagerRendererAllowed$8(Ljava/util/Map$Entry;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mcaefcompat$allowEpicFightVillagerRenderer(
            Map.Entry<String, String> entry,
            CallbackInfoReturnable<Boolean> cir
    ) {

        if (entry == null
                || !"epicfight".equals(entry.getKey())) {
            return;
        }

        cir.setReturnValue(false);

        if (!mcaefcompat$loggedVillagerBlacklistBypass) {

            mcaefcompat$loggedVillagerBlacklistBypass = true;

            System.out.println(
                    "[MCAEFCompat] 1.0.0 "
                            + "ignored MCA epicfight blacklist "
                            + "for villager renderer"
            );
        }
    }
}

package dev.mcaefcompat.client;

import dev.mcaefcompat.MCAEpicFightCompat;

import forge.net.mca.client.model.CommonVillagerModel;
import forge.net.mca.client.model.PlayerEntityExtendedModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = MCAEpicFightCompat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class McaPlayerVisibilityGuard {

    private static boolean loggedCorruption = false;

    private McaPlayerVisibilityGuard() {
    }

    /*
     * Esto corre ANTES de Epic Fight.
     *
     * PPlayerRenderer.prepareModel() copia estos .visible hacia
     * HumanoidMesh.setHidden().
     *
     * Por lo tanto arreglamos la FUENTE antes de que Epic Fight
     * pueda copiar un estado corrupto.
     */
    @SubscribeEvent(
            priority = EventPriority.HIGHEST,
            receiveCanceled = true
    )
    public static void beforeEpicFight(RenderLivingEvent.Pre event) {

        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player != player) {
            return;
        }

        /*
         * No tocar el preview MCA.
         */
        if (mc.screen != null) {
            return;
        }

        if (!(event.getRenderer().getModel()
                instanceof PlayerEntityExtendedModel model)) {
            return;
        }

        String playerModel =
                CommonVillagerModel
                        .getVillager(player)
                        .getPlayerModel()
                        .name();

        if (!"VILLAGER".equals(playerModel)) {
            return;
        }

        StringBuilder repaired =
                new StringBuilder();

        /*
         * Solo geometría CORE.
         *
         * No forzamos jacket/hat/sleeves/pants porque esos sí pueden
         * estar ocultos legítimamente por opciones de skin.
         */
        repair(model.head, "head", repaired);
        repair(model.body, "body", repaired);

        repair(model.leftArm, "leftArm", repaired);
        repair(model.rightArm, "rightArm", repaired);

        repair(model.leftLeg, "leftLeg", repaired);
        repair(model.rightLeg, "rightLeg", repaired);

        if (repaired.length() > 0 && !loggedCorruption) {

            loggedCorruption = true;

            System.out.println(
                    "[MCAEFCompat] 0.0.18 repaired corrupted "
                            + "PlayerModel visibility before Epic Fight: "
                            + repaired
            );
        }
    }

    private static void repair(
            ModelPart part,
            String name,
            StringBuilder repaired
    ) {

        if (part.visible) {
            return;
        }

        part.visible = true;

        if (repaired.length() > 0) {
            repaired.append(", ");
        }

        repaired.append(name);
    }
}

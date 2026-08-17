package dev.mcaefcompat.client;

import dev.mcaefcompat.MCAEpicFightCompat;

import forge.net.mca.client.gui.VillagerEditorScreen;
import forge.net.mca.entity.VillagerEntityMCA;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mod.EventBusSubscriber(
        modid = MCAEpicFightCompat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class McaEditorRenderBypass {

    private static final Set<Object> BYPASSED_EVENTS =
            Collections.newSetFromMap(
                    new IdentityHashMap<>()
            );

    private static boolean loggedVillagerPreview = false;
    private static boolean loggedPlayerPreview = false;

    private McaEditorRenderBypass() {
    }

    /*
     * MCA Editor usa dos rutas:
     *
     * VILLAGER
     *     -> VillagerEditorScreen.villager
     *
     * PLAYER / VANILLA
     *     -> Minecraft.player
     *
     * Epic Fight no debe reemplazar NINGUNA de esas dos
     * entidades mientras se renderizan dentro del editor.
     */
    @SubscribeEvent(
            priority = EventPriority.HIGHEST,
            receiveCanceled = true
    )
    public static void beforeEpicFight(
            RenderLivingEvent.Pre event
    ) {

        Minecraft mc =
                Minecraft.getInstance();

        if (!(mc.screen
                instanceof VillagerEditorScreen editor)) {
            return;
        }

        Object entity =
                event.getEntity();

        /*
         * VillagerEditorScreen usa tanto 'villager' como una segunda entidad
         * 'villagerVisualization' para las miniaturas de ropa/pelo/skin.
         * Mientras esta GUI esté abierta, cualquier VillagerEntityMCA
         * renderizado aquí es un preview y Epic Fight no debe sustituirlo.
         */
        boolean villagerPreview =
                entity instanceof VillagerEntityMCA;

        boolean playerPreview =
                entity instanceof AbstractClientPlayer player
                        && mc.player == player;

        if (!villagerPreview && !playerPreview) {
            return;
        }

        /*
         * Marcamos solo ESTE evento.
         *
         * Epic Fight escucha RenderLivingEvent.Pre con prioridad
         * normal y receiveCanceled=false, así que no verá este
         * evento.
         */
        BYPASSED_EVENTS.add(event);

        event.setCanceled(true);

        if (villagerPreview
                && !loggedVillagerPreview) {

            loggedVillagerPreview = true;

            System.out.println(
                    "[MCAEFCompat] MCA Editor: "
                            + "VILLAGER preview bypassed from Epic Fight/EFMCA"
                            + " entity="
                            + entity.getClass().getName()
                            + " renderer="
                            + event.getRenderer()
                            .getClass()
                            .getName()
                            + " model="
                            + event.getRenderer()
                            .getModel()
                            .getClass()
                            .getName()
            );
        }

        if (playerPreview
                && !loggedPlayerPreview) {

            loggedPlayerPreview = true;

            System.out.println(
                    "[MCAEFCompat] MCA Editor: "
                            + "PLAYER preview bypassed from Epic Fight"
                            + " renderer="
                            + event.getRenderer()
                            .getClass()
                            .getName()
                            + " model="
                            + event.getRenderer()
                            .getModel()
                            .getClass()
                            .getName()
            );
        }
    }

    /*
     * Epic Fight ya fue saltado.
     *
     * Volvemos a poner canceled=false para que Minecraft
     * continúe con el renderer ORIGINAL de la entidad.
     */
    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true
    )
    public static void afterEpicFight(
            RenderLivingEvent.Pre event
    ) {

        if (!BYPASSED_EVENTS.remove(event)) {
            return;
        }

        event.setCanceled(false);
    }
}

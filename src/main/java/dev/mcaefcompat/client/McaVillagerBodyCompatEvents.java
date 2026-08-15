package dev.mcaefcompat.client;

import dev.mcaefcompat.MCAEpicFightCompat;

import forge.net.mca.client.render.VillagerEntityMCARenderer;
import forge.net.mca.entity.VillagerEntityMCA;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(
        modid = MCAEpicFightCompat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class McaVillagerBodyCompatEvents {

    /*
     * Un PMCAVillagerRenderer puede renderizar miles de frames.
     * Registramos nuestro custom layer solamente una vez
     * por instancia del renderer.
     */
    private static final Map<Object, Boolean> REGISTERED =
            new IdentityHashMap<>();

    private McaVillagerBodyCompatEvents() {
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST,
            receiveCanceled = true
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderVillager(
            RenderLivingEvent.Pre event
    ) {

        if (!(event.getEntity()
                instanceof VillagerEntityMCA villager)) {
            return;
        }

        /*
         * Renderer MCA vanilla real.
         *
         * Lo necesitamos para construir nuestro SkinLayer
         * auxiliar y obtener exactamente la textura que
         * MCA utilizaría.
         */
        if (!(event.getRenderer()
                instanceof VillagerEntityMCARenderer originalRenderer)) {
            return;
        }

        ClientEngine clientEngine =
                ClientEngine.getInstance();

        if (clientEngine == null
                || clientEngine.renderEngine == null) {
            return;
        }

        Object patchedRenderer =
                clientEngine.renderEngine
                        .getEntityRenderer(villager);

        if (!(patchedRenderer
                instanceof PatchedLivingEntityRenderer renderer)) {
            return;
        }

        /*
         * No queremos alterar villagers de otros mods.
         *
         * Tampoco agregamos EFMCA como dependencia de
         * compilación: comprobamos el renderer por nombre.
         */
        if (!patchedRenderer
                .getClass()
                .getName()
                .equals(
                        "net.forixaim.mcea.renderer.PMCAVillagerRenderer"
                )) {
            return;
        }

        if (REGISTERED.containsKey(patchedRenderer)) {
            return;
        }

        renderer.addCustomLayer(
                new McaVillagerBodyPatchedLayer(
                        originalRenderer
                )
        );

        REGISTERED.put(
                patchedRenderer,
                Boolean.TRUE
        );

        System.out.println(
                "[MCAEFCompat] 0.0.24 registered"
                        + " MCA villager body bridge"
                        + " renderer="
                        + patchedRenderer
                                .getClass()
                                .getName()
        );
    }
}

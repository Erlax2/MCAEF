package dev.mcaefcompat.client;

import dev.mcaefcompat.MCAEpicFightCompat;

import forge.net.mca.client.model.CommonVillagerModel;
import forge.net.mca.client.render.layer.ClothingLayer;
import forge.net.mca.client.render.layer.FaceLayer;
import forge.net.mca.client.render.layer.HairLayer;
import forge.net.mca.client.render.layer.SkinLayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.renderer.patched.entity.PPlayerRenderer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(
        modid = MCAEpicFightCompat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientCompatEvents {

    private static final PatchedLayer NOOP =
            new NoopMcaPatchedLayer();

    /*
     * Mesh concreto BIPED/ALEX -> bridge de skin MCA.
     */
    private static final Map<SkinnedMesh, PatchedLayer> SKIN_BRIDGES =
            new IdentityHashMap<>();

    private static final Map<SkinnedMesh, PatchedLayer> CLOTHING_BRIDGES =
            new IdentityHashMap<>();

    private static final Map<SkinnedMesh, PatchedLayer> HAIR_BRIDGES =
            new IdentityHashMap<>();

    private static final Map<SkinnedMesh, PatchedLayer> FACE_BRIDGES =
            new IdentityHashMap<>();

    /*
     * PPlayerRenderer -> cuatro mappings MCA originales.
     *
     * Se restauran dentro del MCA Editor y cuando el jugador
     * deja de usar PlayerModel.VILLAGER.
     */
    private static final Map<Object, Map<Class<?>, PatchedLayer>>
            ORIGINAL_MCA_LAYERS = new IdentityHashMap<>();

    private static Field patchedLayersField;

    private static boolean loggedBridge = false;
    private static boolean loggedEditor = false;

    private ClientCompatEvents() {
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST,
            receiveCanceled = true
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderLiving(RenderLivingEvent.Pre event) {

        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        /*
         * Por ahora solo nuestro jugador local.
         */
        if (mc.player != player) {
            return;
        }

        ClientEngine clientEngine = ClientEngine.getInstance();

        if (clientEngine == null || clientEngine.renderEngine == null) {
            return;
        }

        /*
         * Este es el PPlayerRenderer REAL que Epic Fight usará
         * unos listeners más tarde durante este mismo
         * RenderLivingEvent.Pre.
         */
        Object patched =
                clientEngine.renderEngine.getEntityRenderer(player);

        if (!(patched instanceof PPlayerRenderer renderer)) {
            return;
        }

        Map<Class<?>, PatchedLayer> mappings =
                getMappings(renderer);

        if (mappings == null) {
            return;
        }

        /*
         * Capturamos los cuatro mappings MCA originales una sola vez.
         *
         * En un arranque nuevo del juego todavía no hemos modificado
         * el PPlayerRenderer, así que éstos son los mappings creados
         * originalmente por Epic Fight.
         */
        Map<Class<?>, PatchedLayer> originals =
                ORIGINAL_MCA_LAYERS.computeIfAbsent(
                        renderer,
                        ignored -> captureOriginals(mappings)
                );

        /*
         * MCA Editor:
         *
         * restauramos los modelos/layers MCA originales.
         *
         * Esto conserva el comportamiento que en 0.0.12 hizo
         * reaparecer correctamente el personaje dentro del editor.
         */
        if (isMcaScreen()) {

            restoreOriginals(renderer, originals);

            if (!loggedEditor) {
                loggedEditor = true;

                System.out.println(
                        "[MCAEFCompat] 0.0.19 MCA GUI -> "
                                + "original MCA layers restored"
                                + " screen="
                                + Minecraft.getInstance().screen.getClass().getName()
                                + " originalRenderer="
                                + event.getRenderer().getClass().getName()
                                + " originalModel="
                                + event.getRenderer().getModel().getClass().getName()
                );
            }

            return;
        }

        /*
         * Solo hacemos el bridge cuando el modelo seleccionado
         * en MCA es VILLAGER.
         */
        var villager =
                CommonVillagerModel.getVillager(player);

        if (villager == null) {
            restoreOriginals(renderer, originals);
            return;
        }

        var selectedPlayerModel =
                villager.getPlayerModel();

        /*
         * MCA puede no haber sincronizado todavía el PlayerModel
         * durante los primeros frames al entrar en un mundo.
         *
         * En ese caso no intentamos hacer el bridge todavía.
         */
        if (selectedPlayerModel == null) {
            restoreOriginals(renderer, originals);
            return;
        }

        String playerModel =
                selectedPlayerModel.name();

        if (!"VILLAGER".equals(playerModel)) {

            restoreOriginals(renderer, originals);
            return;
        }

        /*
         * Necesitamos el player patch para preguntarle a
         * PPlayerRenderer qué mesh corresponde realmente al jugador:
         *
         *    BIPED
         *       o
         *    ALEX
         */
        AbstractClientPlayerPatch playerPatch =
                (AbstractClientPlayerPatch)
                        clientEngine.getPlayerPatch();

        if (playerPatch == null) {
            return;
        }

        /*
         * PPlayerRenderer.getMeshProvider(...)
         * devuelve AssetAccessor<HumanoidMesh>.
         *
         * AssetAccessor.get() nos da la instancia real del mesh
         * que Epic Fight va a renderizar.
         */
        SkinnedMesh mesh =
                (SkinnedMesh)
                        renderer
                                .getMeshProvider(playerPatch)
                                .get();

        if (mesh == null) {
            return;
        }

        /*
         * Reset del mesh ANTES de que Epic Fight haga su render.
         * Los bridges individuales también pueden hacerlo, pero
         * aquí ocurre en el punto realmente útil del pipeline.
         */
        McaMeshVisibility.restore(mesh);

        PatchedLayer skinBridge =
                SKIN_BRIDGES.computeIfAbsent(
                        mesh,
                        McaSkinPatchedLayer::new
                );

        PatchedLayer clothingBridge =
                CLOTHING_BRIDGES.computeIfAbsent(
                        mesh,
                        McaClothingPatchedLayer::new
                );

        PatchedLayer hairBridge =
                HAIR_BRIDGES.computeIfAbsent(
                        mesh,
                        McaHairPatchedLayer::new
                );

        PatchedLayer faceBridge =
                FACE_BRIDGES.computeIfAbsent(
                        mesh,
                        McaFacePatchedLayer::new
                );

        /*
         * ====================================================
         * 0.0.19
         * ====================================================
         *
         * Skin:
         *   MCA texture/color
         *         ↓
         *   Epic Fight SkinnedMesh
         *         ↓
         *   Epic Fight Armature + pose matrices
         *
         * Face/Clothing/Hair:
         *   todavía NOOP para no introducir geometría MCA
         *   tradicional durante esta prueba.
         */
        renderer.addPatchedLayerAlways(
                SkinLayer.class,
                skinBridge
        );

        renderer.addPatchedLayerAlways(
                FaceLayer.class,
                faceBridge
        );

        renderer.addPatchedLayerAlways(
                ClothingLayer.class,
                clothingBridge
        );

        renderer.addPatchedLayerAlways(
                HairLayer.class,
                hairBridge
        );

        if (!loggedBridge) {

            loggedBridge = true;

            System.out.println(
                    "[MCAEFCompat] 0.0.19 MCA Skin -> Epic Fight mesh"
                            + " renderer="
                            + renderer.getClass().getName()
                            + " mesh="
                            + mesh.getClass().getName()
                            + " mappings="
                            + mappings.size()
            );
        }
    }

    private static Map<Class<?>, PatchedLayer> captureOriginals(
            Map<Class<?>, PatchedLayer> mappings
    ) {

        Map<Class<?>, PatchedLayer> originals =
                new LinkedHashMap<>();

        capture(
                originals,
                mappings,
                SkinLayer.class
        );

        capture(
                originals,
                mappings,
                FaceLayer.class
        );

        capture(
                originals,
                mappings,
                ClothingLayer.class
        );

        capture(
                originals,
                mappings,
                HairLayer.class
        );

        return originals;
    }

    private static void capture(
            Map<Class<?>, PatchedLayer> originals,
            Map<Class<?>, PatchedLayer> mappings,
            Class<?> type
    ) {

        PatchedLayer layer = mappings.get(type);

        if (layer != null) {
            originals.put(type, layer);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreOriginals(
            PPlayerRenderer renderer,
            Map<Class<?>, PatchedLayer> originals
    ) {

        for (Map.Entry<Class<?>, PatchedLayer> entry :
                originals.entrySet()) {

            if (entry.getValue() == null) {
                continue;
            }

            renderer.addPatchedLayerAlways(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private static boolean isMcaScreen() {

        Minecraft mc =
                Minecraft.getInstance();

        if (mc.screen == null) {
            return false;
        }

        return mc.screen
                .getClass()
                .getName()
                .startsWith("forge.net.mca.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<Class<?>, PatchedLayer> getMappings(
            PPlayerRenderer renderer
    ) {

        try {

            if (patchedLayersField == null) {

                Class<?> type =
                        renderer.getClass();

                while (type != null) {

                    try {

                        patchedLayersField =
                                type.getDeclaredField(
                                        "patchedLayers"
                                );

                        patchedLayersField.setAccessible(true);
                        break;

                    } catch (NoSuchFieldException ignored) {

                        type = type.getSuperclass();
                    }
                }
            }

            if (patchedLayersField == null) {

                System.out.println(
                        "[MCAEFCompat] ERROR: "
                                + "patchedLayers field not found"
                );

                return null;
            }

            return (Map<Class<?>, PatchedLayer>)
                    patchedLayersField.get(renderer);

        } catch (ReflectiveOperationException exception) {

            exception.printStackTrace();
            return null;
        }
    }
}

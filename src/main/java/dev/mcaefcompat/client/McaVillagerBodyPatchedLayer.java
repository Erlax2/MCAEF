package dev.mcaefcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import forge.net.mca.client.model.VillagerEntityModelMCA;
import forge.net.mca.client.render.VillagerEntityMCARenderer;
import forge.net.mca.client.render.layer.SkinLayer;
import forge.net.mca.client.render.layer.ClothingLayer;
import forge.net.mca.entity.VillagerEntityMCA;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.IdentityHashMap;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class McaVillagerBodyPatchedLayer extends PatchedLayer {

    /*
     * Modelo MCA auxiliar.
     *
     * NO lo usamos para renderizar el villager completo.
     * Solamente necesitamos la geometría extra que EFMCA
     * pierde al convertir el villager a HumanoidMesh.
     */
    private final VillagerEntityModelMCA<VillagerEntityMCA> model;

    /*
     * Se usa únicamente para obtener la textura/color
     * que MCA habría utilizado para la piel.
     */
    private final SkinLayer skinLayer;

    /*
     * Modelo de ropa MCA.
     *
     * MCA usa una geometría separada (breastsWear /
     * breastplate) para que la ropa cubra correctamente
     * la geometría corporal adicional.
     */
    private final VillagerEntityModelMCA<VillagerEntityMCA>
            clothingModel;

    private final ClothingLayer clothingLayer;

    /*
     * Armature -> joint resuelto.
     *
     * Se cachea porque este método se ejecutará cada frame.
     */
    private final Map<Armature, Integer> jointIds =
            new IdentityHashMap<>();

    public McaVillagerBodyPatchedLayer(
            VillagerEntityMCARenderer originalRenderer
    ) {

        MeshDefinition meshDefinition =
                VillagerEntityModelMCA.bodyData(
                        CubeDeformation.NONE
                );

        ModelPart root =
                LayerDefinition.create(
                        meshDefinition,
                        64,
                        64
                ).bakeRoot();

        this.model =
                new VillagerEntityModelMCA<>(root);

        this.skinLayer =
                new SkinLayer(
                        originalRenderer,
                        model
                );

        /*
         * El renderer original de MCA construye ClothingLayer
         * con bodyData(new CubeDeformation(0.0625F)).
         *
         * Replicamos exactamente esa geometría para recuperar
         * el breastplate / breastsWear que EFMCA pierde.
         */
        MeshDefinition clothingMeshDefinition =
                VillagerEntityModelMCA.bodyData(
                        new CubeDeformation(0.0625F)
                );

        ModelPart clothingRoot =
                LayerDefinition.create(
                        clothingMeshDefinition,
                        64,
                        64
                ).bakeRoot();

        this.clothingModel =
                new VillagerEntityModelMCA<>(
                        clothingRoot
                );

        this.clothingLayer =
                new ClothingLayer(
                        originalRenderer,
                        clothingModel,
                        "normal"
                );
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

        if (!(entity instanceof VillagerEntityMCA villager)) {
            return;
        }

        /*
         * IMPORTANTE:
         *
         * NO llamamos setupAnim().
         *
         * La animación debe venir completamente del armature
         * de Epic Fight. Solo dejamos que MCA calcule:
         *
         * - género
         * - breastSize
         * - VillagerDimensions
         * - visibilidad
         * - posición local de la geometría
         */
        model.applyVillagerDimensions(
                villager,
                villager.isPassenger()
        );

        clothingModel.applyVillagerDimensions(
                villager,
                villager.isPassenger()
        );

        ModelPart bodyShape =
                model.getBreastPart();

        if (bodyShape == null || !bodyShape.visible) {
            return;
        }

        /*
         * Esta es exactamente la variable que MCA usa
         * dentro de renderCommon():
         *
         * breastSize * dimensions.getBreasts()
         */
        float size =
                model.getBreastSize()
                        * model.getDimensions().getBreasts();

        if (size <= 0.0F) {
            return;
        }

        ResourceLocation texture =
                skinLayer.getSkin(villager);

        if (texture == null || !skinLayer.canUse(texture)) {
            return;
        }

        float[] color =
                skinLayer.getColor(
                        villager,
                        partialTicks
                );

        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;

        if (color != null && color.length >= 3) {
            r = color[0];
            g = color[1];
            b = color[2];
        }

        Armature armature =
                entityPatch.getArmature();

        int jointId =
                resolveBodyJoint(armature);

        if (jointId < 0
                || poses == null
                || jointId >= poses.length
                || poses[jointId] == null) {
            return;
        }

        poseStack.pushPose();

        /*
         * Pasamos al espacio local del torso animado
         * por Epic Fight.
         */
        MathUtils.mulStack(
                poseStack,
                poses[jointId]
        );

        /*
         * PHumanoidRenderer usa 0.75F como corrección vertical
         * para las capas de modelo vanilla asociadas a Root.
         *
         * Esto nos devuelve al mismo espacio en el que MCA
         * espera renderizar sus ModelPart.
         */
        poseStack.translate(
                0.0D,
                0.75D,
                0.0D
        );

        /*
         * Misma conversión de coordenadas utilizada por
         * RenderOriginalModelLayer.
         */
        poseStack.scale(
                -1.0F,
                -1.0F,
                1.0F
        );

        /*
         * Escala EXACTA de MCA renderCommon().
         */
        poseStack.scale(
                1.05F + size * 0.20F,
                0.75F + size * 0.75F,
                0.75F + size * 0.45F
        );

        VertexConsumer consumer =
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(
                                texture
                        )
                );

        bodyShape.render(
                poseStack,
                consumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                r,
                g,
                b,
                1.0F
        );

        /*
         * 0.0.23:
         *
         * La geometría de piel ya está renderizada.
         * Ahora colocamos encima el breastplate de MCA con
         * la textura real de ClothingLayer.
         *
         * Ya estamos dentro de:
         *
         * Root -> +0.75 -> conversión vanilla -> escala MCA
         *
         * por lo que NO repetimos ninguna transformación.
         */
        /*
         * 0.0.24:
         *
         * CommonVillagerModel.renderCommon() NO renderiza
         * solamente breastsWear.
         *
         * MCA itera getBreastParts(), que para
         * VillagerEntityModelMCA contiene:
         *
         * - breasts
         * - breastsWear
         *
         * ClothingLayer debe cubrir ambas geometrías para
         * reproducir fielmente el renderer original de MCA.
         */
        ResourceLocation clothingTexture =
                clothingLayer.getSkin(villager);

        if (clothingTexture != null
                && clothingLayer.canUse(
                        clothingTexture
                )) {

            float[] clothingColor =
                    clothingLayer.getColor(
                            villager,
                            partialTicks
                    );

            float cr = 1.0F;
            float cg = 1.0F;
            float cb = 1.0F;

            if (clothingColor != null
                    && clothingColor.length >= 3) {

                cr = clothingColor[0];
                cg = clothingColor[1];
                cb = clothingColor[2];
            }

            VertexConsumer clothingConsumer =
                    buffer.getBuffer(
                            RenderType.entityCutoutNoCull(
                                    clothingTexture
                            )
                    );

            /*
             * Exactamente la colección que MCA utiliza
             * en renderCommon().
             */
            for (ModelPart clothingShape
                    : clothingModel.getBreastParts()) {

                if (clothingShape == null
                        || !clothingShape.visible) {
                    continue;
                }

                clothingShape.render(
                        poseStack,
                        clothingConsumer,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        cr,
                        cg,
                        cb,
                        1.0F
                );
            }
        }

        poseStack.popPose();
    }

    private int resolveBodyJoint(
            Armature armature
    ) {

        Integer cached =
                jointIds.get(armature);

        if (cached != null) {
            return cached;
        }

        /*
         * 0.0.22:
         *
         * La geometría extra de MCA está definida directamente
         * respecto al root del modelo vanilla.
         *
         * Por tanto NO debemos tratarla como una pieza local
         * de Chest/Torso.
         */
        String[] candidates = {
                "Root"
        };

        for (String name : candidates) {

            try {

                Joint joint =
                        armature.searchJointByName(name);

                if (joint == null) {
                    continue;
                }

                int id =
                        joint.getId();

                jointIds.put(
                        armature,
                        id
                );

                System.out.println(
                        "[MCAEFCompat] 0.0.24 villager body joint="
                                + name
                                + " id="
                                + id
                                + " armature="
                                + armature.getClass().getName()
                );

                return id;

            } catch (RuntimeException ignored) {
            }
        }

        jointIds.put(
                armature,
                -1
        );

        System.out.println(
                "[MCAEFCompat] 0.0.24 WARNING:"
                        + " no Root joint for "
                        + armature.getClass().getName()
        );

        return -1;
    }
}

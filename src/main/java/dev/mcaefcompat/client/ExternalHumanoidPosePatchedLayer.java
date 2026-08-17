package dev.mcaefcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * YDM / Curios -> Epic Fight pose bridge.
 *
 * Important:
 * Epic Fight joint positions are NOT vanilla ModelPart pivots.
 *
 * A Joint's getToOrigin() is the inverse bind/model transform. Therefore:
 *
 *     bindModel = inverse(toOrigin)
 *
 * We compare the animated joint against its bind position and apply only that
 * movement DELTA to the existing vanilla pivot. This keeps vanilla's canonical
 * pivots (head 0/0/0, arms around +/-5/2/0, legs around +/-2/12/0, etc.)
 * instead of replacing them with Epic Fight skeleton coordinates.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ExternalHumanoidPosePatchedLayer extends PatchedLayer {

    private static boolean loggedPoseSync = false;
    private static boolean loggedDelta = false;

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
        if (!(originalLayer.getParentModel() instanceof HumanoidModel humanoid)) {
            return;
        }

        Armature armature = entityPatch.getArmature();

        if (armature == null || poses == null || poses.length == 0) {
            return;
        }

        Joint rootJoint = armature.searchJointByName("Root");

        if (rootJoint == null) {
            return;
        }

        Matrix4f currentRoot = matrixOf(poses, rootJoint.getId());
        Matrix4f bindRoot = bindMatrixOf(rootJoint);

        if (currentRoot == null || bindRoot == null) {
            return;
        }

        Matrix4f currentRootInverse = new Matrix4f(currentRoot).invert();
        Matrix4f bindRootInverse = new Matrix4f(bindRoot).invert();

        ModelState state = ModelState.capture(humanoid);

        boolean curiosLayer =
                "top.theillusivec4.curios.client.render.CuriosLayer"
                        .equals(originalLayer.getClass().getName());

        try {
            PoseDelta debug = syncModel(
                    humanoid,
                    armature,
                    poses,
                    currentRootInverse,
                    bindRootInverse,
                    curiosLayer
            );

            if (!loggedDelta && debug != null) {
                loggedDelta = true;

                System.out.println(
                        "[MCAEFCompat] YDM & Curios Compat: bind-relative pivot delta(px) "
                                + "head=" + debug.head
                                + " body=" + debug.body
                                + " rArm=" + debug.rightArm
                                + " lArm=" + debug.leftArm
                                + " rLeg=" + debug.rightLeg
                                + " lLeg=" + debug.leftLeg
                );
            }

            int rootId = rootJoint.getId();

            if (rootId < 0 || rootId >= poses.length || poses[rootId] == null) {
                return;
            }

            poseStack.pushPose();

            try {
                MathUtils.mulStack(poseStack, poses[rootId]);

                /*
                 * Same conversion used by Epic Fight when returning to a
                 * vanilla RenderLayer coordinate space.
                 */
                poseStack.translate(0.0D, 0.75D, 0.0D);
                poseStack.scale(-1.0F, -1.0F, 1.0F);

                originalLayer.render(
                        poseStack,
                        buffer,
                        packedLight,
                        entity,
                        entity.walkAnimation.position(),
                        entity.walkAnimation.speed(),
                        partialTicks,
                        bob,
                        netHeadYaw,
                        ageInTicks
                );
            } finally {
                poseStack.popPose();
            }

            if (!loggedPoseSync) {
                loggedPoseSync = true;

                System.out.println(
                        "[MCAEFCompat] YDM & Curios Compat: "
                                + "bind-relative pose bridge active"
                );
            }
        } finally {
            state.restore(humanoid);
        }
    }

    private static PoseDelta syncModel(
            HumanoidModel model,
            Armature armature,
            OpenMatrix4f[] poses,
            Matrix4f currentRootInverse,
            Matrix4f bindRootInverse,
            boolean curiosLayer
    ) {
        Vector3f head = syncPart(
                model.head, armature, poses,
                currentRootInverse, bindRootInverse, curiosLayer,
                "Head"
        );

        Vector3f body = syncPart(
                model.body, armature, poses,
                currentRootInverse, bindRootInverse, curiosLayer,
                "Torso", "Chest"
        );

        Vector3f rightArm = syncPart(
                model.rightArm, armature, poses,
                currentRootInverse, bindRootInverse, curiosLayer,
                "Arm_R", "Shoulder_R"
        );

        Vector3f leftArm = syncPart(
                model.leftArm, armature, poses,
                currentRootInverse, bindRootInverse, curiosLayer,
                "Arm_L", "Shoulder_L"
        );

        Vector3f rightLeg = syncPart(
                model.rightLeg, armature, poses,
                currentRootInverse, bindRootInverse, curiosLayer,
                "Leg_R", "Thigh_R"
        );

        Vector3f leftLeg = syncPart(
                model.leftLeg, armature, poses,
                currentRootInverse, bindRootInverse, curiosLayer,
                "Leg_L", "Thigh_L"
        );

        if (model instanceof PlayerModel playerModel) {
            copyPose(model.head, playerModel.hat);
            copyPose(model.body, playerModel.jacket);

            copyPose(model.rightArm, playerModel.rightSleeve);
            copyPose(model.leftArm, playerModel.leftSleeve);

            copyPose(model.rightLeg, playerModel.rightPants);
            copyPose(model.leftLeg, playerModel.leftPants);
        }

        return new PoseDelta(
                head,
                body,
                rightArm,
                leftArm,
                rightLeg,
                leftLeg
        );
    }

    private static Vector3f syncPart(
            ModelPart part,
            Armature armature,
            OpenMatrix4f[] poses,
            Matrix4f currentRootInverse,
            Matrix4f bindRootInverse,
            boolean curiosLayer,
            String... jointNames
    ) {
        Joint joint = findJoint(armature, jointNames);

        if (joint == null) {
            return new Vector3f();
        }

        Matrix4f currentJoint = matrixOf(poses, joint.getId());
        Matrix4f bindJoint = bindMatrixOf(joint);

        if (currentJoint == null || bindJoint == null) {
            return new Vector3f();
        }

        Matrix4f currentRelative =
                new Matrix4f(currentRootInverse).mul(currentJoint);

        Matrix4f bindRelative =
                new Matrix4f(bindRootInverse).mul(bindJoint);

        /*
         * Rotation:
         * keep the build-4 behavior that already fixed the reversed head
         * pitch. We use the current root-relative orientation and convert
         * Epic Fight's X/Y handedness to vanilla ModelPart axes.
         */
        /*
         * Rotation must be bind-relative too.
         *
         * poses[] contiene el transform jerárquico final del joint sin aplicar
         * toOrigin. Un ModelPart vanilla con rotación 0 ya representa su pose
         * base; por eso no debemos copiar la orientación absoluta de Epic Fight
         * durante animaciones fuertes.
         *
         * deltaRotation = currentRotation * inverse(bindRotation)
         */
        Quaternionf currentRotation =
                currentRelative
                        .getUnnormalizedRotation(new Quaternionf())
                        .normalize();

        Quaternionf bindRotation =
                bindRelative
                        .getUnnormalizedRotation(new Quaternionf())
                        .normalize();

        Quaternionf deltaRotation =
                new Quaternionf(currentRotation)
                        .mul(new Quaternionf(bindRotation).invert())
                        .normalize();

        /*
         * Curios copia estas rotaciones a modelos HumanoidModel propios.
         * Para rotaciones compuestas probamos la misma orientación usando
         * descomposición ZYX SOLO en Curios. YDM queda sin tocar.
         */
        Vector3f euler =
                curiosLayer
                        ? deltaRotation.getEulerAnglesZYX(new Vector3f())
                        : deltaRotation.getEulerAnglesXYZ(new Vector3f());

        /*
         * Conversión de handedness Epic Fight -> vanilla.
         * Conserva el fix del pitch de cabeza de build 4.
         */
        part.xRot = -euler.x;
        part.yRot = -euler.y;
        part.zRot = euler.z;

        /*
         * Position:
         * do NOT replace the vanilla pivot with Epic Fight's joint position.
         *
         * Only apply animated displacement from bind/rest pose:
         *
         *     delta = currentRelativePos - bindRelativePos
         *
         * Epic Fight matrices use model units, while ModelPart pivots are
         * pixels (1 block/model unit = 16 model pixels).
         */
        Vector3f currentPos =
                currentRelative.getTranslation(new Vector3f());

        Vector3f bindPos =
                bindRelative.getTranslation(new Vector3f());

        Vector3f delta =
                currentPos.sub(bindPos);

        float dx = -delta.x * 16.0F;
        float dy = -delta.y * 16.0F;
        float dz =  delta.z * 16.0F;

        part.x += dx;
        part.y += dy;
        part.z += dz;

        return new Vector3f(dx, dy, dz);
    }

    private static Joint findJoint(
            Armature armature,
            String... names
    ) {
        for (String name : names) {
            Joint joint = armature.searchJointByName(name);

            if (joint != null) {
                return joint;
            }
        }

        return null;
    }

    private static Matrix4f bindMatrixOf(Joint joint) {
        /*
         * Joint.initOriginTransform():
         *
         *   modelTransform = parent * local
         *   toOrigin = inverse(modelTransform)
         *
         * therefore inverse(toOrigin) is the bind/model transform.
         */
        OpenMatrix4f bind =
                new OpenMatrix4f(joint.getToOrigin()).invert();

        return matrixOf(bind);
    }

    private static Matrix4f matrixOf(
            OpenMatrix4f matrix
    ) {
        if (matrix == null) {
            return null;
        }

        PoseStack scratch = new PoseStack();

        MathUtils.mulStack(
                scratch,
                matrix
        );

        return new Matrix4f(
                scratch.last().pose()
        );
    }

    private static Matrix4f matrixOf(
            OpenMatrix4f[] poses,
            int id
    ) {
        if (id < 0 || id >= poses.length || poses[id] == null) {
            return null;
        }

        return matrixOf(poses[id]);
    }

    private static void copyPose(
            ModelPart from,
            ModelPart to
    ) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;

        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
    }

    private record PoseDelta(
            Vector3f head,
            Vector3f body,
            Vector3f rightArm,
            Vector3f leftArm,
            Vector3f rightLeg,
            Vector3f leftLeg
    ) {
    }

    private record PartState(
            float x,
            float y,
            float z,
            float xRot,
            float yRot,
            float zRot
    ) {
        static PartState capture(ModelPart part) {
            return new PartState(
                    part.x,
                    part.y,
                    part.z,
                    part.xRot,
                    part.yRot,
                    part.zRot
            );
        }

        void restore(ModelPart part) {
            part.x = x;
            part.y = y;
            part.z = z;

            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
        }
    }

    private record ModelState(
            PartState head,
            PartState body,
            PartState rightArm,
            PartState leftArm,
            PartState rightLeg,
            PartState leftLeg,
            PartState hat,
            PartState jacket,
            PartState rightSleeve,
            PartState leftSleeve,
            PartState rightPants,
            PartState leftPants
    ) {
        static ModelState capture(HumanoidModel model) {
            if (model instanceof PlayerModel playerModel) {
                return new ModelState(
                        PartState.capture(model.head),
                        PartState.capture(model.body),
                        PartState.capture(model.rightArm),
                        PartState.capture(model.leftArm),
                        PartState.capture(model.rightLeg),
                        PartState.capture(model.leftLeg),
                        PartState.capture(playerModel.hat),
                        PartState.capture(playerModel.jacket),
                        PartState.capture(playerModel.rightSleeve),
                        PartState.capture(playerModel.leftSleeve),
                        PartState.capture(playerModel.rightPants),
                        PartState.capture(playerModel.leftPants)
                );
            }

            return new ModelState(
                    PartState.capture(model.head),
                    PartState.capture(model.body),
                    PartState.capture(model.rightArm),
                    PartState.capture(model.leftArm),
                    PartState.capture(model.rightLeg),
                    PartState.capture(model.leftLeg),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        void restore(HumanoidModel model) {
            head.restore(model.head);
            body.restore(model.body);

            rightArm.restore(model.rightArm);
            leftArm.restore(model.leftArm);

            rightLeg.restore(model.rightLeg);
            leftLeg.restore(model.leftLeg);

            if (model instanceof PlayerModel playerModel) {
                if (hat != null) hat.restore(playerModel.hat);
                if (jacket != null) jacket.restore(playerModel.jacket);
                if (rightSleeve != null) rightSleeve.restore(playerModel.rightSleeve);
                if (leftSleeve != null) leftSleeve.restore(playerModel.leftSleeve);
                if (rightPants != null) rightPants.restore(playerModel.rightPants);
                if (leftPants != null) leftPants.restore(playerModel.leftPants);
            }
        }
    }
}

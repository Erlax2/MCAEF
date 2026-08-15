package dev.mcaefcompat.client;

import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.mesh.HumanoidMesh;

public final class McaMeshVisibility {

    private McaMeshVisibility() {
    }

    public static void restore(SkinnedMesh mesh) {

        if (!(mesh instanceof HumanoidMesh humanoid)) {
            return;
        }

        /*
         * El HumanoidMesh de Epic Fight es reutilizado.
         *
         * MCA Editor / layers MCA pueden dejar estados de
         * visibilidad que terminan copiándose o persistiendo
         * al volver al mundo.
         *
         * Nuestro bridge necesita partir siempre de un humanoide
         * completamente visible.
         */
        humanoid.head.setHidden(false);
        humanoid.hat.setHidden(false);

        humanoid.torso.setHidden(false);
        humanoid.jacket.setHidden(false);

        humanoid.leftArm.setHidden(false);
        humanoid.leftSleeve.setHidden(false);

        humanoid.rightArm.setHidden(false);
        humanoid.rightSleeve.setHidden(false);

        humanoid.leftLeg.setHidden(false);
        humanoid.leftPants.setHidden(false);

        humanoid.rightLeg.setHidden(false);
        humanoid.rightPants.setHidden(false);
    }
}

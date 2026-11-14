package dev.adam.adamcamera;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The detached camera. It is a client-only fake player that the game uses as the
 * "camera entity", so vanilla handles rendering and mouse-look for us.
 * It has its own keyboard input, because the real player's input is switched off
 * while Adam Camera is active.
 */
public class AdamCamera extends AbstractClientPlayer {

    /** Blocks per tick (x20 for blocks per second). */
    public static final double NORMAL_SPEED = 0.5;
    /** Speed while Shift is held. */
    public static final double FAST_SPEED = 2.0;

    // Negative id so it can never clash with a real entity id from the server.
    private static final int CAMERA_ENTITY_ID = -8080;

    private final KeyboardInput keyboardInput;

    public AdamCamera(ClientLevel level) {
        super(level, new GameProfile(UUID.randomUUID(), "AdamCamera"));
        setId(CAMERA_ENTITY_ID);
        setPose(Pose.SWIMMING);
        setNoGravity(true);
        getAbilities().flying = true;
        keyboardInput = new KeyboardInput(Minecraft.getInstance().options);
    }

    public void spawn() {
        ((ClientLevel) level()).addEntity(this);
    }

    public void despawn() {
        ((ClientLevel) level()).removeEntity(getId(), Entity.RemovalReason.DISCARDED);
    }

    @Override
    public void tick() {
        keyboardInput.tick();
        applyMotion();
        super.tick();
    }

    /** WASD = move, Space = up, Ctrl = down, Shift = fast. */
    private void applyMotion() {
        var keys = keyboardInput.keyPresses;
        Minecraft mc = Minecraft.getInstance();

        double speed = keys.shift() ? FAST_SPEED : NORMAL_SPEED;

        Vec3 forward = Vec3.directionFromRotation(0, getYRot());
        Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90);

        double x = 0;
        double z = 0;
        if (keys.forward()) { x += forward.x; z += forward.z; }
        if (keys.backward()) { x -= forward.x; z -= forward.z; }
        if (keys.right()) { x += side.x; z += side.z; }
        if (keys.left()) { x -= side.x; z -= side.z; }

        // Keep diagonal movement the same speed as straight movement.
        double length = Math.sqrt(x * x + z * z);
        if (length > 1.0E-6) {
            x = x / length * speed;
            z = z / length * speed;
        }

        double y = 0;
        if (keys.jump()) y += speed;
        if (mc.options.keySprint.isDown()) y -= speed;

        setDeltaMovement(x, y, z);
    }

    // Fly through blocks. Player.tick() resets noPhysics every tick, so set it right before moving.
    @Override
    public void move(MoverType type, Vec3 movement) {
        this.noPhysics = true;
        super.move(type, movement);
    }

    // Always stay in the small swimming pose so the camera isn't a tall standing hitbox.
    @Override
    public void setPose(Pose pose) {
        super.setPose(Pose.SWIMMING);
    }

    // No water/ladder slowdown.
    @Override
    public boolean isInWater() {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    // Use the exact current rotation (no tick interpolation) so mouse-look feels instant.
    @Override
    public float getViewXRot(float partialTick) {
        return getXRot();
    }

    @Override
    public float getViewYRot(float partialTick) {
        return getYRot();
    }

    // Let this client-side entity simulate its own movement.
    @Override
    public boolean isEffectiveAi() {
        return true;
    }

    // Minecraft 1.21.11 to 26.2. On 26.3+ replace this with:
    //   @Override public MoveSimulationType getMoveSimulationType() { return MoveSimulationType.SERVER_AND_CLIENT; }
    @Override
    public boolean canSimulateMovement() {
        return true;
    }
}

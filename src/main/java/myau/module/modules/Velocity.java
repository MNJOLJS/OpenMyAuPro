package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.util.PlayerUtil;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S19PacketEntityStatus;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double FALL_DAMAGE_DISTANCE = 3.0;
    private static final long FALL_DAMAGE_GRACE_NANOS = 750_000_000L;

    public final FloatProperty minJumpTime = new FloatProperty("min-jump-time", 80.0F, 0.0F, 250.0F);
    public final FloatProperty maxJumpTime = new FloatProperty("max-jump-time", 180.0F, 0.0F, 250.0F);
    public final PercentProperty chance = new PercentProperty("chance", 85);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    private volatile long pendingJumpAt;
    private volatile long pendingJumpUntil;
    private boolean trackingFall;
    private double highestFallY;
    private long fallDamageSuppressUntil;
    
    public Velocity() {
        super("Velocity", false);
    }
    
    @Override
    public void onDisabled() {
        clearPendingJump();
        resetFallTracking();
    }

    @Override
    public void onEnabled() {
        resetFallTracking();
        if (mc.thePlayer != null) {
            trackingFall = !mc.thePlayer.onGround;
            highestFallY = mc.thePlayer.posY;
        }
    }

    @Override
    public void verifyValue(String name) {
        if (minJumpTime.getName().equals(name) && minJumpTime.getValue() > maxJumpTime.getValue()) {
            maxJumpTime.setValue(minJumpTime.getValue());
        } else if (maxJumpTime.getName().equals(name) && minJumpTime.getValue() > maxJumpTime.getValue()) {
            minJumpTime.setValue(maxJumpTime.getValue());
        }
    }
    
    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE
                || !(event.getPacket() instanceof S19PacketEntityStatus)) {
            return;
        }

        mc.addScheduledTask(() -> handleHurtPacket(event));
    }

    private void handleHurtPacket(PacketEvent event) {
        if (!isEnabled() || event.isCancelled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
        updateFallTracking();
        if (packet.getOpCode() != 2 || packet.getEntity(mc.theWorld) != mc.thePlayer
                || !mc.thePlayer.onGround || mc.thePlayer.isSneaking() || PlayerUtil.isSneaking()
            || isFallDamageSuppressed() || !hasNearbyThreat()
                || RandomUtil.nextInt(1, 100) > chance.getValue()) {
            return;
        }
        
        float minDelay = Math.min(minJumpTime.getValue(), maxJumpTime.getValue());
        float maxDelay = Math.max(minJumpTime.getValue(), maxJumpTime.getValue());
        float jumpTime = RandomUtil.nextFloat(minDelay, maxDelay);
        long now = System.nanoTime();
        pendingJumpUntil = now + (long) ((jumpTime + 100.0F) * 1_000_000.0F);
        pendingJumpAt = now + (long) (jumpTime * 1_000_000.0F);
    }
    
    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null) {
            clearPendingJump();
            resetFallTracking();
            return;
        }

        updateFallTracking();
        if (pendingJumpAt == 0L) {
            return;
        }
        
        long now = System.nanoTime();
        if (now < pendingJumpAt) {
            return;
        }
        if (now >= pendingJumpUntil || mc.thePlayer == null || mc.theWorld == null) {
            clearPendingJump();
            return;
        }
        if (mc.thePlayer.isSneaking() || PlayerUtil.isSneaking()) {
            clearPendingJump();
            return;
        }
        if (isFallDamageSuppressed()) {
            clearPendingJump();
            return;
        }

    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || pendingJumpAt == 0L) {
            return;
        }

        long now = System.nanoTime();
        if (now < pendingJumpAt) {
            return;
        }
        if (now >= pendingJumpUntil || mc.thePlayer.isSneaking() || PlayerUtil.isSneaking()
                || isFallDamageSuppressed()) {
            clearPendingJump();
            return;
        }
        if (mc.thePlayer.movementInput.jump || PlayerUtil.isJumping()) {
            clearPendingJump();
            return;
        }
        if (!mc.thePlayer.onGround || mc.thePlayer.hurtTime <= 0
                || (mc.thePlayer.movementInput.moveForward == 0.0F
                && mc.thePlayer.movementInput.moveStrafe == 0.0F)
                || !hasNearbyThreat()) {
            return;
        }

        if (!mc.thePlayer.movementInput.jump) {
            mc.thePlayer.movementInput.jump = true;
        }
        clearPendingJump();
    }

    private void clearPendingJump() {
        pendingJumpAt = 0L;
        pendingJumpUntil = 0L;
    }

    private void updateFallTracking() {
        long now = System.nanoTime();
        if (!mc.thePlayer.onGround) {
            if (!trackingFall) {
                trackingFall = true;
                highestFallY = mc.thePlayer.posY;
            } else {
                highestFallY = Math.max(highestFallY, mc.thePlayer.posY);
            }

            if (highestFallY - mc.thePlayer.posY > FALL_DAMAGE_DISTANCE
                    || mc.thePlayer.fallDistance > FALL_DAMAGE_DISTANCE) {
                fallDamageSuppressUntil = now + FALL_DAMAGE_GRACE_NANOS;
            }
        } else {
            if (trackingFall && highestFallY - mc.thePlayer.posY > FALL_DAMAGE_DISTANCE) {
                fallDamageSuppressUntil = now + FALL_DAMAGE_GRACE_NANOS;
            }
            trackingFall = false;
            highestFallY = mc.thePlayer.posY;
        }

        if (mc.thePlayer.fallDistance > FALL_DAMAGE_DISTANCE) {
            fallDamageSuppressUntil = now + FALL_DAMAGE_GRACE_NANOS;
        }
    }

    private boolean isFallDamageSuppressed() {
        return mc.thePlayer.fallDistance > FALL_DAMAGE_DISTANCE
                || System.nanoTime() < fallDamageSuppressUntil;
    }

    private void resetFallTracking() {
        trackingFall = false;
        highestFallY = 0.0;
        fallDamageSuppressUntil = 0L;
    }

    private boolean hasNearbyThreat() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }

        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity == mc.thePlayer || entity.isDead || entity.getDistanceToEntity(mc.thePlayer) > 5.0F
                    || RotationUtil.angleToEntity(entity) > fov.getValue().floatValue()) {
                continue;
            }

            if (entity instanceof EntityPlayer) {
                if (mc.getNetHandler() == null) {
                    continue;
                }
                EntityPlayer player = (EntityPlayer) entity;
                if (!TeamUtil.isFriend(player) && !TeamUtil.isSameTeam(player) && !TeamUtil.isBot(player)) {
                    return true;
                }
            } else if (entity instanceof IMob || entity instanceof EntityDragon) {
                return true;
            }
        }
        return false;
    }
    
    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        clearPendingJump();
        resetFallTracking();
    }
}

package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private EntityLivingBase target;
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 10.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 10.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty bosses = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty golems = new BooleanProperty("golems", false);
    public final BooleanProperty silverfish = new BooleanProperty("silverfish", false);

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity != mc.thePlayer && entity != mc.thePlayer.ridingEntity) {
            if (entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entity.deathTime > 0) {
                return false;
            } else if (RotationUtil.distanceToEntity(entity) > (double) this.range.getValue()) {
                return false;
            } else if (RotationUtil.angleToEntity(entity) > (float) this.fov.getValue()) {
                return false;
            } else if (!RotationUtil.hasVisiblePoint(entity.getEntityBoundingBox().expand(
                    entity.getCollisionBorderSize(),
                    entity.getCollisionBorderSize(),
                    entity.getCollisionBorderSize()
            ))) {
                return false;
            } else if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                return this.players.getValue()
                        && !TeamUtil.isFriend(player)
                        && (!this.team.getValue() || !TeamUtil.isSameTeam(player))
                        && (!this.botChecks.getValue() || !TeamUtil.isBot(player));
            } else if (entity instanceof EntityDragon || entity instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (entity instanceof EntitySilverfish) {
                return this.silverfish.getValue() && (!this.team.getValue() || !TeamUtil.hasTeamColor(entity));
            } else if (entity instanceof EntityMob || entity instanceof EntitySlime) {
                return this.mobs.getValue();
            } else if (entity instanceof EntityAnimal || entity instanceof EntityBat
                    || entity instanceof EntitySquid || entity instanceof EntityVillager) {
                return this.animals.getValue();
            } else if (entity instanceof EntityIronGolem) {
                return this.golems.getValue() && (!this.team.getValue() || !TeamUtil.hasTeamColor(entity));
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isInReach(EntityLivingBase entity) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entity) <= distance;
    }

    private float[] getTargetRotations(AxisAlignedBB boundingBox) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY ? maxTargetY - eyePos.yCoord
                : eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0;
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return new float[]{yaw, pitch};
    }

    public AimAssist() {
        super("AimAssist", false);
    }

    @Override
    public void onDisabled() {
        target = null;
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST && mc.currentScreen == null) {
            if (!(Boolean) this.weaponOnly.getValue()
                    || ItemUtil.hasRawUnbreakingEnchant()
                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
                boolean attacking = PlayerUtil.isAttacking();
                    if (attacking || !this.timer.hasTimeElapsed(350L)) {
                        List<EntityLivingBase> inRange = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityLivingBase)
                                .map(entity -> (EntityLivingBase) entity)
                                .filter(this::isValidTarget)
                                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                                .collect(Collectors.toList());
                        if (!inRange.isEmpty()) {
                            if (inRange.stream().anyMatch(this::isInReach)) {
                                inRange.removeIf(entity -> !this.isInReach(entity));
                            }
                            if (inRange.isEmpty()) {
                                target = null;
                            } else {
                                if (target == null || !inRange.contains(target)) {
                                    target = inRange.get(0);
                                }
                            }
                            EntityLivingBase entity = target;
                            if (entity != null && !(RotationUtil.distanceToEntity(entity) <= 0.0)) {
                                AxisAlignedBB axisAlignedBB = entity.getEntityBoundingBox();
                                double collisionBorderSize = entity.getCollisionBorderSize();
                                float[] rotation = this.getTargetRotations(
                                        axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize)
                                );
                                float smoothingFactor = 1.0F - (float) this.smoothing.getValue() / 200.0F;
                                float maxYawStep = Math.min(Math.abs(this.hSpeed.getValue()), 10.0F) * 10.0F * smoothingFactor;
                                float maxPitchStep = Math.min(Math.abs(this.vSpeed.getValue()), 10.0F) * 10.0F * smoothingFactor;
                                float currentYaw = mc.thePlayer.rotationYaw;
                                float currentPitch = mc.thePlayer.rotationPitch;
                                float yawDelta = MathHelper.wrapAngleTo180_float(rotation[0] - currentYaw);
                                float pitchDelta = rotation[1] - currentPitch;
                                Myau.rotationManager
                                        .setRotation(
                                                currentYaw + MathHelper.clamp_float(yawDelta, -maxYawStep, maxYawStep),
                                                currentPitch + MathHelper.clamp_float(pitchDelta, -maxPitchStep, maxPitchStep),
                                                0,
                                                false
                                        );
                            } else if (entity == null) {
                                target = null;
                            }
                        } else {
                            target = null;
                        }
                    }
                    else {
                        target = null;
                    }
            } else {
                target = null;
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode() && !Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            this.timer.reset();
        }
    }
}

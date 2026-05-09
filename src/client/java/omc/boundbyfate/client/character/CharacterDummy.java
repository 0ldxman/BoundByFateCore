package omc.boundbyfate.client.character;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import omc.boundbyfate.client.skin.ClientSkinManager;
import omc.boundbyfate.data.world.character.ModelType;
import omc.boundbyfate.network.packet.s2c.DummyAnimationType;

import java.util.UUID;

/**
 * Клиентская entity — манекен персонажа.
 *
 * Отображается на месте персонажа когда игрок вышел из него или из игры.
 * Наследует {@link AbstractClientPlayerEntity} чтобы:
 * - Рендериться через стандартный PlayerEntityRenderer
 * - Поддерживать PlayerAnimator (он работает через Mixin на этот класс)
 * - Использовать кастомный скин через {@link ClientSkinManager}
 *
 * Существует только на клиенте — сервер о ней не знает.
 * Управляется {@link CharacterDummyManager}.
 */
@Environment(EnvType.CLIENT)
public class CharacterDummy extends AbstractClientPlayerEntity {

    /** UUID персонажа — ключ для despawn. */
    public final UUID characterId;

    /** ID скина из FileTransferSystem. */
    private final String skinId;

    /** Тип модели ("steve" или "alex"). */
    private final String modelType;

    /** Тип анимации отдыха. */
    public final DummyAnimationType animationType;

    /** Сущность-источник (например, игрок), чьё состояние мы копируем. */
    public net.minecraft.entity.LivingEntity sourceEntity;

    public CharacterDummy(
            ClientWorld world,
            UUID characterId,
            String skinId,
            String modelType,
            DummyAnimationType animationType
    ) {
        super(world, new GameProfile(characterId, ""));
        this.characterId = characterId;
        this.skinId = skinId;
        this.modelType = modelType;
        this.animationType = animationType;
    }

    /**
     * Копирует визуальное состояние из сущности-источника.
     */
    public void syncWithSource() {
        if (sourceEntity == null) return;

        // Копируем позицию и повороты
        this.setPos(sourceEntity.getX(), sourceEntity.getY(), sourceEntity.getZ());
        this.prevX = sourceEntity.prevX;
        this.prevY = sourceEntity.prevY;
        this.prevZ = sourceEntity.prevZ;

        this.setYaw(sourceEntity.getYaw());
        this.prevYaw = sourceEntity.prevYaw;
        this.setPitch(sourceEntity.getPitch());
        this.prevPitch = sourceEntity.prevPitch;

        this.headYaw = sourceEntity.headYaw;
        this.prevHeadYaw = sourceEntity.prevHeadYaw;
        this.bodyYaw = sourceEntity.bodyYaw;
        this.prevBodyYaw = sourceEntity.prevBodyYaw;

        // Копируем анимации конечностей (движение ног/рук)
        this.limbDistance = sourceEntity.limbDistance;
        this.prevLimbDistance = sourceEntity.prevLimbDistance;
        this.limbAnimator.setSpeed(sourceEntity.limbAnimator.getSpeed());

        // Состояние (крадётся, плывёт и т.д.)
        this.setSneaking(sourceEntity.isSneaking());
        this.setSprinting(sourceEntity.isSprinting());
        this.setSwimming(sourceEntity.isSwimming());

        // Копируем время жизни (важно для некоторых ванильных анимаций)
        // Но не затираем свой age полностью, если dummy используется для анимаций отдыха
        // this.age = sourceEntity.age; 
    }

    // ── Скин (API 1.20.1) ─────────────────────────────────────────────────

    /**
     * Возвращает текстуру скина.
     * В 1.20.1 PlayerEntityRenderer вызывает getSkinTexture() (без 's').
     */
    @Override
    public Identifier getSkinTexture() {
        if (!skinId.isEmpty()) {
            ModelType mt = "alex".equalsIgnoreCase(modelType) ? ModelType.ALEX : ModelType.STEVE;
            ClientSkinManager.INSTANCE.ensureLoaded(skinId, mt);
            Identifier custom = ClientSkinManager.INSTANCE.getTexture(skinId);
            if (custom != null) return custom;
        }
        return super.getSkinTexture();
    }

    /**
     * Возвращает тип модели ("slim" для Alex, "default" для Steve).
     * Используется PlayerEntityRenderer для выбора геометрии рук.
     */
    @Override
    public String getModel() {
        return "alex".equalsIgnoreCase(modelType) ? "slim" : "default";
    }

    // ── Заморозка — Dummy не двигается самостоятельно ────────────────────

    @Override
    public void tick() {
        // Мы НЕ вызываем super.tick(), чтобы избежать ванильной логики движения.
        // age увеличивается в CharacterDummyManager (Client Tick), а не здесь.
    }

    /**
     * Вызывается из клиентского тика для обновления состояния.
     */
    public void clientTick() {
        this.age++;
    }

    @Override
    public boolean isSpectator() { return false; }

    @Override
    public boolean isCreative() { return false; }

    @Override
    public boolean shouldRenderName() { return false; }

    @Override
    public boolean isInvisible() { return false; }

    @Override
    public boolean isInvisibleTo(net.minecraft.entity.player.PlayerEntity player) { return false; }

    // ── Физика отключена ──────────────────────────────────────────────────

    @Override
    public void updateVelocity(float speed, net.minecraft.util.math.Vec3d movementInput) {
        // Не двигаемся
    }
}

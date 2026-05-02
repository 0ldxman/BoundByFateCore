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

    // ── Заморозка — Dummy не двигается и не реагирует ────────────────────

    @Override
    public void tick() {
        // Не вызываем super.tick() — Dummy полностью статичен.
        // Только увеличиваем возраст для корректного рендера.
        age++;
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
    public void applyMovementInput(net.minecraft.util.math.Vec3d movementInput, float slipperiness) {
        // Не двигаемся
    }

    @Override
    public void updateVelocity(float speed, net.minecraft.util.math.Vec3d movementInput) {
        // Не двигаемся
    }
}

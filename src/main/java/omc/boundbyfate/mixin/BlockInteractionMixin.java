package omc.boundbyfate.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import omc.boundbyfate.api.proficiency.ProficiencyEntry;
import omc.boundbyfate.system.proficiency.ProficiencySystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockInteractionMixin {

    @Inject(
        method = "onUse",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbf_checkBlockProficiency(
        BlockState state,
        World world,
        BlockPos pos,
        PlayerEntity player,
        Hand hand,
        BlockHitResult hit,
        CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return;

        ProficiencyEntry blocked = ProficiencySystem.INSTANCE.getBlockedEntry(serverPlayer, state.getBlock());

        if (blocked != null) {
            serverPlayer.sendMessage(
                Text.literal("§cТребуется владение: " + blocked.getDisplayName()),
                true
            );
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}

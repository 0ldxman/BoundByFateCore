package omc.boundbyfate.client.render.entity

import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.texture.MissingSprite
import net.minecraft.util.Identifier
import net.minecraft.world.entity.Entity

/**
 * Пустой рендерер сущности — не рисует ничего стандартного.
 * Используется для НПС у которых модель рендерится через kool/GLTF систему,
 * перехватывая стандартный рендер через RenderEntityEvent.
 */
open class EmptyEntityRenderer<T : Entity>(
    context: EntityRendererFactory.Context
) : EntityRenderer<T>(context) {

    override fun getTexture(entity: T): Identifier = MissingSprite.getMissingSpriteId()
}

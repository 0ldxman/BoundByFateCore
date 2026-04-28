package omc.boundbyfate.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import omc.boundbyfate.client.util.ListOrSingle

@Serializable
data class GltfScene(
    val nodes: ListOrSingle<Int>,
    val name: String? = null
) {
    @Transient
    lateinit var nodeRefs: List<GltfNode>
}



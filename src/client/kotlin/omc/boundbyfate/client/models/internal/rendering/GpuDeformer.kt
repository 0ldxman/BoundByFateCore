package omc.boundbyfate.client.models.internal.rendering

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import omc.boundbyfate.client.models.gltf.GltfMesh
import omc.boundbyfate.client.models.internal.Primitive
import omc.boundbyfate.client.models.internal.SkinGetter
import omc.boundbyfate.client.models.internal.manager.HollowModelManager
import omc.boundbyfate.client.models.internal.utils.VboWrapper
import omc.boundbyfate.client.models.internal.utils.toFloatBuffer
import kotlin.math.min

class GpuDeformer(private val primitive: Primitive) {
    // Убрали processingVao - используем VAO из PipelineRenderer

    private var srcJointsBuffer: VboWrapper? = null
    private var srcWeightsBuffer: VboWrapper? = null

    private var morphPosBuffer: VboWrapper? = null
    private var morphPosTexture = -1
    private var morphNorBuffer: VboWrapper? = null
    private var morphNorTexture = -1
    private var morphTanBuffer: VboWrapper? = null
    private var morphTanTexture = -1

    private var jointMatrixBuffer: VboWrapper? = null
    private var jointMatrixTexture = -1

    private var outPosBufferId = -1
    private var outNorBufferId = -1
    private var outTanBufferId = -1

    private var drawCount = 0
    private var computeCallCount = 0
    private val logger = org.slf4j.LoggerFactory.getLogger(GpuDeformer::class.java)

    fun init(dstPos: Int, dstNor: Int, dstTan: Int) {
        this.outPosBufferId = dstPos
        this.outNorBufferId = dstNor
        this.outTanBufferId = dstTan
        this.drawCount = primitive.positions?.size ?: 0

        // Логируем первые несколько вершин для отладки
        primitive.joints?.let { joints ->
            logger.info("[GpuDeformer] First 3 vertex joints: v0=${joints[0]} v1=${joints[1]} v2=${joints[2]}")
        }
        primitive.jointWeights?.let { weights ->
            logger.info("[GpuDeformer] First 3 vertex weights: v0=${weights[0]} v1=${weights[1]} v2=${weights[2]}")
        }

        // Не создаём отдельный VAO - используем VAO из PipelineRenderer
        // Привязываем joints и weights к основному VAO в PipelineRenderer.initDynamicBuffers()

        if (primitive.morphTargets.isNotEmpty()) {
            initMorphTextures()
        }

        if (primitive.hasSkinning) {
            initJointMatrixTexture()
        }
    }

    private fun initMorphTextures() {
        val morphCount = primitive.morphTargets.size

        val totalElements = drawCount * morphCount

        val posBuffer = BufferUtils.createFloatBuffer(totalElements * 4)
        val norBuffer = BufferUtils.createFloatBuffer(totalElements * 4)
        val tanBuffer = BufferUtils.createFloatBuffer(totalElements * 4)

        for (targetMap in primitive.morphTargets) {
            val posDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_POSITION]
            val norDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_NORMAL]
            val tanDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_TANGENT]

            for (i in 0 until drawCount) {
                if (posDeltas != null) {
                    posBuffer.put(posDeltas[i * 3]).put(posDeltas[i * 3 + 1]).put(posDeltas[i * 3 + 2]).put(0f)
                } else {
                    posBuffer.put(0f).put(0f).put(0f).put(0f)
                }

                if (norDeltas != null) {
                    norBuffer.put(norDeltas[i * 3]).put(norDeltas[i * 3 + 1]).put(norDeltas[i * 3 + 2]).put(0f)
                } else {
                    norBuffer.put(0f).put(0f).put(0f).put(0f)
                }

                if (tanDeltas != null) {
                    tanBuffer.put(tanDeltas[i * 3]).put(tanDeltas[i * 3 + 1]).put(tanDeltas[i * 3 + 2]).put(0f)
                } else {
                    tanBuffer.put(0f).put(0f).put(0f).put(0f)
                }
            }
        }

        posBuffer.flip(); norBuffer.flip(); tanBuffer.flip()

        morphPosBuffer = VboWrapper.createTextureBuffer().apply {
            uploadData(posBuffer)
            morphPosTexture = createTextureFromBuffer(this.id)
        }

        morphNorBuffer = VboWrapper.createTextureBuffer().apply {
            uploadData(norBuffer)
            morphNorTexture = createTextureFromBuffer(this.id)
        }

        morphTanBuffer = VboWrapper.createTextureBuffer().apply {
            uploadData(tanBuffer)
            morphTanTexture = createTextureFromBuffer(this.id)
        }
    }

    private fun initJointMatrixTexture() {
        jointMatrixBuffer = VboWrapper.createTextureBuffer().apply {
            allocate(primitive.jointCount * 64L, GL33.GL_DYNAMIC_DRAW)
            jointMatrixTexture = createTextureFromBuffer(this.id)
        }
    }

    private fun createTextureFromBuffer(bufferId: Int): Int {
        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId)
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, bufferId)
        return textureId
    }

    fun compute(node: SkinGetter, renderVao: Int) {
        computeCallCount++
        val shouldLog = computeCallCount == 1 || computeCallCount % 60 == 0
        
        if (shouldLog) {
            logger.info("[GpuDeformer] Compute call #$computeCallCount, hasSkinning=${primitive.hasSkinning}")
        }
        
        val shaderId: Int

        if (primitive.hasSkinning) {
            shaderId = HollowModelManager.glProgramSkinning
            GL20.glUseProgram(shaderId)
            
            // Проверяем, что шейдер валиден
            if (shouldLog) {
                val linkStatus = GL20.glGetProgrami(shaderId, GL20.GL_LINK_STATUS)
                val validateStatus = GL20.glGetProgrami(shaderId, GL20.GL_VALIDATE_STATUS)
                logger.info("[GpuDeformer] Shader program $shaderId: linkStatus=$linkStatus, validateStatus=$validateStatus")
                
                // Проверяем, что Transform Feedback настроен
                val tfVaryings = GL30.glGetProgrami(shaderId, GL30.GL_TRANSFORM_FEEDBACK_VARYINGS)
                logger.info("[GpuDeformer] Transform Feedback varyings count: $tfVaryings")
            }

            updateJointMatrices(node)

            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, jointMatrixTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "jointMatrices"), 0)
        } else {
            shaderId = HollowModelManager.glProgramMorphing
            GL20.glUseProgram(shaderId)
        }

        setupMorphUniforms(shaderId)

        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, outPosBufferId)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, outNorBufferId)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, outTanBufferId)

        // Проверяем ошибки перед Transform Feedback
        if (shouldLog) {
            val error1 = GL11.glGetError()
            if (error1 != GL11.GL_NO_ERROR) {
                logger.error("[GpuDeformer] OpenGL error before TF: $error1")
            }
        }

        GL30.glBeginTransformFeedback(GL11.GL_POINTS)
        GL30.glBindVertexArray(renderVao)
        GL11.glDrawArrays(GL11.GL_POINTS, 0, drawCount)
        GL30.glEndTransformFeedback()

        // Проверяем ошибки после Transform Feedback
        if (shouldLog) {
            val error2 = GL11.glGetError()
            if (error2 != GL11.GL_NO_ERROR) {
                logger.error("[GpuDeformer] OpenGL error after TF: $error2")
            }
        }

        GL30.glBindVertexArray(0)
        
        // Проверяем что данные записались и меняются ли они
        if (shouldLog) {
            val testBuffer = org.lwjgl.BufferUtils.createFloatBuffer(9)
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, outPosBufferId)
            GL33.glGetBufferSubData(GL33.GL_ARRAY_BUFFER, 0, testBuffer)
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, 0)
            logger.info("[GpuDeformer] Frame $computeCallCount output positions: v0=(${testBuffer.get(0)},${testBuffer.get(1)},${testBuffer.get(2)}) v1=(${testBuffer.get(3)},${testBuffer.get(4)},${testBuffer.get(5)}) v2=(${testBuffer.get(6)},${testBuffer.get(7)},${testBuffer.get(8)})")
        }

        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, 0)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, 0)
    }

    private fun updateJointMatrices(node: SkinGetter) {
        val matrices = node()
        val buffer = BufferUtils.createFloatBuffer(matrices.size * 16)
        for (m in matrices) {
            buffer.put(m.m00).put(m.m01).put(m.m02).put(m.m03)
            buffer.put(m.m10).put(m.m11).put(m.m12).put(m.m13)
            buffer.put(m.m20).put(m.m21).put(m.m22).put(m.m23)
            buffer.put(m.m30).put(m.m31).put(m.m32).put(m.m33)
        }
        buffer.flip()
        
        if (computeCallCount == 1 || computeCallCount % 60 == 0) {
            // Логируем несколько матриц для проверки изменений
            logger.info("[GpuDeformer] Frame $computeCallCount: joint[0] translation=(${matrices[0].m30},${matrices[0].m31},${matrices[0].m32})")
            if (matrices.size > 19) {
                logger.info("[GpuDeformer] Frame $computeCallCount: joint[19] translation=(${matrices[19].m30},${matrices[19].m31},${matrices[19].m32})")
            }
            if (matrices.size > 20) {
                logger.info("[GpuDeformer] Frame $computeCallCount: joint[20] translation=(${matrices[20].m30},${matrices[20].m31},${matrices[20].m32})")
            }
        }
        
        jointMatrixBuffer?.bind()
        GL33.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, buffer)
        jointMatrixBuffer?.unbind()
    }

    private fun setupMorphUniforms(shaderId: Int) {
        if (primitive.morphTargets.isNotEmpty()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE1)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphPosTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasPosition"), 1)

            GL13.glActiveTexture(GL13.GL_TEXTURE2)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphNorTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasNormal"), 2)

            GL13.glActiveTexture(GL13.GL_TEXTURE3)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphTanTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasTangent"), 3)

            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "activeMorphCount"), primitive.morphTargets.size)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "vertexCount"), drawCount)

            val locWeights = GL20.glGetUniformLocation(shaderId, "morphWeights")
            if (locWeights != -1 && primitive.weights.isNotEmpty()) {
                val weightArray = FloatArray(64)
                val copyLength = min(primitive.weights.size, 64)
                System.arraycopy(primitive.weights, 0, weightArray, 0, copyLength)
                GL20.glUniform1fv(locWeights, weightArray)
            }
        } else {
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "activeMorphCount"), 0)
        }
    }

    fun destroy() {
        // Убрали удаление processingVao

        srcJointsBuffer?.delete()
        srcWeightsBuffer?.delete()

        morphPosBuffer?.delete()
        morphNorBuffer?.delete()
        morphTanBuffer?.delete()
        jointMatrixBuffer?.delete()

        if (morphPosTexture != -1) GL11.glDeleteTextures(morphPosTexture)
        if (morphNorTexture != -1) GL11.glDeleteTextures(morphNorTexture)
        if (morphTanTexture != -1) GL11.glDeleteTextures(morphTanTexture)
        if (jointMatrixTexture != -1) GL11.glDeleteTextures(jointMatrixTexture)
    }
}




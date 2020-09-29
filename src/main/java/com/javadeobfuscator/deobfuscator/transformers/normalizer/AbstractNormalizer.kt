/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.javadeobfuscator.deobfuscator.transformers.normalizer

import com.fasterxml.jackson.annotation.JsonProperty
import com.javadeobfuscator.deobfuscator.config.TransformerConfig
import com.javadeobfuscator.deobfuscator.config.TransformerConfig.ConfigOptions
import com.javadeobfuscator.deobfuscator.transformers.Transformer
import com.javadeobfuscator.deobfuscator.utils.ClassTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.*
import java.util.function.Consumer

@ConfigOptions(configClass = AbstractNormalizer.Config::class)
abstract class AbstractNormalizer<T : AbstractNormalizer.Config?> : Transformer<T>() {

    @Throws(Throwable::class)
    override fun transform(): Boolean {
        val remapper = CustomRemapper()

        remap(remapper)

        logger.info("finalyze processing...")

        val updated: MutableMap<String, ClassNode> = HashMap()
        val removed: MutableSet<String> = HashSet()
        classNodes().forEach(Consumer { wr: ClassNode ->
            removed.add(wr.name)
            val newNode = ClassNode()
            val classRemapper = ClassRemapper(newNode, remapper)
            wr.accept(classRemapper)
            updated[newNode.name] = newNode
            deobfuscator.setConstantPool(newNode, deobfuscator.getConstantPool(wr))
        })
        removed.forEach(Consumer { key: String? -> classes.remove(key) })
        removed.forEach(Consumer { key: String? -> classpath.remove(key) })
        classes.putAll(updated)
        classpath.putAll(updated)
        deobfuscator.resetHierachy()
        return true
    }

    abstract fun remap(remapper: CustomRemapper)

    /** We must load the entire class tree so subclasses are correctly counted */
    protected fun loadClassHierarchy() {
        classNodes().forEach { classNode: ClassNode ->
            fillClassHierarchy(deobfuscator.getClassTree(classNode.name).parentClasses, HashSet()) { ct, _, tts ->
                tts.addAll(ct.parentClasses)
                tts.addAll(ct.subClasses)
            }
        }
    }

    protected fun fillClassHierarchy(initial: Collection<String>, tried: MutableSet<String>, func: (ct: ClassTree, polled: String, tts: LinkedList<String>) -> Unit): LinkedList<String> {
        val toTrySelf = LinkedList<String>(initial)
        while (toTrySelf.isNotEmpty()) {
            val polled = toTrySelf.poll()
            if (tried.add(polled) && polled != "java/lang/Object") {
                val ct = deobfuscator.getClassTree(polled)
                func(ct, polled, toTrySelf)
            }
        }
        return toTrySelf
    }

    protected fun isEnumClass(classNode: ClassNode) = classNode.superName == "java/lang/Enum"
    protected fun isInterface(classNode: ClassNode) = classNode.access and Opcodes.ACC_INTERFACE != 0
    protected fun isAbstractClass(classNode: ClassNode) = classNode.access and Opcodes.ACC_ABSTRACT != 0
    protected fun isSyntheticClass(classNode: ClassNode) = classNode.access and Opcodes.ACC_SYNTHETIC != 0

    abstract class Config(implementation: Class<out Transformer<*>?>?) : TransformerConfig(implementation) {
        @JsonProperty(value = "mapping-file")
        var mappingFile: File? = null

        @JsonProperty("package-restrict")
        var packageRestrict: List<String> = emptyList()
    }
}
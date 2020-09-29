/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.javadeobfuscator.deobfuscator.transformers.advanced

import com.javadeobfuscator.deobfuscator.config.TransformerConfig.ConfigOptions
import com.javadeobfuscator.deobfuscator.transformers.normalizer.AbstractNormalizer
import com.javadeobfuscator.deobfuscator.transformers.normalizer.CustomRemapper
import org.assertj.core.internal.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@ConfigOptions(configClass = FieldNormalizer.Config::class)
class FieldNormalizer : AbstractNormalizer<FieldNormalizer.Config>() {
    override fun remap(remapper: CustomRemapper) {
        if (config.packageRestrict.isNotEmpty()) logger.info("[process classes only in packages: ${config.packageRestrict}]")

        loadClassHierarchy()

        val id = AtomicInteger(0)
        classNodes().forEach { classNode: ClassNode ->
            if (config.packageRestrict.isNotEmpty() && !config.packageRestrict.any { classNode.name.startsWith(it) }) return@forEach
            logger.info("processes class '{}'", classNode.name)

            val tree = deobfuscator.getClassTree(classNode.name)
            val allClasses: MutableSet<String> = HashSet()
            val tried: MutableSet<String> = HashSet()

            fillClassHierarchy(tree.parentClasses, tried) { ct, polled, tts ->
                allClasses.add(polled)
                allClasses.addAll(ct.parentClasses)
                tts.addAll(ct.parentClasses)
            }
            fillClassHierarchy(tree.parentClasses, tried) { ct, polled, tts ->
                allClasses.add(polled)
                allClasses.addAll(ct.subClasses)
                tts.addAll(ct.subClasses)
            }

            for (fieldNode in classNode.fields) {
                if (isEnumClass(classNode) && Type.getType(fieldNode.desc).sort == Type.OBJECT && Type.getType(fieldNode.desc).internalName == classNode.name) continue

                val references: MutableList<String> = ArrayList()
                for (possibleClass in allClasses) {
                    val otherNode = deobfuscator.assureLoaded(possibleClass)
                    if (!otherNode.fields.any { otherField -> otherField.name == fieldNode.name && otherField.desc == fieldNode.desc }) {
                        references.add(possibleClass)
                    }
                }

                if (!remapper.fieldMappingExists(classNode.name, fieldNode.name, fieldNode.desc)) {
                    while (true) {
                        val name = "field" + id.getAndIncrement()

                        if (remapper.mapFieldName(classNode.name, fieldNode.name, fieldNode.desc, name, false)) {
                            for (s in references) {
                                remapper.mapFieldName(s, fieldNode.name, fieldNode.desc, name, true)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    class Config : AbstractNormalizer.Config(FieldNormalizer::class.java)
}
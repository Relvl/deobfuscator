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
package com.javadeobfuscator.deobfuscator.transformers.normalizer

import com.javadeobfuscator.deobfuscator.config.TransformerConfig.ConfigOptions
import org.objectweb.asm.tree.ClassNode
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

@ConfigOptions(configClass = AdvancedPackageNormalizer.Config::class)
class AdvancedPackageNormalizer : AbstractNormalizer<AdvancedPackageNormalizer.Config?>() {
    override fun remap(remapper: CustomRemapper) {
        classNodes().forEach(Consumer { classNode: ClassNode ->
            val path = classNode.name.split("/".toRegex()).toTypedArray()
            val packageNames = Arrays.copyOf(path, path.size - 1)
            val oldPackageName = java.lang.String.join("/", *packageNames)
            val newPackageName = Arrays.stream(packageNames).map { pck: String ->
                if (ForbiddenPckNames.contains(pck)) {
                    return@map pck + "_pkg"
                }
                pck
            }.collect(Collectors.joining("/"))
            if (!oldPackageName.equals(newPackageName, ignoreCase = true)) {
                remapper.mapPackage(oldPackageName, newPackageName)
            }
        })
    }

    class Config : AbstractNormalizer.Config(AdvancedPackageNormalizer::class.java)

    companion object {
        val ForbiddenPckNames = setOf("class", "package")
    }
}
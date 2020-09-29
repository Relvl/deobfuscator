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
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.regex.Pattern

@ConfigOptions(configClass = AdvancedClassNormalizer.Config::class)
class AdvancedClassNormalizer : AbstractNormalizer<AdvancedClassNormalizer.Config?>() {
    override fun remap(remapper: CustomRemapper) {
        val id = AtomicInteger(0)
        classNodes().forEach(Consumer { classNode: ClassNode ->
            var packageName: String
            var className: String
            if (classNode.name.contains("/")) {
                packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/'))
                className = classNode.name.substring(packageName.length + 1)
            } else {
                packageName = ""
                className = classNode.name
            }
            if (ClassPtn.matcher(className).matches()) {
                className += "_Class"
            }
            if (packageName.isNotEmpty() && !packageName.endsWith("/")) {
                packageName = "$packageName/"
            }
            var mappedName = packageName + className
            while (!remapper.map(classNode.name, mappedName)) {
                mappedName = packageName + className + +id.getAndIncrement()
            }
        })
    }

    class Config : AbstractNormalizer.Config(AdvancedClassNormalizer::class.java)
    companion object {
        private val ClassPtn = Pattern.compile("^[a-zA-Z0-9]{9,10}$")
    }
}
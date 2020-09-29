package com.javadeobfuscator.deobfuscator.transformers.advanced

import com.javadeobfuscator.deobfuscator.config.TransformerConfig
import com.javadeobfuscator.deobfuscator.transformers.normalizer.AbstractNormalizer
import com.javadeobfuscator.deobfuscator.transformers.normalizer.CustomRemapper
import java.util.concurrent.atomic.AtomicInteger

@TransformerConfig.ConfigOptions(configClass = ClassNormalizer.Config::class)
class ClassNormalizer : AbstractNormalizer<ClassNormalizer.Config>() {
    override fun remap(remapper: CustomRemapper) {
        if (config.packageRestrict.isNotEmpty()) logger.info("[process classes only in packages: ${config.packageRestrict}]")
        val id = AtomicInteger(1)
        classNodes().forEach { classNode ->
            if (config.packageRestrict.isNotEmpty() && !config.packageRestrict.any { classNode.name.startsWith(it) }) return@forEach

            val packagePath = ArrayList(classNode.name.split("/"))
            val className = packagePath.last()
            packagePath.remove(className)
            val packageName = packagePath.joinToString("/")

            val newClassName = when {
                isEnumClass(classNode) -> "${className}_Enum"
                isInterface(classNode) -> "${className}_Interface"
                isAbstractClass(classNode) -> "${className}_ClassAbstract"
                isSyntheticClass(classNode) -> "${className}_ClassSynthetic"
                else -> "${className}_Class"
            }

            if (!className.equals(classNode.name, true)) {
                var mapperUseClassName = "${packageName}/${newClassName}"
                while (!remapper.map(classNode.name, mapperUseClassName)) {
                    mapperUseClassName = "${packageName}/${newClassName}_${id.incrementAndGet()}"
                }
                logger.info("- - - remap class '${classNode.name}' -> '${mapperUseClassName}'")
            }
        }
    }

    class Config : AbstractNormalizer.Config(ClassNormalizer::class.java)
}
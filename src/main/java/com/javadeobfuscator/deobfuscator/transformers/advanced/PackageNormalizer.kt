package com.javadeobfuscator.deobfuscator.transformers.advanced

import com.javadeobfuscator.deobfuscator.config.TransformerConfig
import com.javadeobfuscator.deobfuscator.transformers.normalizer.AbstractNormalizer
import com.javadeobfuscator.deobfuscator.transformers.normalizer.ClassNormalizer
import com.javadeobfuscator.deobfuscator.transformers.normalizer.CustomRemapper

@TransformerConfig.ConfigOptions(configClass = PackageNormalizer.Config::class)
class PackageNormalizer : AbstractNormalizer<PackageNormalizer.Config>() {
    override fun remap(remapper: CustomRemapper) {
        classNodes().forEach { classNode ->
            val packagePath = ArrayList(classNode.name.split("/"))
            packagePath.remove(packagePath.last())
            if (packagePath.isEmpty()) return

            val oldPackageName = packagePath.joinToString("/")
            val newPackageName = packagePath.joinToString("/") { if (ILLEGAL_JAVA_NAMES.contains(it) || ILLEGAL_WINDOWS_CHARACTERS.contains(it)) it + ILLEGAL_PACKAGE_SUFFIX else it }

            if (!oldPackageName.equals(newPackageName, true)) {
                if (remapper.mapPackage(oldPackageName, newPackageName)) {
                    logger.info("- - - remap package '$oldPackageName' -> '$newPackageName'")
                }
            }
        }
    }

    class Config : AbstractNormalizer.Config(PackageNormalizer::class.java)

    companion object {
        private const val ILLEGAL_PACKAGE_SUFFIX = "_pkg"
        val ILLEGAL_WINDOWS_CHARACTERS = arrayOf(
                "aux", "con", "prn", "nul",
                "com0", "com1", "com2", "com3",
                "com4", "com5", "com6", "com7",
                "com8", "com9", "lpt0", "lpt1",
                "lpt2", "lpt3", "lpt4", "lpt5",
                "lpt6", "lpt7", "lpt8", "lpt9")
        val ILLEGAL_JAVA_NAMES = arrayOf(
                "abstract", "assert", "boolean", "break",
                "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do",
                "double", "else", "enum", "extends",
                "false", "final", "finally", "float",
                "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface",
                "long", "native", "new", "null",
                "package", "private", "protected", "public",
                "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "true",
                "try", "void", "volatile", "while")

    }

}
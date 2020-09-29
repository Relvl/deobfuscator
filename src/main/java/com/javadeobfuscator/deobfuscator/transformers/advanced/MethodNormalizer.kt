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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.*
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Suppress("DuplicatedCode")
@ConfigOptions(configClass = MethodNormalizer.Config::class)
class MethodNormalizer : AbstractNormalizer<MethodNormalizer.Config>() {


    override fun remap(remapper: CustomRemapper) {
        if (config.packageRestrict.isNotEmpty()) logger.info("[process classes only in packages: ${config.packageRestrict}]")

        loadClassHierarchy()

        val id = AtomicInteger(0)
        classNodes().forEach { classNode: ClassNode ->
            if (config.packageRestrict.isNotEmpty() && !config.packageRestrict.any { classNode.name.startsWith(it) }) return@forEach
            logger.info("processes class '{}'", classNode.name)

            val allClasses: MutableSet<String> = HashSet()
            val tree = deobfuscator.getClassTree(classNode.name)
            val tried: MutableSet<String> = HashSet()

            fillClassHierarchy(listOf(tree.thisClass), tried) { ct, polled, tts ->
                allClasses.add(polled)
                allClasses.addAll(ct.parentClasses)
                allClasses.addAll(ct.subClasses)
                tts.addAll(ct.parentClasses)
                tts.addAll(ct.subClasses)
            }
            val toTryParent = fillClassHierarchy(tree.parentClasses, tried) { ct, polled, tts ->
                allClasses.add(polled)
                allClasses.addAll(ct.parentClasses)
                tts.addAll(ct.parentClasses)
            }
            val toTryChild = fillClassHierarchy(tree.parentClasses, tried) { ct, polled, tts ->
                allClasses.add(polled)
                allClasses.addAll(ct.subClasses)
                tts.addAll(ct.subClasses)
            }

            allClasses.remove(tree.thisClass)
            val allClassNodes = allClasses.map { name -> deobfuscator.assureLoaded(name) }

            // Подготовка закончена, поехали считать методы.
            for (methodNode in classNode.methods) {
                if (methodNode.name.startsWith("<")) {
                    continue
                }
                if (methodNode.name == "main") {
                    continue
                }

                val allMethodNodes: MutableMap<Map.Entry<ClassNode, MethodNode?>, Boolean> = HashMap()
                val methodType = Type.getReturnType(methodNode.desc)

                if (methodType.sort == Type.ARRAY) {
                    val elementType = methodType.elementType
                    var layers = 1
                    val passed = AtomicBoolean()
                    while (true) {
                        if (passed.get()) {
                            layers++
                            passed.set(false)
                        }
                        if (elementType.sort == Type.OBJECT) {
                            val parent = elementType.internalName
                            val layersF = layers
                            allClassNodes.forEach { node ->
                                var foundSimilar = false
                                var equals = false
                                var equalsMethod: MethodNode? = null
                                for (method in node.methods) {
                                    val thisType = Type.getMethodType(methodNode.desc)
                                    val otherType = Type.getMethodType(method.desc)
                                    if (methodNode.name == method.name && Arrays.equals(thisType.argumentTypes, otherType.argumentTypes)) {
                                        var otherEleType = otherType.returnType
                                        if (toTryParent.contains(node.name) && otherEleType.sort == Type.OBJECT && otherEleType.internalName == "java/lang/Object") {
                                            //Passed (superclass has Object return)
                                            allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                                            break
                                        }
                                        if (otherEleType.sort != Type.ARRAY || otherEleType.dimensions < layersF) {
                                            break
                                        }
                                        for (i in 0 until layersF) {
                                            otherEleType = otherEleType.elementType
                                        }
                                        if (otherEleType.sort == Type.OBJECT) {
                                            foundSimilar = true
                                            val child = otherEleType.internalName
                                            deobfuscator.assureLoaded(parent)
                                            deobfuscator.assureLoaded(child)
                                            if (toTryChild.contains(node.name) && deobfuscator.isSubclass(parent, child) ||
                                                    toTryParent.contains(node.name) && deobfuscator.isSubclass(child, parent) || child == parent) {
                                                equals = true
                                                equalsMethod = method
                                            }
                                        }
                                    }
                                }
                                if (foundSimilar && equals) {
                                    allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                                } else {
                                    allMethodNodes[SimpleEntry(node, methodNode)] = false
                                }
                            }
                            break
                        } else if (elementType.sort == Type.ARRAY) {
                            val layersF = layers
                            allClassNodes.forEach { node ->
                                val equalsMethod: MethodNode? = null
                                for (method in node.methods) {
                                    val thisType = Type.getMethodType(methodNode.desc)
                                    val otherType = Type.getMethodType(method.desc)
                                    if (methodNode.name == method.name && Arrays.equals(thisType.argumentTypes, otherType.argumentTypes)) {
                                        var otherEleType = otherType.returnType
                                        for (i in 0 until layersF) {
                                            otherEleType = otherEleType.elementType
                                        }
                                        if (otherEleType.sort == Type.ARRAY) {
                                            //Continue checking element
                                            passed.set(true)
                                            continue
                                        } else if (toTryParent.contains(node.name) && otherEleType.sort == Type.OBJECT && otherEleType.internalName == "java/lang/Object") {
                                            //Passed (superclass has Object return)
                                            allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                                            break
                                        } else {
                                            //Fail
                                            break
                                        }
                                    }
                                }
                            }
                        } else {
                            val layersF = layers
                            allClassNodes.forEach { node ->
                                var foundSimilar = false
                                var equals = false
                                var equalsMethod: MethodNode? = null
                                for (method in node.methods) {
                                    val thisType = Type.getMethodType(methodNode.desc)
                                    val otherType = Type.getMethodType(method.desc)
                                    if (methodNode.name == method.name && Arrays.equals(thisType.argumentTypes, otherType.argumentTypes)) {
                                        foundSimilar = true
                                        var otherEleType = otherType.returnType
                                        if (toTryParent.contains(node.name) && otherEleType.sort == Type.OBJECT && otherEleType.internalName == "java/lang/Object") {
                                            //Passed (superclass has Object return)
                                            allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                                            break
                                        }
                                        if (otherEleType.sort != Type.ARRAY || otherEleType.dimensions < layersF) {
                                            break
                                        }
                                        for (i in 0 until layersF) {
                                            otherEleType = otherEleType.elementType
                                        }
                                        if (elementType.sort == otherEleType.sort) {
                                            equals = true
                                            equalsMethod = method
                                        }
                                    }
                                }
                                if (foundSimilar && equals) {
                                    allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                                } else {
                                    allMethodNodes[SimpleEntry(node, methodNode)] = false
                                }
                            }
                            break
                        }
                    }
                } else if (methodType.sort == Type.OBJECT) {
                    val parent = methodType.internalName
                    allClassNodes.forEach { node ->
                        var foundSimilar = false
                        var equals = false
                        var equalsMethod: MethodNode? = null
                        for (method in node.methods) {
                            val thisType = Type.getMethodType(methodNode.desc)
                            val otherType = Type.getMethodType(method.desc)
                            if (methodNode.name == method.name && Arrays.equals(thisType.argumentTypes, otherType.argumentTypes)) {
                                if (otherType.returnType.sort == Type.OBJECT) {
                                    foundSimilar = true
                                    val child = otherType.returnType.internalName
                                    deobfuscator.assureLoaded(parent)
                                    deobfuscator.assureLoaded(child)
                                    if (toTryChild.contains(node.name) && deobfuscator.isSubclass(parent, child) ||
                                            toTryParent.contains(node.name) && deobfuscator.isSubclass(child, parent) || child == parent) {
                                        equals = true
                                        equalsMethod = method
                                    }
                                } else if (parent == "java/lang/Object" && toTryChild.contains(node.name) && otherType.sort == Type.ARRAY) {
                                    //Arrays extend object
                                    foundSimilar = true
                                    equals = true
                                    equalsMethod = method
                                }
                            }
                        }
                        if (foundSimilar && equals) {
                            allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                        } else {
                            allMethodNodes[SimpleEntry(node, methodNode)] = false
                        }
                    }
                } else {
                    // не объект и не массив - тогда что?
                    require(methodType.sort != Type.METHOD) { "Did not expect method" }

                    allClassNodes.forEach { node ->
                        var foundSimilar = false
                        var equals = false
                        var equalsMethod: MethodNode? = null
                        for (method in node.methods) {
                            val thisType = Type.getMethodType(methodNode.desc)
                            val otherType = Type.getMethodType(method.desc)
                            if (methodNode.name == method.name && Arrays.equals(thisType.argumentTypes, otherType.argumentTypes)) {
                                foundSimilar = true
                                if (thisType.returnType.sort == otherType.returnType.sort) {
                                    equals = true
                                    equalsMethod = method
                                }
                            }
                        }
                        if (foundSimilar && equals) {
                            allMethodNodes[SimpleEntry(node, equalsMethod)] = true
                        } else {
                            allMethodNodes[SimpleEntry(node, methodNode)] = false
                        }
                    }
                }

                val isLibrary = AtomicBoolean(false)
                allMethodNodes.forEach { (key, similarAndEquals) ->
                    if (deobfuscator.isLibrary(key.key) && similarAndEquals) {
                        isLibrary.set(true)
                    }
                }
                if (!isLibrary.get()) {
                    if (!remapper.methodMappingExists(classNode.name, methodNode.name, methodNode.desc)) {
                        while (true) {
                            val name = "method" + id.getAndIncrement()

                            if (remapper.mapMethodName(classNode.name, methodNode.name, methodNode.desc, name, false)) {
                                allMethodNodes.keys.forEach { ent ->
                                    remapper.mapMethodName(ent.key.name, ent.value!!.name, ent.value!!.desc, name, true)
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    class Config : AbstractNormalizer.Config(MethodNormalizer::class.java)
}
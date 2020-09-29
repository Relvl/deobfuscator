package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.assertj.core.internal.asm.Opcodes;
import org.assertj.core.internal.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.utils.Utils;

@TransformerConfig.ConfigOptions(configClass = EnumNormalizer.Config.class)
public class EnumNormalizer extends AbstractNormalizer<EnumNormalizer.Config> {
    @Override
    public void remap(CustomRemapper remapper) {
        for (ClassNode classNode : classNodes()) {
            // null-check cuz 'module-info' element does not inherits Object
            if (classNode.superName == null || !classNode.superName.equals("java/lang/Enum")) {
                continue;
            }
            if (!getConfig().getPackageRestrict().isEmpty() && getConfig().getPackageRestrict().stream().noneMatch(s -> classNode.name.startsWith(s))) {
                continue;
            }

            logger.info("- - - processing enum '{}'", classNode.name);

            MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null && clinit.instructions != null && clinit.instructions.getFirst() != null) {
                //Fix order
                LinkedHashMap<LabelNode, List<AbstractInsnNode>> result = new FlowAnalyzer(clinit).analyze(clinit.instructions.getFirst(), new ArrayList<>(), new HashMap<>(), false, true);

                List<FieldNode> order = new ArrayList<>();
                FieldNode valuesArr = null;
                boolean hasDuplicate = false;

                for (List<AbstractInsnNode> insns : result.values()) {
                    for (AbstractInsnNode ain : insns) {
                        if (isSomething1(ain, classNode)) {
                            FieldNode field = classNode.fields.stream()
                                                              .filter(f -> f.name.equals(((FieldInsnNode)ain).name) && Type.getType(f.desc).getInternalName().equals(classNode.name))
                                                              .findFirst()
                                                              .orElse(null);
                            if (field != null && Modifier.isStatic(field.access)) {
                                order.add(field);
                                classNode.fields.remove(field);
                            }
                        }
                        else if (!hasDuplicate && ain.getOpcode() == Opcodes.PUTSTATIC && Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.ARRAY &&
                                 Type.getType(((FieldInsnNode)ain).desc).getElementType().getSort() == Type.OBJECT &&
                                 Type.getType(((FieldInsnNode)ain).desc).getElementType().getInternalName().equals(classNode.name)) {
                            FieldNode field = classNode.fields.stream().filter(f -> {
                                try {
                                    return f.name.equals(((FieldInsnNode)ain).name) && Type.getType(f.desc).getElementType().getInternalName().equals(classNode.name);
                                }
                                catch (Exception e) {
                                    // При слишком жесткой обфускации сюда может залететь примитив и всё упадёт.
                                    // todo! это не правильно, условие выше должно было отсеять примитивы?
                                    return false;
                                }
                            }).findFirst().orElse(null);
                            if (field != null && Modifier.isStatic(field.access)) {
                                if (valuesArr != null) {
                                    hasDuplicate = true;
                                }
                                else {
                                    valuesArr = field;
                                }
                            }
                        }
                    }
                }

                if (valuesArr != null) {
                    valuesArr.access |= Opcodes.ACC_SYNTHETIC;
                    classNode.fields.remove(valuesArr);
                    order.add(valuesArr);
                }

                Collections.reverse(order);
                for (FieldNode field : order) {
                    classNode.fields.add(0, field);
                }

                //Fix names
                Frame<SourceValue>[] frames;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, clinit);
                }
                catch (AnalyzerException e) {
                    oops("unexpected analyzer exception", e);
                    continue;
                }
                for (List<AbstractInsnNode> insns : result.values()) {
                    for (AbstractInsnNode ain : insns) {
                        if (isSomething1(ain, classNode) && Utils.getPrevious(ain) != null && Utils.getPrevious(ain).getOpcode() == Opcodes.INVOKESPECIAL) {
                            FieldNode field = classNode.fields.stream()
                                                              .filter(f -> f.name.equals(((FieldInsnNode)ain).name) && Type.getType(f.desc).getInternalName().equals(classNode.name))
                                                              .findFirst()
                                                              .orElse(null);
                            if (field != null && Modifier.isStatic(field.access)) {
                                int argLen = Type.getArgumentTypes(((MethodInsnNode)Utils.getPrevious(ain)).desc).length;
                                Frame<SourceValue> frame = frames[clinit.instructions.indexOf(Utils.getPrevious(ain))];
                                if (frame.getStack(frame.getStackSize() - argLen).insns.size() == 1 &&
                                    frame.getStack(frame.getStackSize() - argLen).insns.iterator().next().getOpcode() == Opcodes.LDC) {
                                    String value = (String)((LdcInsnNode)frame.getStack(frame.getStackSize() - argLen).insns.iterator().next()).cst;
                                    if (!field.name.equals(value)) {
                                        remapper.mapFieldName(classNode.name, field.name, field.desc, value, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSomething1(AbstractInsnNode ain, ClassNode classNode) {
        return ain.getOpcode() == Opcodes.PUTSTATIC && Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.OBJECT &&
               Type.getType(((FieldInsnNode)ain).desc).getInternalName().equals(classNode.name);
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(EnumNormalizer.class);
        }
    }
}

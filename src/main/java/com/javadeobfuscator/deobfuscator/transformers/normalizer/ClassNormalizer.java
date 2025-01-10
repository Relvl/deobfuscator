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

package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TransformerConfig.ConfigOptions(configClass = ClassNormalizer.Config.class)
public class ClassNormalizer extends AbstractNormalizer<ClassNormalizer.Config> {
    @Override
    public void remap(CustomRemapper remapper) {
        classNodes().forEach(classNode -> {
            String newName;

            for (Pattern pattern : getDeobfuscator().getConfig().getSkipNormalizeCache()) {
                Matcher matcher = pattern.matcher(classNode.name);
                if (matcher.find()) {
                    return;
                }
            }

            if (classNode.name.contains("/")) {
                int idx = classNode.name.lastIndexOf('/');
                String packageName = classNode.name.substring(0, idx);
                String simpleClassName = classNode.name.substring(idx + 1);
                newName = packageName + "/" + getDeobfuscator().getConfig().getCustomClassName(simpleClassName, "Class_" + simpleClassName);
            } else {
                newName = getDeobfuscator().getConfig().getCustomClassName(classNode.name, "Class_" + classNode.name);
            }

            remapper.map(classNode.name, newName);
        });
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(ClassNormalizer.class);
        }
    }
}

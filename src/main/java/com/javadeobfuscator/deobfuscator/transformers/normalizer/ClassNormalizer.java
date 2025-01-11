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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@TransformerConfig.ConfigOptions(configClass = ClassNormalizer.Config.class)
public class ClassNormalizer extends AbstractNormalizer<ClassNormalizer.Config> {
    @Override
    public void remap(CustomRemapper remapper) {
        classNodes().forEach(classNode -> {
            String newName;

            for (Pattern pattern : getConfig().skipRename()) {
                Matcher matcher = pattern.matcher(classNode.name);
                if (matcher.find()) {
                    return;
                }
            }

            if (classNode.name.contains("/")) {
                int idx = classNode.name.lastIndexOf('/');
                String packageName = classNode.name.substring(0, idx);
                String simpleClassName = classNode.name.substring(idx + 1);
                newName = packageName + "/" + getConfig().getCustomClassName(simpleClassName, "Class_" + simpleClassName);
            } else {
                newName = getConfig().getCustomClassName(classNode.name, "Class_" + classNode.name);
            }

            remapper.map(classNode.name, newName);
        });
    }

    public static class Config extends AbstractNormalizer.Config {
        @JsonProperty
        private List<String> skipRenameClasses;

        @JsonProperty
        private Map<String, String> customClassNames;

        @JsonIgnore
        private List<Pattern> _skipNormalize;

        public Config() {
            super(ClassNormalizer.class);
        }

        public List<Pattern> skipRename() {
            if (_skipNormalize == null) {
                _skipNormalize = new ArrayList<>();
                if (skipRenameClasses != null) {
                    for (String ignoredClass : skipRenameClasses) {
                        Pattern pattern;
                        try {
                            pattern = Pattern.compile(ignoredClass);
                            _skipNormalize.add(pattern);
                        } catch (PatternSyntaxException e) {
                            System.err.println("Error while compiling pattern for ignore statement " + ignoredClass);
                            e.printStackTrace();
                        }
                    }
                }
            }
            return _skipNormalize;
        }

        public String getCustomClassName(String name, String defaultName) {
            if (customClassNames == null) return defaultName;
            return customClassNames.getOrDefault(name, defaultName);
        }

    }
}

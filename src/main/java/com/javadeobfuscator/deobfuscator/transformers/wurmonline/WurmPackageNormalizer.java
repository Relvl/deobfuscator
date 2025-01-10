package com.javadeobfuscator.deobfuscator.transformers.wurmonline;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.normalizer.AbstractNormalizer;
import com.javadeobfuscator.deobfuscator.transformers.normalizer.CustomRemapper;

import java.util.concurrent.atomic.AtomicInteger;

@TransformerConfig.ConfigOptions(configClass = WurmPackageNormalizer.Config.class)
public class WurmPackageNormalizer extends AbstractNormalizer<WurmPackageNormalizer.Config> {
    private static final String[] WRONG_PK_NAMES = new String[]{"class", "package"};

    @Override
    public void remap(CustomRemapper remapper) {
        AtomicInteger uniqId = new AtomicInteger(0);

        classNodes().forEach(classNode -> {
            String packageName = classNode.name.lastIndexOf('/') == -1 ? "" : classNode.name.substring(0, classNode.name.lastIndexOf('/'));
            if (packageName.isEmpty()) return;

            // Just for now - only first level packages as most commonly
            for (String wrongPkName : WRONG_PK_NAMES) {
                if (packageName.equals(wrongPkName)) {
                    remapper.mapPackage(packageName, "package_" + uniqId.getAndIncrement());
                    break;
                }
            }
        });
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(WurmPackageNormalizer.class);
        }
    }
}

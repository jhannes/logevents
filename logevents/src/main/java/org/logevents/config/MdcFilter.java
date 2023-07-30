package org.logevents.config;

import java.util.HashSet;
import java.util.Set;

public interface MdcFilter {

    IncludeAll INCLUDE_ALL = new IncludeAll();

    boolean isKeyIncluded(String key);

    class IncludedMdcKeys implements MdcFilter {

        private final Set<String> includedMdcKeys;

        public IncludedMdcKeys(Set<String> includedMdcKeys) {
            this.includedMdcKeys = includedMdcKeys;
        }

        @Override
        public boolean isKeyIncluded(String key) {
            return includedMdcKeys.contains(key);
        }
    }

    class ExcludedMdcKeys implements MdcFilter {
        private final HashSet<String> excludedMdcKeys;

        public ExcludedMdcKeys(HashSet<String> excludedMdcKeys) {
            this.excludedMdcKeys = excludedMdcKeys;
        }

        @Override
        public boolean isKeyIncluded(String key) {
            return !excludedMdcKeys.contains(key);
        }
    }

    class IncludeAll implements MdcFilter {
        @Override
        public boolean isKeyIncluded(String key) {
            return true;
        }
    }


}

package ai.platon.pulsar.persist;

import ai.platon.pulsar.common.config.ImmutableConfig;
import org.apache.hadoop.conf.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.Map;
import java.util.TreeMap;

public final class HadoopUtils {

    public static final HadoopUtils INSTANCE = new HadoopUtils();

    private HadoopUtils() {
    }

    public Configuration toHadoopConfiguration(ImmutableConfig conf) {
        Configuration hadoopConfiguration = new Configuration();

        // conf.unbox().forEach(hadoopConfiguration::set);

        // spring properties has higher priority
        // dumpSpringProperties(conf.getEnvironment()).forEach(hadoopConfiguration::set);

        return hadoopConfiguration;
    }

    private Map<String, String> dumpSpringProperties(Environment environment) {
        if (environment == null) {
            return java.util.Collections.emptyMap();
        }

        Map<String, String> properties = new TreeMap<>();
        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configurableEnv = (ConfigurableEnvironment) environment;
            for (PropertySource<?> propertySource : configurableEnv.getPropertySources()) {
                Object source = propertySource.getSource();
                if (source instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> mapSource = (Map<Object, Object>) source;
                    mapSource.forEach((key, value) -> {
                        if (key != null && value != null) {
                            properties.put(key.toString(), value.toString());
                        }
                    });
                }
            }
        }
        return properties;
    }
}

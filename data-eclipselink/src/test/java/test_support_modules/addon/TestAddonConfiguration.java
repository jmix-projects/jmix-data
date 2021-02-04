package test_support_modules.addon;

import io.jmix.core.annotation.JmixModule;
import io.jmix.dataeclipselink.DataEclipselinkConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration
@JmixModule(dependsOn = {DataEclipselinkConfiguration.class})
public class TestAddonConfiguration {
}

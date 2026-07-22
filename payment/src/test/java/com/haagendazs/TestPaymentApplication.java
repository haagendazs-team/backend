package com.haagendazs;

import java.io.IOException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.haagendazs",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = PaymentApplication.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.CUSTOM,
                        classes = TestPaymentApplication.NotificationModuleFilter.class
                )
        }
)
public class TestPaymentApplication {

    public static class NotificationModuleFilter implements TypeFilter {

        @Override
        public boolean match(
                MetadataReader metadataReader,
                MetadataReaderFactory metadataReaderFactory
        ) throws IOException {
            return metadataReader.getResource().getURL().getPath()
                    .contains("/notification/build/classes/");
        }
    }
}

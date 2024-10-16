package io.quarkus.it.jpa.postgresql.otherpu;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;

import io.quarkus.hibernate.common.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.JsonFormat;

@JsonFormat
@PersistenceUnitExtension("other")
public class JsonFormatMapper implements FormatMapper {

    @Override
    public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        throw new UnsupportedOperationException("I cannot convert anything from JSON.");
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        throw new UnsupportedOperationException("I cannot convert anything to JSON.");
    }
}

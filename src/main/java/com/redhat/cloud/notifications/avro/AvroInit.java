package com.redhat.cloud.notifications.avro;

import org.apache.avro.LogicalTypes;
import org.apache.avro.LogicalTypes.LogicalTypeFactory;

public class AvroInit {

    private AvroInit() {

    }

    public static void init() {
        LogicalTypeFactory[] logicalTypeFactories = {new JsonObjectFactory(), new Iso8601Factory()};
        for (LogicalTypeFactory ltf : logicalTypeFactories) {
            LogicalTypes.register(ltf.getTypeName(), ltf);
        }
    }
}

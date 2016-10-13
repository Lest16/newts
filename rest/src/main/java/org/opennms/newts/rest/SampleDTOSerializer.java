/*
 * Copyright 2014, The OpenNMS Group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opennms.newts.rest;

import java.io.IOException;

import org.opennms.newts.api.Context;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class SampleDTOSerializer extends JsonSerializer<SampleDTO> {

    @Override
    public void serialize(SampleDTO value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("name", value.getName());
        jgen.writeNumberField("timestamp", value.getTimestamp());
        jgen.writeStringField("type", value.getType().toString());
        jgen.writeObjectField("value", value.getValue());
        jgen.writeObjectField("time-to-live", value.getTimeToLive());
        
        // Since attributes is optional, be compact and omit from JSON output when unused.
        if (value.getAttributes() != null && !value.getAttributes().isEmpty()) {
            jgen.writeObjectField("attributes", value.getAttributes());
        }

        // Omit the context field when it is set to the default
        if (!Context.DEFAULT_CONTEXT.equals(value.getContext())) {
            jgen.writeStringField("context", value.getContext().getId());
        }

        jgen.writeEndObject();
    }

}

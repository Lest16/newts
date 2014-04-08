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
package org.opennms.newts.gsod;


import java.util.Properties;

import org.opennms.newts.api.SampleRepository;
import org.opennms.newts.persistence.cassandra.CassandraSampleRepository;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;


public class Config extends AbstractModule {

    @Override
    protected void configure() {

        bind(SampleRepository.class).to(CassandraSampleRepository.class);

        Properties properties = new Properties();
        properties.put("cassandra.keyspace", System.getProperty("cassandra.keyspace", "newts"));
        properties.put("cassandra.host", System.getProperty("cassandra.host", "localhost"));
        properties.put("cassandra.port", System.getProperty("cassandra.port", "9042"));
        Names.bindProperties(binder(), properties);

        bind(MetricRegistry.class).toInstance(new MetricRegistry());

    }

}

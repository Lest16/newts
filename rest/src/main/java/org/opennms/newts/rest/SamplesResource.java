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


import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.newts.api.Context;
import org.opennms.newts.api.MetricType;
import org.opennms.newts.api.Resource;
import org.opennms.newts.api.SampleRepository;
import org.opennms.newts.api.Timestamp;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import org.opennms.newts.api.search.Indexer;


@Path("/samples")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SamplesResource {

    private final SampleRepository m_sampleRepository;
    private final Indexer m_indexer;

    public SamplesResource(SampleRepository sampleRepository,
                           Indexer indexer) {
        m_sampleRepository = checkNotNull(sampleRepository, "sample repository");
        m_indexer = checkNotNull(indexer, "indexer");
    }

    @POST
    @Timed
    public Response writeSamples(Collection<SampleDTO> samples) {
    	ResourceDTO f = new ResourceDTO("5", new HashMap<String, String>());
    	
    	SampleDTO s = new SampleDTO(390009300, f, "sdg", MetricType.GAUGE, 45, new HashMap<String, String>(), "skgsjgskgs", 10);
    	Collection<SampleDTO> k = new ArrayList<SampleDTO>();
    	k.add(s);
    	m_sampleRepository.insert(Transform.samples(k));
        //m_sampleRepository.insert(Transform.samples(samples));
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Timed
    @Path("/{resource}")
    public Collection<Collection<SampleDTO>> getSamples(@PathParam("resource") Resource resource,
            @QueryParam("start") Optional<TimestampParam> start, @QueryParam("end") Optional<TimestampParam> end,
            @QueryParam("context") Optional<String> contextId) {

        Optional<Timestamp> lower = Transform.toTimestamp(start);
        Optional<Timestamp> upper = Transform.toTimestamp(end);
        Context context = contextId.isPresent() ? new Context(contextId.get()) : Context.DEFAULT_CONTEXT;

        return Transform.sampleDTOs(m_sampleRepository.select(context, resource, lower, upper));
    }

    @DELETE
    @Timed
    @Path("/{resource}")
    public void deleteSamples(@PathParam("resource") Resource resource,
                              @QueryParam("context") Optional<String> contextId) {
        final Context context = contextId.isPresent()
                                ? new Context(contextId.get())
                                : Context.DEFAULT_CONTEXT;

        m_sampleRepository.delete(context, resource);
        m_indexer.delete(context, resource);
    }
}

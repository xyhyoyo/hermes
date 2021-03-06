package com.ctrip.hermes.metaserver.rest.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import com.ctrip.hermes.core.utils.PlexusComponentLocator;
import com.ctrip.hermes.meta.entity.Meta;
import com.ctrip.hermes.meta.entity.Server;
import com.ctrip.hermes.metaserver.cluster.ClusterStateHolder;
import com.ctrip.hermes.metaserver.meta.MetaHolder;
import com.ctrip.hermes.metaserver.rest.commons.RestException;

@Path("/metaserver/")
@Singleton
@Produces(MediaType.APPLICATION_JSON)
public class MetaServerResource {

	private MetaHolder m_metaHolder = PlexusComponentLocator.lookup(MetaHolder.class);

	private ClusterStateHolder m_clusterStatusHolder = PlexusComponentLocator.lookup(ClusterStateHolder.class);

	@GET
	@Path("servers")
	public List<String> getServers() {
		Meta meta = m_metaHolder.getMeta();
		if (meta == null) {
			throw new RestException("Meta not found", Status.NOT_FOUND);
		}

		Collection<Server> servers = meta.getServers().values();
		List<String> result = new ArrayList<>();
		for (Server server : servers) {
			result.add(String.format("%s:%s", server.getHost(), server.getPort()));
		}
		return result;
	}

	@GET
	@Path("status")
	public MetaStatusStatusResponse getStatus() {
		MetaStatusStatusResponse response = new MetaStatusStatusResponse();
		response.setLeader(m_clusterStatusHolder.hasLeadership());
		return response;
	}

	public static class MetaStatusStatusResponse {
		private boolean m_leader;

		public boolean isLeader() {
			return m_leader;
		}

		public void setLeader(boolean leader) {
			m_leader = leader;
		}

	}
}

package com.konka.dhtsearch.bittorrentkad.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.konka.dhtsearch.Node;
import com.konka.dhtsearch.bittorrentkad.bucket.KBuckets;
import com.konka.dhtsearch.bittorrentkad.cache.KadCache;
import com.konka.dhtsearch.bittorrentkad.msg.FindNodeRequest;
import com.konka.dhtsearch.bittorrentkad.msg.FindNodeResponse;
import com.konka.dhtsearch.bittorrentkad.msg.KadMessage;
import com.konka.dhtsearch.bittorrentkad.net.Communicator;
import com.konka.dhtsearch.bittorrentkad.net.MessageDispatcher;
import com.konka.dhtsearch.bittorrentkad.net.filter.MessageFilter;
import com.konka.dhtsearch.bittorrentkad.net.filter.TypeMessageFilter;

/**
 * Handle find node requests by giving the known closest nodes to the requested
 * key from the KBuckets data structure
 * 
 * @author eyal.kibbar@gmail.com
 * 
 */
public class KademliaFindNodeHandler extends AbstractHandler implements FindNodeHandler {
	private final Communicator kadServer;
	private final Node localNode;
	private final KadCache cache;
	private final KBuckets kBuckets;
	private final int kBucketSize;

	private final AtomicInteger nrFindnodeHits;
	private final AtomicInteger nrFindnodeMiss;

	KademliaFindNodeHandler(final  MessageDispatcher<Void>  msgDispatcherProvider, final Communicator kadServer,
			 final Node localNode, final KadCache cache, final KBuckets kBuckets,
			  final int kBucketSize,

			 final AtomicInteger nrFindnodeHits,
			  final AtomicInteger nrFindnodeMiss) {

		super(msgDispatcherProvider);
		this.kadServer = kadServer;
		this.localNode = localNode;
		this.cache = cache;
		this.kBuckets = kBuckets;
		this.kBucketSize = kBucketSize;

		this.nrFindnodeHits = nrFindnodeHits;
		this.nrFindnodeMiss = nrFindnodeMiss;
	}

	@Override
	public void completed(final KadMessage msg, final Void attachment) {

		final FindNodeRequest findNodeRequest = ((FindNodeRequest) msg);
		final FindNodeResponse findNodeResponse = findNodeRequest.generateResponse(this.localNode).setCachedResults(false);

		List<Node> cachedResults = null;

		if (!findNodeRequest.shouldSearchCache())
			findNodeResponse.setNodes(this.kBuckets.getClosestNodesByKey(findNodeRequest.getKey(), this.kBucketSize));
		else {
			// requester ask to search in cache
			cachedResults = this.cache.search(findNodeRequest.getKey());

			if (cachedResults == null) {
				this.nrFindnodeMiss.incrementAndGet();
				findNodeResponse.setNodes(this.kBuckets.getClosestNodesByKey(findNodeRequest.getKey(), this.kBucketSize));
			} else {
				this.nrFindnodeHits.incrementAndGet();
				findNodeResponse.setNodes(new ArrayList<Node>(cachedResults)).setCachedResults(true);

			}
		}

		try {
			this.kadServer.send(msg.getSrc(), findNodeResponse);
		} catch (final IOException e) {
			// could not send back a response
			// nothing to do
			e.printStackTrace();
		}
	}

	@Override
	public void failed(final Throwable exc, final Void attachment) {
		// should never b here
		exc.printStackTrace();
	}

	@Override
	protected Collection<MessageFilter> getFilters() {
		// only accept FindNodeRequests messages
		return Arrays.asList(new MessageFilter[]{new TypeMessageFilter(FindNodeRequest.class)});
	}
}
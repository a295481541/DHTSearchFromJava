package com.konka.dhtsearch.bittorrentkad.krpc;

import com.konka.dhtsearch.Node;

/**
 * Base class for all responses
 * 
 * @author eyal.kibbar@gmail.com
 *
 */
public abstract class KadResponse extends KadMessage {

	private static final long serialVersionUID = 5247239397467830857L;

	protected KadResponse(long id, Node src) {
		super(id, src);
	}

}
package com.konka.dhtsearch.bittorrentkad.msg;

import java.io.Serializable;

import com.konka.dhtsearch.Node;

/**
 * A message containing arbitrary data to be used by the KeybasedRouting.sendRequest methods
 * @author eyal.kibbar@gmail.com
 *
 */
public class ContentResponse extends KadResponse {

	private static final long serialVersionUID = -4479208136049358778L;

	private Serializable content;
	
	ContentResponse(long id, Node src) {
		super(id, src);
	}

	public Serializable getContent() {
		return content;
	}
	
	public ContentResponse setContent(Serializable content) {
		this.content = content;
		return this;
	}

}
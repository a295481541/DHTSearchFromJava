package com.konka.dhtsearch.bittorrentkad.net.filter;

import com.konka.dhtsearch.bittorrentkad.msg.KadMessage;

/**
 * Reject all messages not in the given class
 * @author eyal.kibbar@gmail.com
 *
 */
public class TypeMessageFilter implements MessageFilter {

	private final Class<? extends KadMessage> clazz;
	
	public TypeMessageFilter(Class<? extends KadMessage> clazz) {
		this.clazz = clazz;
	}
	
	@Override
	public boolean shouldHandle(KadMessage m) {
		return m.getClass().equals(clazz);
	}

	
}
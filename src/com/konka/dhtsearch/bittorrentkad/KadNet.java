package com.konka.dhtsearch.bittorrentkad;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.konka.dhtsearch.AppManager;
import com.konka.dhtsearch.Key;
import com.konka.dhtsearch.KeybasedRouting;
import com.konka.dhtsearch.Node;
import com.konka.dhtsearch.bittorrentkad.bucket.Bucket;
import com.konka.dhtsearch.bittorrentkad.bucket.SlackBucket;
import com.konka.dhtsearch.bittorrentkad.krpc.KadMessage;
import com.konka.dhtsearch.bittorrentkad.net.KadParserTorrentServer;
import com.konka.dhtsearch.bittorrentkad.net.KadReceiveServer;
import com.konka.dhtsearch.bittorrentkad.net.KadSendMsgServer;
import com.konka.dhtsearch.exception.ErrHandler;
import com.konka.dhtsearch.util.ThreadUtil;

/**
 * KadNet
 * 
 * @author 耳东 (cgp@0731life.com)
 * 
 */
public class KadNet implements KeybasedRouting, Runnable {
	private KadReceiveServer kadReceiveServer;// 接受消息
	private KadSendMsgServer kadSendMsgServer;// 发生消息
	private static KadParserTorrentServer kadParserTorrentServer;// = new KadParserTorrentServer();// 解析种子
	private final static Bucket kadBuckets = new SlackBucket(10000);// =
																	// AppManager.getKadBuckets();//
																	// 路由表
	private final int BUCKETSIZE = 8;// 一个k桶大小
	private final BootstrapNodesSaver bootstrapNodesSaver;// 关机后保存到本地，启动时候从本地文件中加载
	private final DatagramChannel channel;
	private final Node localnode;
	private Selector selector;

	/**
	 * @param bootstrapNodesSaver
	 * @param localnode
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public KadNet(BootstrapNodesSaver bootstrapNodesSaver, Node localnode) throws NoSuchAlgorithmException, IOException {
		this.bootstrapNodesSaver = bootstrapNodesSaver;
		DatagramSocket socket = null;

		this.localnode = localnode;
		// -----------------------------------------------------------------------
		channel = DatagramChannel.open();
		socket = channel.socket();
		channel.configureBlocking(false);// 调整此通道的阻塞模式 false 否 ture 是。
		socket.bind(localnode.getSocketAddress());
		selector = Selector.open();
		channel.register(selector, SelectionKey.OP_READ);

		// -----------------------------------------------------------------------

		// this.kadBuckets = new SlackBucket(1000);
		// this.kadParserTorrentServer = new KadParserTorrentServer();
	}

	private void startKadReceiveServer() {
		this.kadReceiveServer = new KadReceiveServer(selector, this);
		kadReceiveServer.setUncaughtExceptionHandler(new ErrHandler() {
			@Override
			public void caughtEnd() {
				System.gc();
				ThreadUtil.sleep(1000 * 10);
				startKadReceiveServer();
			}
		});
		kadReceiveServer.start();
	}

	private void startKadParserTorrentServer() {
		kadParserTorrentServer = new KadParserTorrentServer();
		kadParserTorrentServer.setUncaughtExceptionHandler(new ErrHandler() {
			@Override
			public void caughtEnd() {
				System.gc();
				ThreadUtil.sleep(1000 * 10);
				startKadParserTorrentServer();
			}
		});
		kadParserTorrentServer.start();
	}

	private void startKadSendMsgServer() {

		kadSendMsgServer = new KadSendMsgServer(this);
		kadSendMsgServer.setUncaughtExceptionHandler(new ErrHandler() {
			@Override
			public void caughtEnd() {
				System.gc();
				ThreadUtil.sleep(1000 * 1);
				System.out.println("重启" + kadSendMsgServer);
				startKadSendMsgServer();
			}
		});
		kadSendMsgServer.start();
	}

	public void addNodeToBuckets(Node node) {
		if (!node.equals(localnode)) {
			kadBuckets.insert(new KadNode().setNode(node).setNodeWasContacted());// 插入一个节点
		}
	}

	@Override
	public void create() throws IOException {
//		startKadReceiveServer();
//		startKadSendMsgServer();
		if (kadParserTorrentServer == null || !kadParserTorrentServer.isRunning()) {
			startKadParserTorrentServer();
		}
		// kadParserTorrentServer.
		// if (!kadParserTorrentServer.isRunning()) {
		// kadParserTorrentServer.start();
		// }
		if (bootstrapNodesSaver != null) {
			bootstrapNodesSaver.load();
			bootstrapNodesSaver.start();
		}
		starting = true;
	}

	private boolean starting = false;

	public boolean isStarting() {

		return starting;
	}

	/**
	 * 加入已知节点uri
	 */
	@Override
	public void join(KadNode... kadNodes) {
		for (KadNode kadNode : kadNodes) {
			// kadBuckets.insert(kadNode);
			kadBuckets.insertToPublicBucket(kadNode);
		}
	}

	public List<KadNode> getAllNodes() {
		return kadBuckets.getAllNodes();
	}

	public KadNet join(InetSocketAddress... inetSocketAddresses) {
		for (InetSocketAddress socketAddress : inetSocketAddresses) {
			Key key = AppManager.getKeyFactory().generate();
			Node node = new Node(key).setSocketAddress(socketAddress);
			join(new KadNode().setNode(node).setNodeWasContacted());
		}
		return this;
	}

	@Override
	public List<Node> findNode(Key k) {// 根据k返回相似节点
		List<Node> result = kadBuckets.getClosestNodesByKey(k, BUCKETSIZE);

		List<Node> $ = new ArrayList<Node>(result);

		if ($.size() > BUCKETSIZE)
			$.subList(BUCKETSIZE, $.size()).clear();

		return result;
	}

	@Override
	public void sendMessage(KadMessage msg) throws IOException {
		if (msg.getSrc().equals(localnode)) {
			return;
		}
		try {
			byte[] buf = msg.getBencodeData(localnode);
			channel.send(ByteBuffer.wrap(buf), msg.getSrc().getSocketAddress());
			buf = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void shutdown() {
		try {
			if (bootstrapNodesSaver != null) {
				bootstrapNodesSaver.saveNow();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		kadReceiveServer.shutdown();
		kadSendMsgServer.shutdown();
		kadParserTorrentServer.shutdown();
		starting = false;
	}

	@Override
	public void run() {
		try {
			create();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

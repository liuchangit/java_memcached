package com.liuchangit.memcached;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.liuchangit.comlib.log.Logger;
import com.liuchangit.memcached.cmd.Command;
import com.liuchangit.memcached.util.Configs;
import com.liuchangit.memcached.util.Loggers;

public class MemcachedServer {
	private static final Logger LOG = Loggers.SERVER;
	
	private final MemcachedServerSocket serverSocket;

	private boolean isServing;

	// Flag for stopping the server
	private volatile boolean stopped = true;

	private SelectThread selectThread;

	private MemcachedProcesser processer;
	
    private ExecutorService threadpool = null;

	public MemcachedServer(int port, int clientTimeout, int threads) throws Exception {
		this.processer = new MemcachedProcesser();
		this.serverSocket = new MemcachedServerSocket(port, clientTimeout);
		
	    LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
	    this.threadpool = new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, queue);
	}

	/**
	 * Begin accepting connections and processing commands.
	 */
	public void serve() {
		// start listening, or exit
		if (!startListening()) {
			return;
		}

		// start the selector, or exit
		if (!startSelectorThread()) {
			return;
		}

		setServing(true);

		// this will block while we serve
		joinSelector();
		
	    gracefullyShutdownThreadPool();

		setServing(false);

		// do a little cleanup
		stopListening();
	}

	public boolean isServing() {
		return isServing;
	}

	protected void setServing(boolean serving) {
		isServing = serving;
	}

	/**
	 * Have the server transport start accepting connections.
	 * 
	 * @return true if we started listening successfully, false if something
	 *         went wrong.
	 */
	protected boolean startListening() {
		try {
			serverSocket.listen();
			return true;
		} catch (Exception ex) {
			LOG.error("Failed to start listening on server socket!", ex);
			return false;
		}
	}

	/**
	 * Stop listening for connections.
	 */
	protected void stopListening() {
		serverSocket.close();
	}

	/**
	 * Start the selector thread running to deal with clients.
	 * 
	 * @return true if everything went ok, false if we couldn't start for some
	 *         reason.
	 */
	protected boolean startSelectorThread() {
		// start the selector
		try {
			selectThread = new SelectThread(serverSocket);
			stopped = false;
			selectThread.start();
			return true;
		} catch (IOException e) {
			LOG.error("Failed to start selector thread!", e);
			return false;
		}
	}

	/**
	 * Block until the selector exits.
	 */
	protected void joinSelector() {
		// wait until the selector thread exits
		try {
			selectThread.join();
		} catch (InterruptedException e) {
			// for now, just silently ignore. technically this means we'll have
			// less of
			// a graceful shutdown as a result.
		}
	}
	
	protected void gracefullyShutdownThreadPool() {
		// try to gracefully shut down the executor service
		threadpool.shutdown();

		// Loop until awaitTermination finally does return without a interrupted
		// exception. If we don't do this, then we'll shut down prematurely. We
		// want
		// to let the executorService clear it's task queue, closing client
		// sockets
		// appropriately.
		long timeoutMS = 10000;
		long now = System.currentTimeMillis();
		while (timeoutMS >= 0) {
			try {
				threadpool.awaitTermination(timeoutMS, TimeUnit.MILLISECONDS);
				break;
			} catch (InterruptedException ix) {
				long newnow = System.currentTimeMillis();
				timeoutMS -= (newnow - now);
				now = newnow;
			}
		}
	}

	/**
	 * Stop serving and shut everything down.
	 */
	public void stop() {
		stopped = true;
		if (selectThread != null) {
			selectThread.wakeupSelector();
		}
	}

	public boolean isStopped() {
		return selectThread.isStopped();
	}
	
	public boolean processCommand(Connection connection) {
		try {
			Runnable task = new Task(connection);
			threadpool.execute(task);
			return true;
		} catch (RejectedExecutionException rx) {
			LOG.warn("ExecutorService rejected execution!", rx);
			return false;
		}
	}
	
	private class Task implements Runnable {
		private Connection connection;

		public Task(final Connection connection) {
			this.connection = connection;
		}

		public void run() {
			MessageBuffer msgBuffer = connection.msgBuffer;
			try {
				byte[] result = processer.process(msgBuffer.getCmd(),
						msgBuffer.getArgs(), msgBuffer.getData());
				connection.setResponseBuffer(result);
			} catch (Exception e) {
				byte[] result = ErrorName.SERVER_ERROR.getBytes(e.getMessage());
				connection.setResponseBuffer(result);
			}
		}
	}
	
	public static interface Stats {
		public int getCompletionQueueSize();
	}
	
	public Stats getStats() {
		return new Stats() {
			
			@Override
			public int getCompletionQueueSize() {
				return MemcachedServer.this.selectThread == null ? -1 : MemcachedServer.this.selectThread.completeMessages;
			}
		};
	}

	/**
	 * The thread that will be doing all the selecting, managing new connections
	 * and those that still need to be read.
	 */
	protected class SelectThread extends Thread {

		private final MemcachedServerSocket serverSocket;
		private final Selector selector;

		// List of Connections that want to change their selection interests.
		private final ConcurrentHashMap<Connection, Connection> selectInterestChanges = new ConcurrentHashMap<Connection, Connection>();
		
		private volatile int completeMessages = 0;

		/**
		 * Set up the SelectorThread.
		 */
		public SelectThread(final MemcachedServerSocket serverSocket)
				throws IOException {
			this.serverSocket = serverSocket;
			this.selector = SelectorProvider.provider().openSelector();
			serverSocket.registerSelector(selector);
			setName("MemcachedServer Selector");
		}

		public boolean isStopped() {
			return stopped;
		}

		/**
		 * The work loop. Handles both selecting (all IO operations) and
		 * managing the selection preferences of all existing connections.
		 */
		public void run() {
			try {
				while (!stopped) {
					select();
					processInterestChanges();
				}
			} catch (Throwable t) {
				LOG.error("run() exiting due to uncaught error", t);
				try {
					System.err.println("system exit due to fatal error");
					Thread.sleep(1000);
					System.exit(1);		//exit
				} catch (Throwable th) {}
			} finally {
				stopped = true;
			}
		}

		/**
		 * If the selector is blocked, wake it up.
		 */
		public void wakeupSelector() {
			selector.wakeup();
		}

		/**
		 * Add Connection to the list of select interest changes and wake up
		 * the selector if it's blocked. When the select() call exits, it'll
		 * give the Connection a chance to change its interests.
		 */
		public void requestSelectInterestChange(Connection conn) {
			selectInterestChanges.putIfAbsent(conn, conn);
			// wakeup the selector, if it's currently blocked.
			selector.wakeup();
		}

		/**
		 * Check to see if there are any Connections that have switched their
		 * interest type of write.
		 */
		private void processInterestChanges() {
			try {
				Iterator<Connection> iter = selectInterestChanges.keySet().iterator();
				int completeMessages = 0;
				for (; iter.hasNext(); ) {
					Connection conn = iter.next();
					iter.remove();
					int count = conn.completes.getAndSet(0);
					completeMessages += count;
					conn.changeSelectInterests();
				}
				this.completeMessages = completeMessages;
			} catch (Exception e) {
				LOG.warn("Got an Exception while processInterestChanges!", e);
			}
		}

		/**
		 * Select and process IO events appropriately: If there are connections
		 * to be accepted, accept them. If there are existing connections with
		 * data waiting to be read, read it, buffering until a whole frame has
		 * been read. If there are any pending responses, buffer them until
		 * their target client is available, and then send the data.
		 */
		private void select() {
			try {
				// wait for io events.
				selector.select();

				// process the io events we received
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				Iterator<SelectionKey> iter = selectedKeys.iterator();
				while (!stopped && iter.hasNext()) {
					SelectionKey key = iter.next();
					iter.remove();

					// skip if not valid
					if (!key.isValid()) {
						cleanupSelectionkey(key);
						continue;
					}

					// if the key is marked Accept, then it has to be the server
					// transport.
					if (key.isAcceptable()) {
						handleAccept();
					} else if (key.isReadable()) {
						// deal with reads
						handleRead(key);
					} else if (key.isWritable()) {
						// deal with writes
						handleWrite(key);
					} else {
						LOG.warn("Unexpected state in select! "
								+ key.interestOps());
					}
				}
			} catch (Exception e) {
				LOG.warn("Got an Exception while selecting!", e);
			}
		}

		/**
		 * Accept a new connection.
		 */
		private void handleAccept() throws IOException {
			SelectionKey clientKey = null;
			MemcachedClientSocket client = null;
			try {
				// accept the connection
				client = (MemcachedClientSocket) serverSocket.accept();
				clientKey = client.registerSelector(selector,
						SelectionKey.OP_READ);

				// add this key to the map
				Connection conn = new Connection(client, clientKey);
				clientKey.attach(conn);
			} catch (Exception tte) {
				// something went wrong accepting.
				LOG.warn("Exception trying to accept!", tte);
				tte.printStackTrace();
				if (clientKey != null) {
					cleanupSelectionkey(clientKey);
				}
				if (client != null) {
					client.close();
				}
			}
		}

		/**
		 * Do the work required to read from a readable client. 
		 */
		private void handleRead(SelectionKey key) {
			Connection conn = (Connection) key.attachment();
			try {
				conn.readRequest();
			} catch (Exception e) {
				conn.close();
			}
		}

		/**
		 * Let a writable client get written, if there's data to be written.
		 */
		private void handleWrite(SelectionKey key) {
			Connection conn = (Connection) key.attachment();
			try {
				conn.writeResponse();
			} catch (Exception e) {
				LOG.warn(getName() + " write " + conn + " failed: " + e.getMessage());
				conn.close();
			}
		}

	    /**
	     * Do connection-close cleanup on a given SelectionKey.
	     */
	    private void cleanupSelectionkey(SelectionKey key) {
	    	Connection conn = (Connection)key.attachment();
	      if (conn != null) {
	        conn.close();
	      }
	      key.cancel();
	    }

	} // SelectorThread
	
	class Connection {

		// the actual socket hooked up to the client.
		private final MemcachedClientSocket socket;
		private final String name;

		// the SelectionKey that corresponds to our transport
		private final SelectionKey selectionKey;
		
		private AtomicInteger completes = new AtomicInteger(0);

		private final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
		private MessageBuffer msgBuffer = new MessageBuffer();
		private volatile ByteBuffer respBuffer;

		public static final int READING = 1;
		public static final int READ_COMPLETE = 2;
		public static final int WAITING_REGISTER_WRITE = 3;
		public static final int WRITING = 4;
		public static final int WAITING_REGISTER_READ = 5;
		public static final int WAITING_CLOSE = 6;
		
		private volatile int status = READING;
		
		Connection(final MemcachedClientSocket socket,
				final SelectionKey selectionKey) {
			this.socket = socket;
			this.selectionKey = selectionKey;
			String name = "Client Connection ";
			try {
				name += socket.getSocketChannel().socket().getRemoteSocketAddress();
			} catch (Exception e) {
				name += socket.getSocketChannel();
			}
			this.name = name;
		}
		
		//invoked by select thread
		void readRequest() throws Exception {
			int len = socket.read(byteBuffer);
			while (len > 0) {
				byteBuffer.flip();
				msgBuffer.append(byteBuffer);
				byteBuffer.clear();

				if (msgBuffer.isFullyRead()) {
					status = READ_COMPLETE;
					selectionKey.interestOps(selectionKey.interestOps() & (~SelectionKey.OP_READ));
					processRequest();
					return;
				}
				
				len = socket.read(byteBuffer);
			}
			if (len < 0) {
				throw new Exception("EOF");
			}
		}
		
		public void setResponseBuffer(byte[] resp) {
			this.respBuffer = ByteBuffer.wrap(resp);
			status = WAITING_REGISTER_WRITE;
			selectThread.requestSelectInterestChange(this);
		}

		//invoked by select thread
		void writeResponse() throws Exception {
			if (respBuffer.hasRemaining()) {
				socket.write(respBuffer);
			}
    		if (!respBuffer.hasRemaining()) {	//fully written
    			completeRequest();
    		}
		}
		
		private void processRequest() {
			if (msgBuffer.getError() != null) {
				byte[] result = msgBuffer.getError().getBytes(msgBuffer.msg);
				setResponseBuffer(result);
			} else {
				if (!processCommand(this)) {
					close();
				}
			}
		}
		
		private void completeRequest() {
			respBuffer = null;
			msgBuffer = new MessageBuffer();
			status = WAITING_REGISTER_READ;
			changeSelectInterests();
			completes.incrementAndGet();
		}
		
		public void changeSelectInterests() {
			if (status == WAITING_REGISTER_WRITE) {
				selectionKey.interestOps(SelectionKey.OP_WRITE);
				status = WRITING;
			} else if (status == WAITING_REGISTER_READ) {
				selectionKey.interestOps(SelectionKey.OP_READ);
				status = READING;
			} else if (status == WAITING_CLOSE) {
				close();
			} else {
				LOG.error("changeSelectInterest was called, but state is invalid (" + status + ")");
			}
		}
		
		public void needClose() {
			status = WAITING_CLOSE;
			selectThread.requestSelectInterestChange(this);
		}

		void close() {
			try {
				socket.close();
				selectionKey.cancel();
			} catch (Exception ex) {
				LOG.warn("error closing " + this, ex);
			}
		}
		
		public String toString() {
			return this.name;
		}
	}
	
	public static class MessageBuffer {
		private ByteArrayOutputStream buffos;
		
		private boolean headRead = false;
		private boolean dataRead = false;
		private boolean eol = false;
		private String cmd;
		private String[] args;
		private int valueLength = 0;
		private int dataLength = 0;
		private byte[] data;
		private ErrorName error = null;
		private String msg;
		
		MessageBuffer() {
			this.buffos = new ByteArrayOutputStream();
		}

		public void append(ByteBuffer buff) {
			while (buff.hasRemaining()) {
				byte b = buff.get();
				if (b == 13) {		//read CR
					if (eol) {
						write((byte)13);
					}
					eol = true;
				} else if (b == 10) {	//read LF
					if (eol) {
						linebreak();
						this.eol = false;
					} else {
						write(b);
					}
				} else {	//read other char
					if (eol) {
						write((byte)13, b);
						this.eol = false;
					} else {
						write(b);
					}
				}
			}
		}
		
		private void write(byte... bytes) {
			try {
				this.buffos.write(bytes);
				if (this.dataLength < this.valueLength) {
					this.dataLength += bytes.length;
				}
			} catch (Exception e) {
				//OOM
				error = ErrorName.SERVER_ERROR;
				msg = e.getMessage();
			}
		}
		
		private void linebreak() {
			if (!this.headRead) {
				headRead();
				this.headRead = true;
			} else {
				if (this.dataLength < this.valueLength) {
					write((byte)13, (byte)10);
				} else {
					dataRead();
					this.dataRead = true;
				}
			}
		}
		
		private void headRead() {
			String head = buffos.toString();
			int space = head.indexOf(" ");
			if (space > 0) {
				this.cmd = head.substring(0, space);
				String arg = head.substring(space + 1);
				if (!Command.GET.isMe(this.cmd)
						&& !Command.SET.isMe(this.cmd)
						&& !Command.DELETE.isMe(this.cmd)) {
					// ERROR
					error = ErrorName.ERROR;
				}
				if (arg.trim().length() > 0) {
					this.args = arg.split(" ");
					String key = args[0];
					if (key.getBytes().length > Configs.MAX_KEY_LENGTH) {
						error = ErrorName.CLIENT_ERROR;
						msg = "key length is over MAX_KEY_LENGTH:" + Configs.MAX_KEY_LENGTH;
					} else {
						if (Command.SET.name().toLowerCase().equals(this.cmd)) {
							if (this.args.length < 4) {
								//CLIENT_ERROR
								error = ErrorName.CLIENT_ERROR;
								msg = "set command args error";
							} else {
								try {
									int flags = Integer.parseInt(args[1]);
									int expire = Integer.parseInt(args[2]);
									this.valueLength = Integer.parseInt(args[3]);
									if (this.valueLength > Configs.MAX_VALUE_LENGTH) {
										error = ErrorName.CLIENT_ERROR;
										msg = "key length is over MAX_VALUE_LENGTH:" + Configs.MAX_VALUE_LENGTH;
									}
								} catch (Exception e) {
									error = ErrorName.CLIENT_ERROR;
									msg = "set command args error";
								}
							}
						}
					}
				} else {
					//CLIENT_ERROR
					error = ErrorName.CLIENT_ERROR;
					msg = this.cmd + " command args error";
				}
			} else {
				//CLIENT_ERROR
				error = ErrorName.CLIENT_ERROR;
				msg = "command error";
			}
			buffos.reset();
		}
		
		private void dataRead() {
			this.data = buffos.toByteArray();
			if (this.valueLength != this.data.length) {
				error = ErrorName.SERVER_ERROR;
				msg = "value length error";
			}
		}

		public boolean isHeadRead() {
			return headRead;
		}

		public boolean isDataRead() {
			return dataRead;
		}
		
		public boolean isFullyRead() {
			return (this.valueLength == 0 && isHeadRead()) || (this.valueLength > 0 && isDataRead());
		}

		public String getCmd() {
			return cmd;
		}

		public String[] getArgs() {
			return args;
		}

		public int getValueLength() {
			return valueLength;
		}

		public byte[] getData() {
			return data;
		}
		
		public ErrorName getError() {
			return this.error;
		}
	}

}

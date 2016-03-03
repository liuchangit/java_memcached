package com.liuchangit.memcached;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.liuchangit.comlib.log.Logger;
import com.liuchangit.memcached.util.Loggers;

/**
 * Transport for use with async client.
 */
public class MemcachedClientSocket {

  private static final Logger LOGGER = Loggers.SERVER;

  /**
   * Host and port if passed in, used for lazy non-blocking connect.
   */
  private final SocketAddress socketAddress;

  private final SocketChannel socketChannel;

  public MemcachedClientSocket(String host, int port) throws IOException {
    this(host, port, 0);
  }

  /**
   * Create a new nonblocking socket transport that will be connected to host:port.
   * @param host
   * @param port
   * @throws TTransportException
   * @throws IOException
   */
  public MemcachedClientSocket(String host, int port, int timeout) throws IOException {
    this(SocketChannel.open(), timeout, new InetSocketAddress(host, port));
  }

  /**
   * Constructor that takes an already created socket.
   *
   * @param socketChannel Already created SocketChannel object
   * @throws IOException if there is an error setting up the streams
   */
  public MemcachedClientSocket(SocketChannel socketChannel) throws IOException {
    this(socketChannel, 0, null);
    if (!socketChannel.isConnected()) throw new IOException("Socket must already be connected");
  }

  private MemcachedClientSocket(SocketChannel socketChannel, int timeout, SocketAddress socketAddress)
      throws IOException {
    this.socketChannel = socketChannel;
    this.socketAddress = socketAddress;

    // make it a nonblocking channel
    socketChannel.configureBlocking(false);

    // set options
    Socket socket = socketChannel.socket();
    socket.setSoLinger(false, 0);
    socket.setTcpNoDelay(true);
    setTimeout(timeout);
  }

  /**
   * Register the new SocketChannel with our Selector, indicating
   * we'd like to be notified when it's ready for I/O.
   *
   * @param selector
   * @return the selection key for this socket.
   */
  public SelectionKey registerSelector(Selector selector, int interests) throws IOException {
    return socketChannel.register(selector, interests);
  }

  /**
   * Sets the socket timeout, although this implementation never uses blocking operations so it is unused.
   *
   * @param timeout Milliseconds timeout
   */
  public void setTimeout(int timeout) {
    try {
      socketChannel.socket().setSoTimeout(timeout);
    } catch (SocketException sx) {
      LOGGER.warn("Could not set socket timeout.", sx);
    }
  }

  /**
   * Returns a reference to the underlying SocketChannel.
   */
  public SocketChannel getSocketChannel() {
    return socketChannel;
  }

  /**
   * Checks whether the socket is connected.
   */
  public boolean isOpen() {
    // isConnected() does not return false after close(), but isOpen() does
    return socketChannel.isOpen() && socketChannel.isConnected();
  }

  /**
   * Perform a nonblocking read into buffer.
   */
  public int read(ByteBuffer buffer) throws IOException {
    return socketChannel.read(buffer);
  }


  /**
   * Reads from the underlying input stream if not null.
   */
  public int read(byte[] buf, int off, int len) throws IOException {
    if ((socketChannel.validOps() & SelectionKey.OP_READ) != SelectionKey.OP_READ) {
      throw new IOException("Cannot read from write-only socket channel");
    }
    try {
      return socketChannel.read(ByteBuffer.wrap(buf, off, len));
    } catch (IOException iox) {
      throw iox;
    }
  }

  /**
   * Perform a nonblocking write of the data in buffer;
   */
  public int write(ByteBuffer buffer) throws IOException {
    return socketChannel.write(buffer);
  }

  /**
   * Writes to the underlying output stream if not null.
   */
  public void write(byte[] buf, int off, int len) throws IOException {
    if ((socketChannel.validOps() & SelectionKey.OP_WRITE) != SelectionKey.OP_WRITE) {
      throw new IOException("Cannot write to write-only socket channel");
    }
    try {
      socketChannel.write(ByteBuffer.wrap(buf, off, len));
    } catch (IOException iox) {
      throw iox;
    }
  }

  /**
   * Closes the socket.
   */
  public void close() {
    try {
      socketChannel.close();
    } catch (IOException iox) {
      LOGGER.warn("Could not close socket.", iox);
    }
  }

  public boolean startConnect() throws IOException {
    return socketChannel.connect(socketAddress);
  }

  public boolean finishConnect() throws IOException {
    return socketChannel.finishConnect();
  }

}

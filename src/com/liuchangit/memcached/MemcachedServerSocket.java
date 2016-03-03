package com.liuchangit.memcached;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.liuchangit.comlib.log.Logger;
import com.liuchangit.memcached.util.Loggers;

/**
 * Wrapper around ServerSocketChannel
 */
public class MemcachedServerSocket {
  private static final Logger LOGGER = Loggers.SERVER;

  /**
   * This channel is where all the nonblocking magic happens.
   */
  private ServerSocketChannel serverSocketChannel = null;

  /**
   * Underlying ServerSocket object
   */
  private ServerSocket serverSocket = null;

  /**
   * Timeout for client sockets from accept
   */
  private int clientTimeout = 0;

  /**
   * Creates just a port listening server socket
   */
  public MemcachedServerSocket(int port) throws IOException {
    this(port, 0);
  }

  /**
   * Creates just a port listening server socket
   */
  public MemcachedServerSocket(int port, int clientTimeout) throws IOException {
    this(new InetSocketAddress(port), clientTimeout);
  }

  public MemcachedServerSocket(InetSocketAddress bindAddr) throws IOException {
    this(bindAddr, 0);
  }

  public MemcachedServerSocket(InetSocketAddress bindAddr, int clientTimeout) throws IOException {
    this.clientTimeout = clientTimeout;
    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);

      // Make server socket
      serverSocket = serverSocketChannel.socket();
      // Prevent 2MSL delay problem on server restarts
      serverSocket.setReuseAddress(true);
      // Bind to listening port
      serverSocket.bind(bindAddr);
    } catch (IOException ioe) {
      serverSocket = null;
      throw new IOException("Could not create ServerSocket on address " + bindAddr.toString() + ".");
    }
  }

  public void listen() throws Exception {
    // Make sure not to block on accept
    if (serverSocket != null) {
      try {
        serverSocket.setSoTimeout(0);
      } catch (SocketException sx) {
        sx.printStackTrace();
      }
    }
  }

  protected MemcachedClientSocket accept() throws IOException {
    if (serverSocket == null) {
      throw new IOException("No underlying server socket.");
    }
    try {
      SocketChannel socketChannel = serverSocketChannel.accept();
      if (socketChannel == null) {
        return null;
      }

      MemcachedClientSocket tsocket = new MemcachedClientSocket(socketChannel);
      tsocket.setTimeout(clientTimeout);
      return tsocket;
    } catch (IOException iox) {
      throw iox;
    }
  }

  public void registerSelector(Selector selector) {
    try {
      // Register the server socket channel, indicating an interest in
      // accepting new connections
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    } catch (ClosedChannelException e) {
      // this shouldn't happen, ideally...
      // TODO: decide what to do with this.
    }
  }

  public void close() {
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException iox) {
        LOGGER.warn("WARNING: Could not close server socket: " + iox.getMessage());
      }
      serverSocket = null;
    }
  }

  public void interrupt() {
    // The thread-safeness of this is dubious, but Java documentation suggests
    // that it is safe to do this from a different thread context
    close();
  }

}

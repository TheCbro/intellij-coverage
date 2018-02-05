/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.rt.coverage.data;

import com.intellij.rt.coverage.util.CoverageIOUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class SocketTestDiscoveryDataListener implements TestDiscoveryDataListener {
  private static final int SOCKET_BUFFER_SIZE = 16 * 1024;
  @SuppressWarnings("WeakerAccess")
  public static final String HOST_PROP = "test.discovery.data.host";
  @SuppressWarnings("WeakerAccess")
  public static final String PORT_PROP = "test.discovery.data.port";

  private static final byte START_MSG = 0;
  private static final byte FINISHED_MSG = 1;
  private static final byte TEST_FINISHED_MSG = 2;
  private static final byte VERSION = 1;
  private final SocketChannel mySocket;
  private final ExecutorService myExecutor;
  private final Selector mySelector;
  private volatile boolean myClosed;
  private final BlockingQueue<ByteBuffer> myData = new ArrayBlockingQueue<ByteBuffer>(10);

  public SocketTestDiscoveryDataListener() throws IOException {
    String host = System.getProperty(HOST_PROP, "127.0.0.1");
    int port = Integer.parseInt(System.getProperty(PORT_PROP));
    mySelector = Selector.open();
    mySocket = SocketChannel.open(new InetSocketAddress(host, port));
    mySocket.configureBlocking(false);
    myExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName("TestDiscoveryDataWriter");
        return thread;
      }
    });
    mySocket.socket().setSendBufferSize(SOCKET_BUFFER_SIZE); // mySocket.setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_SIZE);
    mySocket.register(mySelector, SelectionKey.OP_WRITE);
    myExecutor.submit(new Runnable() {
      public void run() {
        while (!myClosed || !myData.isEmpty()) {
          ByteBuffer data = myData.peek();
          if (data == null) {
            continue;
          }

          try {
            sendDataPart(data);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          if (!data.hasRemaining()) {
            myData.poll();
          }
        }
      }

      private void sendDataPart(ByteBuffer data) throws IOException {
        while (true) {
          int selected = mySelector.select();
          if (selected != 0) {
            Iterator<SelectionKey> keyIt = mySelector.selectedKeys().iterator();
            while (keyIt.hasNext()) {
              SelectionKey key = keyIt.next();
              SocketChannel channel = (SocketChannel) key.channel();
              keyIt.remove();
              if (key.isValid() && key.isWritable()) {
                try {
                  if (channel.write(data) == -1) {
                    throw new IOException("Connection is closed");
                  }
                } catch (IOException e) {
                  key.cancel();
                  throw e;
                }
                return;
              }
            }

          }
        }
      }
    });

    write(ByteBuffer.wrap(new byte[]{START_MSG, VERSION}));
  }

  public void testFinished(final String testName, Map<String, boolean[]> classToVisitedMethods, Map<String, String[]> classToMethodNames) {
    final List<VisitedMethods> visitedMethods = new ArrayList<VisitedMethods>();
    String[] methodNames = null;
    for (Map.Entry<String, boolean[]> e : classToVisitedMethods.entrySet()) {
      VisitedMethods currentMethods = null;
      boolean[] markers = e.getValue();
      for (int i = 0; i < markers.length; i++) {
        boolean marker = markers[i];
        if (marker) {
          if (currentMethods == null) {
            currentMethods = new VisitedMethods(e.getKey());
            methodNames = classToMethodNames.get(e.getKey());
            visitedMethods.add(currentMethods);
          }
          currentMethods.methodNames.add(methodNames[i]);
        }
      }
    }

    try {
      final MyByteArrayOutputStream baos = new MyByteArrayOutputStream();
      baos.write(TEST_FINISHED_MSG);
      DataOutputStream dos = new DataOutputStream(baos);
      CoverageIOUtil.writeUTF(dos, testName);

      CoverageIOUtil.writeINT(dos, visitedMethods.size());
      for (VisitedMethods ns : visitedMethods) {
        CoverageIOUtil.writeUTF(dos, ns.className);
        CoverageIOUtil.writeINT(dos, ns.methodNames.size());
        for (String name : ns.methodNames) {
          CoverageIOUtil.writeUTF(dos, name);
        }
      }

      write(baos.asByteBuffer());
    } catch (IOException ignored) {
    }
  }

  public void testsFinished() {
    write(ByteBuffer.wrap(new byte[]{FINISHED_MSG}));
    myClosed = true;
    myExecutor.shutdown();

    try {
      if (!myExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
        shutdownNow();
        if (!myExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
          System.err.println("Socket worker didn't finished properly");
        }
      }
    } catch (InterruptedException ie) {
      shutdownNow();
      Thread.currentThread().interrupt();
    }

    try {
      mySocket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void shutdownNow() {
    for (Runnable task : myExecutor.shutdownNow()) {
      task.run();
    }
  }

  private void write(final ByteBuffer data) {
    try {
      myData.put(data);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyByteArrayOutputStream extends ByteArrayOutputStream {
    ByteBuffer asByteBuffer() {
      return ByteBuffer.wrap(buf, 0, count);
    }
  }

  private static class VisitedMethods {
    private final String className;
    private final List<String> methodNames = new ArrayList<String>(1);

    private VisitedMethods(String className) {
      this.className = className;
    }
  }
}

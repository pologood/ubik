package org.sapia.ubik.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

public class MinaByteBufferOutputStreamTest {

  @Test
  public void testWriteInt() throws IOException {
    IoBuffer buf = IoBuffer.allocate(10);
    MinaByteBufferOutputStream os = new MinaByteBufferOutputStream(buf);
    os.write(1);
    buf.flip();
    int i = buf.get();
    assertEquals(1, i);
  }

  @Test
  public void testWriteByteArray() throws IOException {
    IoBuffer buf = IoBuffer.allocate(10);
    MinaByteBufferOutputStream os = new MinaByteBufferOutputStream(buf);
    byte[] bytes = new byte[] { (byte) 1, (byte) 2, (byte) 3 };
    os.write(bytes);
    buf.flip();
    byte[] read = new byte[3];
    buf.get(read);
    assertEquals(1, read[0]);
    assertEquals(2, read[1]);
    assertEquals(3, read[2]);
  }

  @Test
  public void testWriteByteArrayWithStartAndOffset() throws IOException {
    IoBuffer buf = IoBuffer.allocate(10);
    MinaByteBufferOutputStream os = new MinaByteBufferOutputStream(buf);
    byte[] bytes = new byte[] { (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, };
    os.write(bytes);
    buf.flip();
    byte[] read = new byte[3];
    buf.get(read, 0, 3);
    assertEquals(1, read[0]);
    assertEquals(2, read[1]);
    assertEquals(3, read[2]);
  }

}

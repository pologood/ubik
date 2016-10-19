package org.sapia.ubik.mcast.avis.io;

import java.nio.BufferUnderflowException;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolCodecException;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.sapia.ubik.mcast.avis.io.messages.ErrorMessage;
import org.sapia.ubik.mcast.avis.io.messages.Message;
import org.sapia.ubik.mcast.avis.io.messages.XidMessage;

/**
 * Base class for Elvin XDR frame codecs. Reads/writes messages
 * to/from the Elvin XDR frame format with the help of
 * {@link Message#decode(IoBuffer)} and
 * {@link Message#encode(IoBuffer)}. Understood message sets are
 * effectively defined by the subclasses' implementation of
 * {@link #newMessage(int, int)}.
 * 
 * @author Matthew Phillips
 */
public abstract class FrameCodec
  extends CumulativeProtocolDecoder implements ProtocolEncoder
{
  public void encode (IoSession session, Object messageObject,
                      ProtocolEncoderOutput out)
    throws Exception
  {
    // buffer is auto deallocated
    IoBuffer buffer = IoBuffer.allocate (4096); 
    buffer.setAutoExpand (true);
    
    // leave room for frame size
    buffer.position (4);
    
    Message message = (Message)messageObject;
  
    // write frame type
    buffer.putInt (message.typeId ());
    
    message.encode (buffer);
  
    int frameSize = buffer.position () - 4;
    
    // write frame size
    buffer.putInt (0, frameSize);
    
    // if (isEnabled (TRACE) && buffer.limit () <= MAX_BUFFER_DUMP)
    //  trace ("Codec output: " + buffer.getHexDump (), this);
    
    // sanity check frame is 4-byte aligned
    if (frameSize % 4 != 0)
      throw new ProtocolCodecException
        ("Frame length not 4 byte aligned for " + message.getClass ());
    
    int maxLength = maxFrameLengthFor (session);
    
    if (frameSize <= maxLength)
    {
      // write out whole frame
      buffer.flip ();
      out.write (buffer);
    } else
    {
      throw new FrameTooLargeException (maxLength, frameSize);
    }
  }

  @Override
  protected boolean doDecode (IoSession session, IoBuffer in,
                              ProtocolDecoderOutput out)
    throws Exception
  {
    // if (isEnabled (TRACE) && in.limit () <= MAX_BUFFER_DUMP)
    //  trace ("Codec input: " + in.getHexDump (), this);
    
    // if in protocol violation mode, do not try to read any further
    if (session.getAttribute ("protocolViolation") != null)
      return false;
    
    if (!haveFullFrame (session, in))
      return false;
    
    int maxLength = maxFrameLengthFor (session);
    
    int frameSize = in.getInt ();
    int dataStart = in.position ();
  
    Message message = null;
    
    try
    {
      int messageType = in.getInt ();
      
      message = newMessage (messageType, frameSize);
    
      if (frameSize % 4 != 0)
        throw new ProtocolCodecException ("Frame length not 4 byte aligned");
      
      if (frameSize > maxLength)
        throw new FrameTooLargeException (maxLength, frameSize);
      
      message.decode (in);
      
      int bytesRead = in.position () - dataStart;
      
      if (bytesRead != frameSize)
      {
        throw new ProtocolCodecException 
          ("Some input not read for " + message.name () + ": " +
           "Frame header said " + frameSize + 
           " bytes, but only read " + bytesRead);
      }
      
      out.write (message);
    
      return true;
    } catch (Exception ex)
    {
      if (ex instanceof ProtocolCodecException ||
          ex instanceof BufferUnderflowException ||
          ex instanceof FrameTooLargeException)
      {
        /*
         * Mark session in violation and handle once: codec will only
         * generate one error message, it's up to consumer to try to
         * recover or close connection.
         */
        session.setAttribute ("protocolViolation");
        session.suspendRead ();
        
        ErrorMessage error = new ErrorMessage (ex, message); 
        
        // fill in XID if possible
        if (message instanceof XidMessage && in.limit () >= 12)
        {
          int xid = in.getInt (8);
          
          if (xid > 0)
            ((XidMessage)message).xid = xid;
        }

        out.write (error);
        
        return true;
      } else
      {
        throw (RuntimeException)ex;
      }
    }
  }

  /**
   * Create a new instance of a message given a message type code and
   * frame length.
   */
  protected abstract Message newMessage (int messageType, int frameSize)
    throws ProtocolCodecException;
  
  private static boolean haveFullFrame (IoSession session, IoBuffer in)
  {
    // need frame size and type before we do anything
    if (in.remaining () < 8)
      return false;
    
    boolean haveFrame;
    int start = in.position ();
    
    int frameSize = in.getInt ();
    
    if (frameSize > maxFrameLengthFor (session))
    {
      // when frame too big, OK it and let doDecode () generate error
      haveFrame = true;
    } else if (in.remaining () < frameSize)
    {
      if (in.capacity () < frameSize + 4)
      {
        // need to save and restore limit
        int limit = in.limit ();
        
        in.expand (frameSize);
      
        in.limit (limit);
      }
      
      haveFrame = false;
    } else
    {
      haveFrame = true;
    }
  
    in.position (start);
    
    return haveFrame;
  }

  @Override
  public void finishDecode (IoSession session, ProtocolDecoderOutput out)
    throws Exception
  {
    // zip
  }

  public static void setMaxFrameLengthFor (IoSession session, int length)
  {
    session.setAttribute ("maxFrameLength", length);
  }

  private static int maxFrameLengthFor (IoSession session)
  {
    Integer length = (Integer)session.getAttribute ("maxFrameLength");
    
    if (length == null)
      return Integer.MAX_VALUE;
    else
      return length;
  }
}
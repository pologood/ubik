package org.sapia.ubik.mcast.avis.io.messages;

import static org.sapia.ubik.mcast.avis.io.XdrCoding.getString;
import static org.sapia.ubik.mcast.avis.io.XdrCoding.putString;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.filter.codec.ProtocolCodecException;

public class Disconn extends Message
{
  public static final int ID = 53;

  public static final int REASON_SHUTDOWN = 1;
  public static final int REASON_SHUTDOWN_REDIRECT = 2;
  public static final int REASON_PROTOCOL_VIOLATION = 4;
  
  public int reason;
  public String args;
  
  public Disconn ()
  {
    this (-1, "");
  }
  
  public Disconn (int reason)
  {
    this (reason, "");
  }
  
  public Disconn (int reason, String args)
  {
    this.reason = reason;
    this.args = args;
  }

  @Override
  public int typeId ()
  {
    return ID;
  }
  
  public boolean hasArgs ()
  {
    return args.length () > 0;
  }
  
  @Override
  public void decode (IoBuffer in)
    throws ProtocolCodecException
  {
    reason = in.getInt ();
    args = getString (in);
  }

  @Override
  public void encode (IoBuffer out)
    throws ProtocolCodecException
  {
    if (reason == -1)
      throw new ProtocolCodecException ("Reason not set");
    
    out.putInt (reason);
    putString (out, args);
  }
}

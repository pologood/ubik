package org.sapia.ubik.mcast.avis.client;

import java.util.Map;

import static org.sapia.ubik.mcast.avis.util.Text.mapToString;

import java.io.IOException;

/**
 * Thrown when the Elvin client receives a rejection of one or more
 * requested connection options.
 * 
 * @author Matthew Phillips
 */
public class ConnectionOptionsException extends IOException
{
  /** The requested options */
  public final ConnectionOptions options;
  /** The rejected options and the actual value that the server will use. */
  public final Map<String, Object> rejectedOptions;

  ConnectionOptionsException (ConnectionOptions options,
                                     Map<String, Object> rejectedOptions)
  {
    super ("Router rejected connection options: " +
            "rejected options and actual values: " +
            mapToString (rejectedOptions));
    
    this.options = options;
    this.rejectedOptions = rejectedOptions;
  }
}

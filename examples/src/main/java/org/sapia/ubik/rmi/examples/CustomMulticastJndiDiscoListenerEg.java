package org.sapia.ubik.rmi.examples;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.sapia.ubik.rmi.naming.remote.JNDIConsts;
import org.sapia.ubik.rmi.naming.remote.RemoteInitialContextFactory;
import org.sapia.ubik.rmi.naming.remote.discovery.JndiDiscoListener;
import org.sapia.ubik.rmi.naming.remote.proxy.ReliableLocalContext;


/**
 * @author Yanick Duchesne
 * <dl>
 * <dt><b>Copyright:</b><dd>Copyright &#169; 2002-2003 <a href="http://www.sapia-oss.org">Sapia Open Source Software</a>. All Rights Reserved.</dd></dt>
 * <dt><b>License:</b><dd>Read the license.txt file of the jar or visit the
 *        <a href="http://www.sapia-oss.org/license.html">license page</a> at the Sapia OSS web site</dd></dt>
 * </dl>
 */
public class CustomMulticastJndiDiscoListenerEg implements JndiDiscoListener {
  /**
   * Constructor for ServiceDiscoListenerExample.
   */
  public CustomMulticastJndiDiscoListenerEg() {
    super();
  }

  /**
   * @see org.sapia.ubik.rmi.naming.remote.discovery.JndiDiscoListener#onJndiDiscovered(Context)
   */
  public void onJndiDiscovered(Context ctx) {
    System.out.println("JNDI discovered!!!!");
  }

  public static void main(String[] args) {
    try {
      Properties props = new Properties();
      props.setProperty(InitialContext.PROVIDER_URL, "ubik://localhost:1099");
      props.setProperty(InitialContext.INITIAL_CONTEXT_FACTORY, RemoteInitialContextFactory.class.getName());
      props.setProperty(JNDIConsts.MCAST_ADDR_KEY, CustomMulticastJndiRunner.CUSTOM_MCAST_ADDRESS);
      props.setProperty(JNDIConsts.MCAST_PORT_KEY, Integer.toString(CustomMulticastJndiRunner.CUSTOM_MCAST_PORT));

      InitialContext       ctx = new InitialContext(props);

      CustomMulticastJndiDiscoListenerEg  sdisco = new CustomMulticastJndiDiscoListenerEg();

      ReliableLocalContext rctx = ReliableLocalContext.currentContext();
      rctx.addJndiDiscoListener(sdisco);

      while (true) {
        Thread.sleep(100000);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

package org.sapia.ubik.mcast.tcp.mina;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.sapia.ubik.mcast.DispatcherFactory;
import org.sapia.ubik.mcast.EventConsumer;
import org.sapia.ubik.mcast.UnicastDispatcher;
import org.sapia.ubik.mcast.testing.UnicastDispatcherTestSupport;
import org.sapia.ubik.rmi.Consts;
import org.sapia.ubik.util.Conf;

public class MinaUnicastDispatcherTest extends UnicastDispatcherTestSupport {

  @Test
  public void testLoadDispatcher() {
    UnicastDispatcher dispatcher = DispatcherFactory.loadUnicastDispatcher(Conf.newInstance().addProperties(Consts.UNICAST_PROVIDER, Consts.UNICAST_PROVIDER_TCP_NIO));
    assertTrue(dispatcher instanceof MinaTcpUnicastDispatcher);
  }
  
  @Override
  protected UnicastDispatcher createUnicastDispatcher(EventConsumer consumer) throws IOException {
    MinaTcpUnicastDispatcher ud = new MinaTcpUnicastDispatcher();
    ud.initialize(consumer, Conf.newInstance().addSystemProperties());
    return ud;
  }
}

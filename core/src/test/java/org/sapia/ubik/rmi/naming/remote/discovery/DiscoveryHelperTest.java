package org.sapia.ubik.rmi.naming.remote.discovery;

import static org.junit.Assert.assertNotNull;

import java.rmi.RemoteException;

import javax.naming.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sapia.ubik.concurrent.BlockingRef;
import org.sapia.ubik.mcast.EventChannelTestSupport;
import org.sapia.ubik.rmi.naming.remote.EmbeddableJNDIServer;
import org.sapia.ubik.rmi.server.Hub;
import org.sapia.ubik.util.PropUtil;

public class DiscoveryHelperTest {

  private EmbeddableJNDIServer jndi;
  private DiscoveryHelper helper;

  @Before
  public void setUp() throws Exception {
    PropUtil.clearUbikSystemProperties();
    jndi = new EmbeddableJNDIServer(EventChannelTestSupport.createEventChannel("test"), 1099);
    helper = new DiscoveryHelper(EventChannelTestSupport.createEventChannel("test").getReference());
    jndi.start(true);
    Thread.sleep(3000);

  }

  @After
  public void tearDown() {
    PropUtil.clearUbikSystemProperties();
    helper.close();
    jndi.stop();
  }

  @Test
  public void testServiceDiscovery() throws Exception {
    final BlockingRef<DiscoveryTestService> ref = new BlockingRef<DiscoveryTestService>();
    helper.addServiceDiscoListener(new ServiceDiscoListener() {
      @Override
      public void onServiceDiscovered(ServiceDiscoveryEvent evt) {
        try {
          ref.set((DiscoveryTestService) evt.getService());
        } catch (RemoteException e) {
          e.printStackTrace();
        }
      }
    });

    DiscoveryTestService toBind = new DiscoveryTestServiceImpl();
    
    jndi.getRootContext().bind("test", Hub.exportObject(toBind));

    DiscoveryTestService service = ref.await(3000);
    assertNotNull("Service not discovered", service);
  }

  @Test
  public void testJndiDiscovery() throws Exception {
    final BlockingRef<Boolean> ref = new BlockingRef<Boolean>();
    helper.addJndiDiscoListener(new JndiDiscoListener() {
      @Override
      public void onJndiDiscovered(Context ctx) {
        ref.set(new Boolean(true));
      }
    });

    Boolean discovered = ref.await(3000);
    assertNotNull("JNDI not discovered", discovered);
  }
}

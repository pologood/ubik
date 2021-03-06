package org.sapia.ubik.rmi.server.transport.mina;

import java.rmi.RemoteException;

import org.sapia.ubik.net.ConnectionPool;
import org.sapia.ubik.net.ThreadInterruptedException;
import org.sapia.ubik.rmi.server.transport.Connections;
import org.sapia.ubik.rmi.server.transport.RmiConnection;
import org.sapia.ubik.util.pool.NoObjectAvailableException;
import org.sapia.ubik.util.pool.PooledObjectCreationException;

/**
 * Implements a pool of client-side {@link MinaRmiClientConnection} instances.
 * It multiplexes the pooled connections among multiple callers.
 * 
 * @author Yanick Duchesne
 */
public class MinaRmiClientConnectionPool implements Connections {

  private ConnectionPool pool;

  /**
   * @param host
   *          the host of the server to connect to.
   * @param port
   *          the port of the server.
   * @param bufsize
   *          the size of connection buffers.
   */
  MinaRmiClientConnectionPool(String host, int port, int bufsize) {
    pool = new ConnectionPool.Builder().host(host).port(port).connectionFactory(new MinaRmiConnectionFactory(bufsize)).build();
  }

  /**
   * @see org.sapia.ubik.rmi.server.transport.Connections#acquire()
   */
  public RmiConnection acquire() throws RemoteException {
    try {
      return (RmiConnection) pool.acquire();
    } catch (PooledObjectCreationException e) {
      throw new RemoteException("Could not create connection", e);
    } catch (NoObjectAvailableException e) {
      throw new RemoteException("No connection available", e);
    } catch (InterruptedException e) {
      throw new ThreadInterruptedException();
    }
  }

  @Override
  public void release(RmiConnection conn) {
    pool.release(conn);
  }

  @Override
  public void clear() {
    pool.shrinkTo(0);
  }

  @Override
  public void invalidate(RmiConnection conn) {
    pool.invalidate(conn);
  }

  @Override
  public String getTransportType() {
    return MinaTransportProvider.TRANSPORT_TYPE;
  }

  ConnectionPool internalPool() {
    return pool;
  }

}

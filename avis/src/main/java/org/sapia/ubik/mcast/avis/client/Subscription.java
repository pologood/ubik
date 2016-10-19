package org.sapia.ubik.mcast.avis.client;

import java.io.IOException;

import org.sapia.ubik.mcast.avis.security.Keys;
import org.sapia.ubik.mcast.avis.util.ListenerList;

import static java.lang.Character.isDigit;
import static java.lang.Character.isLetter;
import static org.sapia.ubik.mcast.avis.client.SecureMode.ALLOW_INSECURE_DELIVERY;
import static org.sapia.ubik.mcast.avis.util.Util.checkNotNull;

/**
 * A subscription to notifications from an Elvin connection.
 *
 * @see Elvin#subscribe(String, Keys, SecureMode)
 * @see #addListener(NotificationListener)
 * 
 * @author Matthew Phillips
 */
public final class Subscription
{
  protected Elvin elvin;
  protected String subscriptionExpr;
  protected SecureMode secureMode;
  protected Keys keys;
  protected long id;
  protected ListenerList<NotificationListener> notificationListeners;

  protected Subscription (Elvin elvin,
                          String subscriptionExpr, 
                          SecureMode secureMode, 
                          Keys keys)
  {
    this.elvin = elvin;
    this.subscriptionExpr = checkSubscription (subscriptionExpr);
    this.secureMode = secureMode;
    this.keys = keys;
    this.notificationListeners = 
      new ListenerList<NotificationListener> 
        (NotificationListener.class, "notificationReceived", 
         NotificationEvent.class);
    
    checkNotNull (keys, "Keys");
    checkNotNull (secureMode, "Secure mode");
  }
  
  /**
   * Remove this subscription (unsubscribe). May be called more than
   * once. Unlike the other methods on this class, this may be called
   * on a subscription for a connection that has been closed without
   * generating an error, since such a subscription is effectively
   * removed anyway.
   * 
   * @throws IOException if a network error occurs.
   */
  public void remove ()
    throws IOException
  {
    synchronized (elvin)
    {
      if (id == 0)
        return;
      
      if (elvin.isOpen ())
        elvin.unsubscribe (this);
      
      id = 0;

      if (elvin.isOpen ())
        elvin.callbacks.flush ();
    }
  }
  
  /**
   * The elvin connection that created this subscription.
   */
  public Elvin elvin ()
  {
    return elvin;
  }
  
  /**
   * Test if this subscription is still able to receive notifications.
   * A subscription is inactive after a {@link #remove()} or when its
   * underlying connection is closed.
   */
  public boolean isActive ()
  {
    synchronized (elvin)
    {
      return id != 0 && elvin.isOpen ();
    }
  }

  private void checkActive ()
  {
    if (!isActive ())
      throw new IllegalStateException ("Subscription is not active");
  }
  
  private static String checkSubscription (String subscriptionExpr)
  {
    if (subscriptionExpr == null)
      throw new IllegalArgumentException ("Subscription cannot be null");
    
    subscriptionExpr = subscriptionExpr.trim ();
    
    if (subscriptionExpr.length () == 0)
      throw new IllegalArgumentException
        ("Subscription expression cannot be empty");
    
    return subscriptionExpr;
  }

  /**
   * The subscription expression.
   */
  public String subscriptionExpr ()
  {
    return subscriptionExpr;
  }
  
  /**
   * Change the subscription expression.
   * 
   * @throws IOException if the subscription is invalid or if a
   *           network error occurs.
   * @throws InvalidSubscriptionException if the subscription expression
   *           is invalid.
   */
  public void setSubscriptionExpr (String newSubscriptionExpr)
    throws IOException, InvalidSubscriptionException
  {
    newSubscriptionExpr = checkSubscription (newSubscriptionExpr);
    
    synchronized (elvin)
    {
      checkActive ();

      if (!newSubscriptionExpr.equals (subscriptionExpr))
      {
        elvin.modifySubscriptionExpr (this, newSubscriptionExpr);

        this.subscriptionExpr = newSubscriptionExpr;

        elvin.callbacks.flush ();
      }
    }
  }

  /**
   * The secure mode specified for receipt of notifications.
   */
  public SecureMode secureMode ()
  {
    return secureMode;
  }
  
  /**
   * Change the subscription's secure delivery requirement.
   * 
   * @param newMode The secure delivery mode.
   * 
   * @throws IOException if an IO error occurs during the operation.
   */
  public void setSecureMode (SecureMode newMode)
    throws IOException
  {
    checkNotNull (newMode, "Secure mode");
    
    synchronized (elvin)
    {
      checkActive ();
      
      if (newMode != secureMode)
      {
        elvin.modifySecureMode (this, newMode);
        
        this.secureMode = newMode;

        elvin.callbacks.flush ();
      }
    }
  }     
  
  /**
   * True if ALLOW_INSECURE_DELIVERY is enabled.
   * 
   * @see #secureMode()
   */
  public boolean acceptInsecure ()
  {
    return secureMode == ALLOW_INSECURE_DELIVERY;
  }

  /**
   * The keys used to receive secure notifications.
   */
  public Keys keys ()
  {
    return keys;
  }
  
  /**
   * Change the keys used for receiving secure notifications.
   */
  public void setKeys (Keys newKeys)
    throws IOException
  {
    checkNotNull (newKeys, "Keys");
    
    synchronized (elvin)
    {
      checkActive ();
     
      elvin.modifyKeys (this, newKeys);
      
      this.keys = newKeys;

      elvin.callbacks.flush ();
    }
  }

  /**
   * Add a listener for notifications matched by this subscription.
   * 
   * @see Elvin#addNotificationListener(GeneralNotificationListener)
   */
  public void addListener (NotificationListener listener)
  {
    synchronized (elvin)
    {
      elvin.callbacks.flush ();
      
      notificationListeners.add (listener);
    }
  }
  
  /**
   * Remove a previously
   * {@linkplain #addListener(NotificationListener) added}
   * listener.
   */
  public void removeListener (NotificationListener listener)
  {
    synchronized (elvin)
    {
      notificationListeners.remove (listener);
    }
  }
  
  /**
   * True if any listeners are in the listener list.
   * 
   * @see #addListener(NotificationListener)
   */
  public boolean hasListeners ()
  {
    return notificationListeners.hasListeners ();
  }

  void notifyListeners (NotificationEvent event)
  {
    notificationListeners.fire (event);
  }

  /**
   * Escape illegal characters in a field name for use in a
   * subscription expression. This can be useful when constructing
   * subscription expressions dynamically.
   * 
   * @param field The string to use as a field name.
   * @return The escaped version of field, guaranteed to be a valid
   *         field name.
   *         
   * @see #escapeString(String)
   */
  public static String escapeField (String field)
  {
    StringBuilder escapedStr = new StringBuilder (field.length () * 2);
    
    for (int i = 0; i < field.length (); i++)
    {
      char c = field.charAt (i);
      boolean escape = false;
      
      if (i == 0)
        escape = !(isLetter (c) || c == '_');
      else
        escape = !(isLetter (c) || isDigit (c) || c == '_');
      
      if (escape)
        escapedStr.append ('\\');
      
      escapedStr.append (c);
    }
    
    return escapedStr.toString ();
  }
  
  /**
   * Escape illegal characters in a string value for use in a
   * subscription expression. This can be useful when constructing
   * subscription expressions dynamically.
   * 
   * @param string A string that will occur within single or double
   *                quotes in a subscription expression.
   * @return The escaped version of string, guaranteed to be a valid
   *         to occur inside a string expression.
   *         
   * @see #escapeField(String)
   */
  public static String escapeString (String string)
  {
    StringBuilder escapedStr = new StringBuilder (string.length () * 2);
    
    for (int i = 0; i < string.length (); i++)
    {
      char c = string.charAt (i);
      
      if (c == '\"' || c == '\'' || c == '\\')
        escapedStr.append ('\\');
      
      escapedStr.append (c);
    }
    
    return escapedStr.toString ();
  }
}

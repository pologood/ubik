package org.sapia.ubik.net.mplex;

/**
 * Implements a selection logic for HTTP requests. By default this selector will
 * choose all HTTP request, as it looks for the token "HTTP/" as specified by
 * the HTTP specification. This selector also gives the functionaly to select
 * only a given HTTP method (like POST or GET) and/or to specify a pattern for
 * the HTTP request path. If specify this request pattern must be the first
 * characters of the request path to select (without any starts or regex
 * expression). The current logic to determine if the request pattern matches is
 * done using the <code>startsWith()</code> method of the
 * {@link java.lang.String} object.
 * 
 * @author <a href="mailto:jc@sapia-oss.org">Jean-Cedric Desrochers</a>
 */
public class HttpStreamSelector implements StreamSelector {

  /** The specific type of HTTP method to select, if provided */
  private String theMethod;

  /**
   * A simple selection pattern of the request path of the HTTP header, if
   * provided
   */
  private String theRequestPatern;

  /**
   * Creates a new HtpStreamSelector instance that will select any HTTP request.
   */
  public HttpStreamSelector() {
  }

  /**
   * Creates a new HtpStreamSelector instance that will select an HTTP request
   * based on the criterias passed in.
   * 
   * @param aMethod
   *          The specific HTTP method to discrimate on, or <code>null</code>
   *          for any type of HTTP method.
   * @param aRequestPattern
   *          The simple pattern that defines the starting request path of the
   *          HTTP request. For example the value "/sapia/example" would select
   *          the path "/sapia/example/index.html" and also
   *          "/sapia/example/service/myWebService". Passing <code>null</code>
   *          will make this selector choose any request path.
   */
  public HttpStreamSelector(String aMethod, String aRequestPattern) {
    theMethod = aMethod;
    theRequestPatern = aRequestPattern;
  }

  /**
   * Selects or not a stream by analyzing the header of the stream passed in.
   * 
   * @param header
   *          The first 64 bytes of the stream.
   * @return True if the header is accepted by this selector, false otherwise.
   */
  public boolean selectStream(byte[] header) {
    String aStringValue = new String(header);

    int firstSpace = aStringValue.indexOf(" ");

    if (firstSpace > 0) {
      // Looking for the HTTP method
      if (theMethod != null) {
        if (!aStringValue.substring(0, firstSpace).equals(theMethod)) {
          return false;
        }
      }

      int secondSpace = aStringValue.indexOf(" ", firstSpace + 1);

      if (secondSpace > firstSpace) {
        // Looking for the HTTP request URI
        if (theRequestPatern != null) {
          if (!aStringValue.substring(firstSpace + 1, secondSpace).startsWith(theRequestPatern)) {
            return false;
          }
        }

        // Looking for the HTTP version
        if (aStringValue.substring(secondSpace + 1, secondSpace + 6).equals("HTTP/")) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    } else {
      return false;
    }
  }
}

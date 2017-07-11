/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package protocols.avro;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public interface DomServer {
  public static final org.apache.avro.Protocol PROTOCOL = org.apache.avro.Protocol.parse("{\"protocol\":\"DomServer\",\"namespace\":\"protocols.avro\",\"types\":[],\"messages\":{\"IsAlive\":{\"request\":[{\"name\":\"IPaddr\",\"type\":\"string\"},{\"name\":\"ID\",\"type\":\"int\"}],\"response\":\"boolean\"}}}");
  boolean IsAlive(java.lang.CharSequence IPaddr, int ID) throws org.apache.avro.AvroRemoteException;

  @SuppressWarnings("all")
  public interface Callback extends DomServer {
    public static final org.apache.avro.Protocol PROTOCOL = protocols.avro.DomServer.PROTOCOL;
    void IsAlive(java.lang.CharSequence IPaddr, int ID, org.apache.avro.ipc.Callback<java.lang.Boolean> callback) throws java.io.IOException;
  }
}
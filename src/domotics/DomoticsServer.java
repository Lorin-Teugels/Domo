package domotics;



import domotics.ElectableClient;

import protocols.avro.DomServer;
import domotics.NetAddress;
/*
 * The code for the actual (original) server of the system. 
 * Most methods inherited from ElectableClient.
 */
public class DomoticsServer extends ElectableClient implements DomServer {
	
	public int getID(){
		return selfID.getPort();
	}
	
	public String getName(){
		return "server";	
	}
	
	public static void main(String[] args){
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
		DomoticsServer myServer = new DomoticsServer();
		Integer ID = 6789;
		if ( args.length > 0 ){
			ID = Integer.valueOf(args[0]);
		}
		String IP = "127.0.0.1";
		if ( args.length > 1 ){
			IP = args[1];
		}
		myServer.selfID = new NetAddress(ID,IP);
		if (myServer.selfID.getIP() == null){
			System.out.println("invalid IP");
			System.exit(-1);
		}
		System.out.println("Server is starting");
		myServer.run();
	}
	
	@Override
	public boolean IsAlive(CharSequence IPaddr, int ID){
		return true;
	}
	

	
}
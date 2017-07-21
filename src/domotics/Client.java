package domotics;

import domotics.NetAddress;

/*
 * Top class in inheritance hierarchy.
 * Contains methods used by all classes and logger.
 */
public abstract class Client{
	
	/*
	 * used to get the id of the client instance, port number is used as id.
	 */
	public abstract int getID();
	
	/*
	 * used to get the name of the client class, for instance, a thermostat object would have "thermostat" as a name
	 */
	public abstract String getName();
	
	/*
	 * logging function for use during testing, when not testing, comment out body.
	 */
	public void log(String s){
		//System.err.println("[" + Thread.currentThread().getName() + "] " + this.getName() + " " + this.getID() + " says: " +s);
	}
	
	public void exceptionLog(Exception e){
		//e.printStackTrace(System.err);
	}
	
	public static class clientinfo{
		public NetAddress serverAddr;
		public NetAddress MyAddr;
	}
	/*
	 * Used by light, smartfridge and thermostat.
	 * Parses command line and gives new classes the id and address of the server.
	 */
	public static clientinfo mainstart(String what, String[] args){
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
		Integer ServerID = 6789;
		String ServerIP = "127.0.0.1";
		Integer FridgeID = 6789;
		String FridgeIP = "127.0.0.1";
		if (args.length>0){
			ServerID = Integer.valueOf(args[0]);
		}
		if (args.length>1){
			ServerIP = args[1];
		}
		if (args.length>2){
			FridgeID = Integer.valueOf(args[2]);
		}
		if (args.length>3){
			FridgeIP = args[3];
		}
		NetAddress ServerAddr = new NetAddress(ServerID,ServerIP);
		if(ServerAddr.getIP() == null){
			System.out.println("Invalid serverIP");
			System.exit(-1);
		}
		NetAddress MyAddr = new NetAddress(FridgeID,FridgeIP);
		if(MyAddr.getIP() == null){
			System.out.println("Invalid " +what + "IP");
			System.exit(-1);
		}
		clientinfo returnval = new clientinfo();
		returnval.MyAddr = MyAddr;
		returnval.serverAddr = ServerAddr;
		//System.err.println("MyAddr: " + MyAddr);
		return returnval;
	}

}

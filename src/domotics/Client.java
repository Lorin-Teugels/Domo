package domotics;

import domotics.NetAddress;

public abstract class Client{
	/*private static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
	static void _log(String s) {
	    System.err.println(sdf.format(new Date()) + " " + s);
	}
	static synchronized void log(String s) {
	    _log(s);
	}
	*/
	public abstract int getID();
	
	public abstract String getName();
	public void log(String s){
		System.err.println("[" + Thread.currentThread().getName() + "] " + this.getName() + " " + this.getID() + " says: " +s);
	}
	public static class clientinfo{
		public NetAddress serverAddr;
		public NetAddress MyAddr;
	}
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
